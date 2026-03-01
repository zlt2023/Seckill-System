package com.seckill.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.ResultCode;
import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillMessage;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务 - 核心业务逻辑
 * 安全防护：验证码 → 路径隐藏 → 限流 → 内存标记 → Redis预减 → MQ异步
 *
 * 重构修复清单:
 * - P0-2: validateSeckillPath 使用原子 getAndDelete
 * - P0-3: 重复判断+预减库存合并为单个 Lua 原子脚本
 * - P1-1: 依赖 CaptchaService 替代 CaptchaController
 * - P1-3: 暴露 clearStockOverFlag 方法供订单取消时调用
 * - P1-6: initSeckillStock 仅加载进行中的秒杀商品
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final OrderService orderService;
    private final CaptchaService captchaService; // P1-1: 替换 CaptchaController
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> seckillScript; // P0-3: 合并后的原子脚本

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String ORDER_KEY = "seckill:order:";
    private static final String SECKILL_RESULT_KEY = "seckill:result:";
    private static final String SECKILL_PATH_KEY = "seckill:path:";
    private static final String PATH_SALT = "FlashSale@2026!";
    /** 订单标记 TTL: 24小时 = 86400秒 */
    private static final long ORDER_MARK_TTL_SECONDS = 24 * 3600;

    /** 内存标记：商品是否已售罄（减少Redis访问） */
    private final Map<Long, Boolean> stockOverMap = new ConcurrentHashMap<>();

    /**
     * 系统初始化：将秒杀商品库存加载到Redis
     * P1-6 修复: 仅加载 status=1（进行中）的秒杀商品，避免浪费资源
     */
    @PostConstruct
    public void initSeckillStock() {
        // 先清理 Redis 中现有的秒杀库存，防止状态已变及脏数据残留
        Set<String> keys = redisTemplate.keys(STOCK_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 清理本地内存标记
        // 3. 重新从数据库按最新状态加载
        List<SeckillGoods> list = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getSeckillStatus, 1)
                        .eq(SeckillGoods::getGoodsStatus, 1));
        for (SeckillGoods sg : list) {
            redisTemplate.opsForValue().set(STOCK_KEY + sg.getId(), sg.getStockCount());
            stockOverMap.put(sg.getId(), false);
        }
        int oldKeys = keys != null ? keys.size() : 0;
        log.info("秒杀库存预热完成, 共清理 {} 个旧库存, 加载 {} 个进行中的秒杀商品", oldKeys, list.size());
    }

    /**
     * 追加初始化缓存（供后台定时任务调度使用）
     * 核心区别：绝对不能删除 Redis 旧数据，也绝对不能覆盖 Redis 中已存在的数据！
     * （如果在运行途中覆盖，就会把正在秒杀扣减的 Redis 最新库存用 DB 里的滞后库存覆盖掉，引发重卖）
     */
    public void incrementalInitSeckillStock() {
        List<SeckillGoods> list = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getSeckillStatus, 1)
                        .eq(SeckillGoods::getGoodsStatus, 1));
        int count = 0;
        for (SeckillGoods sg : list) {
            // setIfAbsent (即 Redis 的 SETNX)
            // 只有当 Redis 中不存在该商品库存记录时，才从 DB 捞出来初始化。
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(STOCK_KEY + sg.getId(), sg.getStockCount());
            if (Boolean.TRUE.equals(absent)) {
                stockOverMap.put(sg.getId(), false);
                count++;
            }
        }
        if (count > 0) {
            log.info("定时任务追加预热了 {} 个新活动商品的库存到Redis", count);
        }
    }

    // ========================= 缓存与状态刷新 =========================

    /**
     * 单个强制刷新：下架后重新上架更新Redis
     */
    public void reloadSingleSeckillStock(Long seckillGoodsId) {
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg != null && sg.getSeckillStatus() == 1 && sg.getGoodsStatus() == 1) {
            redisTemplate.opsForValue().set(STOCK_KEY + sg.getId(), sg.getStockCount());
            stockOverMap.put(sg.getId(), false);
            log.info("手动刷新缓存：下架重新上架，商品 {} 缓存与售罄标记已重置", sg.getId());
        } else {
            redisTemplate.delete(STOCK_KEY + seckillGoodsId);
            stockOverMap.put(seckillGoodsId, true); // 不允许抢了
        }
    }

    // ========================= 安全防护层 =========================

    /**
     * 创建秒杀路径（验证码校验通过后）
     * P1-1 修复: 调用 CaptchaService 替代 CaptchaController
     */
    public String createSeckillPath(Long userId, Long seckillGoodsId, int captchaAnswer) {
        // 1. 验证验证码（通过 Service 层调用，而非 Controller）
        boolean valid = captchaService.verifyCaptcha(userId, seckillGoodsId, captchaAnswer);
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
     * P0-2 修复: 使用原子的 getAndDelete 替代 get + delete 两步操作，
     * 防止高并发下同一个 path 被多次验证通过（重放攻击）
     */
    public boolean validateSeckillPath(Long userId, Long seckillGoodsId, String path) {
        String key = SECKILL_PATH_KEY + userId + ":" + seckillGoodsId;
        // 原子操作：取值并删除（Spring Data Redis 2.6+ 支持）
        Object storedPath = redisTemplate.opsForValue().getAndDelete(key);
        return storedPath != null && path.equals(storedPath.toString());
    }

    // ========================= 秒杀核心逻辑 =========================

    /**
     * 执行秒杀（异步）
     * 1. 内存标记判断
     * 2. 校验秒杀商品及时间窗口
     * 3. 【原子操作】Redis Lua: 重复判断 + 库存预减（P0-3 修复）
     * 4. 发送MQ消息
     */
    public void doSeckill(Long userId, Long seckillGoodsId) {
        // 1. 内存标记：已售罄直接返回
        if (Boolean.TRUE.equals(stockOverMap.get(seckillGoodsId))) {
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }

        // 2. 校验秒杀商品及时间窗口 (原先此处的 DB 查询已移除)
        // 核心解答：绝对没必要在此做 DB 查询！这是扛万级并发的前线，查 MySQL 会使得 Redis 前置形同虚设引发雪崩。
        // 时间窗口和状态已经在两个地方做了最严密的闭环保护：
        // (1) 事前保护：如果尚未开始，STOCK_KEY 在 Redis 中根本不存在（Lua 返回 0）
        // (2) 事后保护：如果已结束，Scheduler 会把 STOCK_KEY 清除（Lua 返回 0）
        // (3) 终极防线：消费者 executeSeckill() 写入数据库前，仍会稳稳当当地查一次 DB 判断时间！

        // 3. 【P0-3 修复】原子 Lua 脚本: 重复秒杀判断 + 库存预减
        // 将原来分离的 setIfAbsent + Lua decr 合并为单个原子操作
        String stockKey = STOCK_KEY + seckillGoodsId;
        String orderKey = ORDER_KEY + userId + ":" + seckillGoodsId;
        Long result = redisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, orderKey),
                ORDER_MARK_TTL_SECONDS);

        if (result == null || result == 0) {
            // 库存不足
            stockOverMap.put(seckillGoodsId, true);
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }
        if (result == -1) {
            // 重复秒杀
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }

        // 4. 发送秒杀消息到MQ
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
            log.error("MQ发送失败，回滚Redis库存和订单标记: {}", e.getMessage());
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
     * P1-3 修复: 清除内存售罄标记
     * 供 OrderService 在取消订单恢复库存后调用,
     * 防止库存已恢复但内存标记仍为"售罄"导致后续请求被拒绝(少卖)
     */
    public void clearStockOverFlag(Long seckillGoodsId) {
        stockOverMap.put(seckillGoodsId, false);
        log.debug("已清除秒杀商品 {} 的内存售罄标记", seckillGoodsId);
    }

    /**
     * 真正执行秒杀（MQ消费者调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeSeckill(Long userId, Long seckillGoodsId) {
        // 1. 校验秒杀商品状态和库存
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null || sg.getStockCount() <= 0) {
            handleSeckillFail(userId, seckillGoodsId);
            return;
        }

        // 如果消费此条消息时，商品被管理员下架或活动状态出错，也作为失败处理
        if (sg.getGoodsStatus() != 1 || sg.getSeckillStatus() != 1) {
            log.warn("秒杀消费时商品已下架或活动不处于进行中: userId={}, seckillGoodsId={}", userId, seckillGoodsId);
            handleSeckillFail(userId, seckillGoodsId);
            return;
        }

        // 2. 校验秒杀时间窗口（MQ消费时也需要校验，防止延迟消费导致超时下单）
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(sg.getEndDate())) {
            log.warn("秒杀消费时已超过活动结束时间: userId={}, seckillGoodsId={}", userId, seckillGoodsId);
            handleSeckillFail(userId, seckillGoodsId);
            return;
        }

        // 3. 再次检查是否重复秒杀（数据库层面 — 最后一道防线）
        SeckillOrder existOrder = orderService.getSeckillOrder(userId, seckillGoodsId);
        if (existOrder != null) {
            // 已有订单，不必再次删除标记，仅退回扣减错的库存份额，设为失败
            setResult(userId, seckillGoodsId, -1L);
            redisTemplate.opsForValue().increment(STOCK_KEY + seckillGoodsId);
            return;
        }

        // 4. 数据库减库存 (乐观锁: stock_count > 0)
        int affectedRows = seckillGoodsMapper.reduceStock(seckillGoodsId);
        if (affectedRows == 0) {
            handleSeckillFail(userId, seckillGoodsId);
            return;
        }

        // 6. 创建订单
        OrderInfo order = new OrderInfo();
        order.setUserId(userId);
        order.setGoodsId(seckillGoodsId);
        order.setGoodsName(sg.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(sg.getSeckillPrice());
        order.setStatus(0); // 未支付
        order.setDeleted(0);
        orderService.save(order);

        // 7. 创建秒杀订单
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(userId);
        seckillOrder.setOrderId(order.getId());
        seckillOrder.setGoodsId(seckillGoodsId);
        seckillOrderMapper.insert(seckillOrder);

        // 8. 标记秒杀结果（设置 24h TTL，防止 Redis Key 永久占用）
        setResult(userId, seckillGoodsId, order.getId());

        // 9. 发送延迟消息（30分钟后检查支付状态）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                order.getId());

        log.info("秒杀成功: userId={}, orderId={}, goodsName={}", userId, order.getId(), sg.getGoodsName());
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

    /**
     * MQ消费时发生异常落库失败的补偿：回滚Redis占用坑位
     */
    public void handleSeckillFail(Long userId, Long seckillGoodsId) {
        setResult(userId, seckillGoodsId, -1L);
        // 回滚 Redis 库存，避免用户占用了 Redis 库存导致永久少卖
        redisTemplate.opsForValue().increment(STOCK_KEY + seckillGoodsId);

        // 删除排队成功的标记，让用户可以重新抢购
        redisTemplate.delete(ORDER_KEY + userId + ":" + seckillGoodsId);

        // 清除内存售罄标记
        clearStockOverFlag(seckillGoodsId);
    }
}
