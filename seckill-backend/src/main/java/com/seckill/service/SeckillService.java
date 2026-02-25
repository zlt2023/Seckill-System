package com.seckill.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.seckill.common.ResultCode;
import com.seckill.config.RabbitMQConfig;
import com.seckill.controller.CaptchaController;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.Goods;
import com.seckill.entity.OrderInfo;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务 - 核心业务逻辑
 * 安全防护：验证码 → 路径隐藏 → 限流 → 内存标记 → Redis预减 → MQ异步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final GoodsService goodsService;
    private final OrderService orderService;
    private final CaptchaController captchaController;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> stockDecrScript;

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String ORDER_KEY = "seckill:order:";
    private static final String SECKILL_RESULT_KEY = "seckill:result:";
    private static final String SECKILL_PATH_KEY = "seckill:path:";
    private static final String PATH_SALT = "FlashSale@2026!";

    /** 内存标记：商品是否已售罄（减少Redis访问） */
    private final Map<Long, Boolean> stockOverMap = new ConcurrentHashMap<>();

    /**
     * 系统初始化：将秒杀商品库存加载到Redis
     */
    @PostConstruct
    public void initSeckillStock() {
        List<SeckillGoods> list = seckillGoodsMapper.selectList(null);
        for (SeckillGoods sg : list) {
            redisTemplate.opsForValue().set(STOCK_KEY + sg.getId(), sg.getStockCount());
            stockOverMap.put(sg.getId(), false);
        }
        log.info("秒杀库存预热完成, 共加载 {} 个秒杀商品", list.size());
    }

    // ========================= 安全防护层 =========================

    /**
     * 创建秒杀路径（验证码校验通过后）
     */
    public String createSeckillPath(Long userId, Long seckillGoodsId, int captchaAnswer) {
        // 1. 验证验证码
        boolean valid = captchaController.verifyCaptcha(userId, seckillGoodsId, captchaAnswer);
        if (!valid) {
            throw new BusinessException(ResultCode.SECKILL_CAPTCHA_ERROR);
        }

        // 2. 生成动态路径 (UUID + MD5)
        String uuid = UUID.randomUUID().toString();
        String path = DigestUtil.md5Hex(userId + "_" + seckillGoodsId + "_" + uuid + "_" + PATH_SALT);

        // 3. 存入Redis (1分钟有效)
        String key = SECKILL_PATH_KEY + userId + ":" + seckillGoodsId;
        redisTemplate.opsForValue().set(key, path, 60, TimeUnit.SECONDS);

        return path;
    }

    /**
     * 验证秒杀路径
     */
    public boolean validateSeckillPath(Long userId, Long seckillGoodsId, String path) {
        String key = SECKILL_PATH_KEY + userId + ":" + seckillGoodsId;
        Object storedPath = redisTemplate.opsForValue().get(key);
        if (storedPath == null) {
            return false;
        }
        // 用后即删
        redisTemplate.delete(key);
        return path.equals(storedPath.toString());
    }

    // ========================= 秒杀核心逻辑 =========================

    /**
     * 执行秒杀（异步）
     * 1. 内存标记判断
     * 2. Redis预减库存
     * 3. 判断重复秒杀
     * 4. 发送MQ消息
     */
    public void doSeckill(Long userId, Long seckillGoodsId) {
        // 1. 内存标记：已售罄直接返回
        if (Boolean.TRUE.equals(stockOverMap.get(seckillGoodsId))) {
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }

        // 2. 校验秒杀商品及时间窗口
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null || sg.getStatus() == 0) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sg.getStartDate())) {
            throw new BusinessException(ResultCode.SECKILL_NOT_START);
        }
        if (now.isAfter(sg.getEndDate())) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }

        // 3. Redis判断是否重复秒杀（设置 24h TTL，避免永益占用）
        String orderKey = ORDER_KEY + userId + ":" + sg.getGoodsId();
        Boolean isRepeat = redisTemplate.opsForValue().setIfAbsent(orderKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isRepeat)) {
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }

        // 4. Redis Lua 原子预减库存
        Long result = redisTemplate.execute(stockDecrScript,
                Collections.singletonList(STOCK_KEY + seckillGoodsId));
        if (result == null || result == 0) {
            stockOverMap.put(seckillGoodsId, true); // 标记售罄
            redisTemplate.delete(orderKey); // 回滚重复标记
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }

        // 5. 发送秒杀消息到MQ
        try {
            SeckillMessage message = new SeckillMessage();
            message.setUserId(userId);
            message.setSeckillGoodsId(seckillGoodsId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    message);
            log.info("秒杀请求已入队: userId={}, seckillGoodsId={}", userId, seckillGoodsId);
        } catch (Exception e) {
            log.error("MQ发送失败，回滚Redis库存: {}", e.getMessage());
            // 回滚 Redis 库存
            redisTemplate.opsForValue().increment(STOCK_KEY + seckillGoodsId);
            // 清除重复秒杀标记
            redisTemplate.delete(orderKey);
            // 清除售罄标记
            stockOverMap.put(seckillGoodsId, false);
            throw new BusinessException("秒杀繁忙，请稍后重试");
        }
    }

    /**
     * 重置库存 (供管理后台使用)
     * 同时更新DB、Redis和清除本地售罄标记
     */
    public void resetStock(Long seckillGoodsId, Integer stockCount) {
        // 1. 更新 Redis
        redisTemplate.opsForValue().set(STOCK_KEY + seckillGoodsId, stockCount);
        // 2. 清除本地售罄标记
        stockOverMap.put(seckillGoodsId, false);
        // 3. 清除商品详情缓存和列表缓存，确保前端能看到最新库存
        redisTemplate.delete("seckill:goods:detail:" + seckillGoodsId);
        redisTemplate.delete("seckill:goods:list");

        log.info("秒杀商品 {} 库存已重置为 {}, 本地售罄标记和缓存已清除", seckillGoodsId, stockCount);
    }

    /**
     * 真正执行秒杀（MQ消费者调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeSeckill(Long userId, Long seckillGoodsId) {
        // 1. 校验秒杀商品
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null || sg.getStockCount() <= 0) {
            setResult(userId, seckillGoodsId, -1L);
            return;
        }

        // 2. 校验秒杀时间窗口（MQ消费时也需要校验，防止延迟消费导致超时下单）
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(sg.getEndDate())) {
            log.warn("秒杀消费时已超过活动结束时间: userId={}, seckillGoodsId={}", userId, seckillGoodsId);
            setResult(userId, seckillGoodsId, -1L);
            return;
        }

        // 3. 再次检查是否重复秒杀（数据库层面）
        SeckillOrder existOrder = orderService.getSeckillOrder(userId, sg.getGoodsId());
        if (existOrder != null) {
            setResult(userId, seckillGoodsId, -1L);
            return;
        }

        // 4. 数据库减库存 (乐观锁: stock_count > 0)
        int affectedRows = seckillGoodsMapper.reduceStock(seckillGoodsId);
        if (affectedRows == 0) {
            setResult(userId, seckillGoodsId, -1L);
            return;
        }

        // 5. 获取商品信息
        Goods goods = goodsService.getById(sg.getGoodsId());

        // 6. 创建订单
        OrderInfo order = new OrderInfo();
        order.setUserId(userId);
        order.setGoodsId(sg.getGoodsId());
        order.setSeckillGoodsId(seckillGoodsId);
        order.setGoodsName(goods.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(sg.getSeckillPrice());
        order.setStatus(0); // 未支付
        order.setDeleted(0);
        orderService.save(order);

        // 7. 创建秒杀订单
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(userId);
        seckillOrder.setOrderId(order.getId());
        seckillOrder.setGoodsId(sg.getGoodsId());
        seckillOrderMapper.insert(seckillOrder);

        // 8. 标记秒杀结果（设置 24h TTL，防止 Redis Key 永久占用）
        setResult(userId, seckillGoodsId, order.getId());

        // 9. 发送延迟消息（30分钟后检查支付状态）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                order.getId());

        log.info("秒杀成功: userId={}, orderId={}, goodsName={}", userId, order.getId(), goods.getGoodsName());
    }

    /**
     * 查询秒杀结果
     *
     * @return orderId-成功 0-排队中 -1-失败
     */
    public Long getResult(Long userId, Long seckillGoodsId) {
        Object result = redisTemplate.opsForValue().get(SECKILL_RESULT_KEY + userId + ":" + seckillGoodsId);
        if (result == null) {
            return 0L; // 排队中
        }
        return Long.valueOf(result.toString());
    }

    /**
     * 设置秒杀结果到Redis（TTL 24小时，防止Key永久占用）
     */
    private void setResult(Long userId, Long seckillGoodsId, Long orderId) {
        redisTemplate.opsForValue().set(
                SECKILL_RESULT_KEY + userId + ":" + seckillGoodsId,
                orderId, 24, TimeUnit.HOURS);
    }
}
