package com.seckill.service;

import cn.hutool.core.util.IdUtil;
import com.seckill.common.ResultCode;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillGoods;
import com.seckill.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final RabbitTemplate rabbitTemplate;

    // ===== Redis Key 前缀 =====
    private static final String BOUGHT_KEY = "seckill:bought:";
    private static final String PATH_KEY = "seckill:path:";
    private static final String RESULT_KEY = "seckill:result:";
    private static final String INFO_KEY = "seckill:info:";

    // ===== MQ 常量 =====
    private static final String SECKILL_EXCHANGE = "seckill.exchange";
    private static final String SECKILL_ROUTING_KEY = "seckill.order";

    /**
     * JVM 内存标记：记录已售罄的商品ID
     * 避免已售罄后还频繁访问 Redis
     */
    private final Map<Long, Boolean> localOverMap = new ConcurrentHashMap<>();

    // ================================================================
    // 动态路径
    // ================================================================

    /**
     * 生成动态秒杀路径 Token
     * 业务规则：一人一单（Redis bought Set 检查），取消订单后可重新购买
     */
    public String createPath(Long userId, Long seckillGoodsId) {
        // 1. 内存标记拦截（已售罄直接拦截，不查 Redis）
        if (localOverMap.getOrDefault(seckillGoodsId, false)) {
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }

        // 2. 从 Redis 获取实时活动信息 (时间、状态、库存)
        String infoKey = INFO_KEY + seckillGoodsId;
        java.util.Collection<Object> fields = java.util.Arrays.asList("startTime", "endTime", "status");
        List<Object> infoList = stringRedisTemplate.opsForHash().multiGet(infoKey, fields);
        
        if (infoList == null || infoList.isEmpty() || infoList.get(0) == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }

        long now = System.currentTimeMillis();
        long startTime = Long.parseLong((String) infoList.get(0));
        long endTime = Long.parseLong((String) infoList.get(1));
        int status = Integer.parseInt((String) infoList.get(2));

        // 3. 状态校验 (手动下架拦截)
        if (status != SeckillGoods.STATUS_ON) {
            throw new BusinessException(ResultCode.SECKILL_OFF_SHELF);
        }

        // 4. 时间校验
        if (now < startTime) {
            throw new BusinessException(ResultCode.SECKILL_NOT_START);
        }
        if (now > endTime) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }

        // 生成随机 Token
        String pathToken = IdUtil.fastSimpleUUID();

        // 存入 Redis，60秒过期
        String key = PATH_KEY + userId + ":" + seckillGoodsId;
        stringRedisTemplate.opsForValue().set(key, pathToken, 60, TimeUnit.SECONDS);

        log.debug("生成秒杀路径: userId={}, goodsId={}, token={}", userId, seckillGoodsId, pathToken);
        return pathToken;
    }

    /**
     * 验证动态路径 Token
     */
    private boolean validatePath(Long userId, Long seckillGoodsId, String path) {
        String key = PATH_KEY + userId + ":" + seckillGoodsId;
        String storedPath = stringRedisTemplate.opsForValue().get(key);
        if (storedPath == null) {
            return false;
        }
        return path.equals(storedPath);
    }

    // ================================================================
    // 执行秒杀
    // ================================================================

    /**
     * 执行秒杀（核心方法）
     * 业务规则：一人一单（Redis bought Set 检查），取消订单后可重新购买
     *
     * @return 排队中/失败原因
     */
    public String doSeckill(Long userId, Long seckillGoodsId, String path) {
        // 1. 验证动态路径
        if (!validatePath(userId, seckillGoodsId, path)) {
            throw new BusinessException(ResultCode.SECKILL_PATH_INVALID);
        }

        // 3. 执行 Lua 脚本 (原子操作：Hash 库存自减 + Set 判重 + 时间/状态硬校验)
        Long result = stringRedisTemplate.execute(
                seckillScript,
                (java.util.List<String>) (java.util.List<?>) java.util.List.of(
                        INFO_KEY + seckillGoodsId,
                        BOUGHT_KEY + seckillGoodsId
                ),
                String.valueOf(userId),
                String.valueOf(System.currentTimeMillis())
        );

        if (result == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR);
        }

        if (result == -1) {
            // 更新 JVM 本地标记 (当 Lua 返回 -1 时代表 Hash 中的 stock 字段已 <= 0)
            localOverMap.put(seckillGoodsId, true);
            throw new BusinessException(ResultCode.SECKILL_SOLD_OUT);
        }
        if (result == -2) {
            throw new BusinessException(ResultCode.SECKILL_REPEAT);
        }
        if (result == -3) {
            throw new BusinessException(ResultCode.SECKILL_OFF_SHELF);
        }
        if (result == -4) {
            throw new BusinessException(ResultCode.SECKILL_ENDED);
        }

        // 4. Lua 返回 1：扣减成功，快速从 Redis 缓存获取秒杀价格，彻底摆脱 MySQL
        Object priceObj = stringRedisTemplate.opsForHash().get(INFO_KEY + seckillGoodsId, "price");
        if (priceObj == null) { // 理论上不可能，除非 TTL 过期
            rollbackRedis(userId, seckillGoodsId);
            throw new BusinessException(ResultCode.SERVER_ERROR, "商品信息缓存丢失");
        }
        java.math.BigDecimal seckillPrice = new java.math.BigDecimal((String) priceObj);

        // 5. 发送 MQ 消息
        SeckillMessage message = new SeckillMessage(userId, seckillGoodsId, seckillPrice);
        try {
            rabbitTemplate.convertAndSend(SECKILL_EXCHANGE, SECKILL_ROUTING_KEY, message);
        } catch (Exception e) {
            // MQ 发送失败，回滚 Redis
            log.error("MQ发送失败，回滚Redis: userId={}, goodsId={}", userId, seckillGoodsId, e);
            rollbackRedis(userId, seckillGoodsId);
            throw new BusinessException("系统繁忙，请稍后再试");
        }

        // 6. 设置排队标记 (0=排队中)
        String resultKey = RESULT_KEY + userId + ":" + seckillGoodsId;
        stringRedisTemplate.opsForValue().set(resultKey, "0", 30, TimeUnit.MINUTES);

        log.info("秒杀排队: userId={}, goodsId={}", userId, seckillGoodsId);
        return "排队中";
    }

    // ================================================================
    // 轮询结果
    // ================================================================

    /**
     * 查询秒杀结果
     *
     * @return Map: result=0排队中/1成功/2失败, orderNo=订单编号(成功时)
     */
    public Map<String, Object> getSeckillResult(Long userId, Long seckillGoodsId) {
        String resultKey = RESULT_KEY + userId + ":" + seckillGoodsId;
        String value = stringRedisTemplate.opsForValue().get(resultKey);

        Map<String, Object> map = new HashMap<>();
        if (value == null) {
            // 没有记录，说明没有参与秒杀
            map.put("result", -1);
        } else {
            if (value.startsWith("1:")) {
                // 成功，格式 "1:orderNo"
                map.put("result", 1);
                map.put("orderNo", value.substring(2));
            } else if ("2".equals(value)) {
                map.put("result", 2);
            } else {
                // "0" 排队中
                map.put("result", 0);
            }
        }
        return map;
    }

    // ================================================================
    // Redis 回滚
    // ================================================================

    /**
     * 回滚 Redis 库存和购买标记
     * 用于 MQ 扣减失败、订单超时取消、用户手动取消等场景
     */
    public void rollbackRedis(Long userId, Long seckillGoodsId) {
        String infoKey = INFO_KEY + seckillGoodsId;
        String boughtKey = BOUGHT_KEY + seckillGoodsId;
        String resultKey = RESULT_KEY + userId + ":" + seckillGoodsId;

        // 1. 检查 Redis 缓存是否存在
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(infoKey))) {
            log.warn("Redis 缓存不存在，跳过库存回滚: goodsId={}", seckillGoodsId);
            // 检查 bought Set 是否存在，避免无效的 Redis 调用
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(boughtKey))) {
                stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            }
            stringRedisTemplate.delete(resultKey);
            // 清除本地售罄标记，即使 Redis 缓存不存在
            localOverMap.put(seckillGoodsId, false);
            return;
        }

        // 2. 回滚 Hash 中的库存
        stringRedisTemplate.opsForHash().increment(infoKey, "stock", 1);

        // 3. 移除重复购买标记（允许重新购买）
        stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));

        // 4. 清除处理结果 (为了支持重新购买)
        stringRedisTemplate.delete(resultKey);

        // 5. 检查 Redis 库存是否大于 0，如果是则清除本地售罄标记
        // 这确保了当有用户取消订单后，库存恢复时其他用户可以继续抢购
        Object stockObj = stringRedisTemplate.opsForHash().get(infoKey, "stock");
        if (stockObj != null) {
            try {
                int stock = Integer.parseInt(stockObj.toString());
                if (stock > 0) {
                    localOverMap.put(seckillGoodsId, false);
                }
            } catch (NumberFormatException e) {
                log.warn("Redis 库存值格式错误，清除本地售罄标记: goodsId={}", seckillGoodsId);
                localOverMap.put(seckillGoodsId, false);
            }
        }

        log.info("回滚 Redis 状态成功: userId={}, goodsId={}", userId, seckillGoodsId);
    }

    /**
     * 设置秒杀结果到 Redis
     */
    public void setSeckillResult(Long userId, Long seckillGoodsId, String resultValue) {
        String resultKey = RESULT_KEY + userId + ":" + seckillGoodsId;
        stringRedisTemplate.opsForValue().set(resultKey, resultValue, 30, TimeUnit.MINUTES);
    }

    /**
     * 获取服务器当前时间戳（倒计时校准用）
     */
    public long getServerTime() {
        return System.currentTimeMillis();
    }
}

