package com.seckill.mq;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.seckill.config.RabbitConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀消息消费者
 * 从 MQ 接收秒杀消息，执行: 幂等性检查 → MySQL扣减库存 → 创建订单 → 设置结果
 * 业务规则：一人一单（Redis bought Set 保证），取消订单后可重新购买
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillMessageConsumer {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillService seckillService;
    private final RabbitTemplate rabbitTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitConfig.SECKILL_QUEUE)
    public void handleSeckillMessage(SeckillMessage msg) {
        try {
            log.info("收到秒杀消息: userId={}, goodsId={}", msg.getUserId(), msg.getSeckillGoodsId());
            processOrder(msg);
        } catch (Exception e) {
            log.error("秒杀消息处理失败，将触发重试或进入死信: userId={}, goodsId={}, error={}",
                    msg.getUserId(), msg.getSeckillGoodsId(), e.getMessage(), e);
            // 抛出异常，让 Spring Retry 机制截获并进行 3 次重试，失败后由框架自动拒绝投递至死信交换机
            throw new RuntimeException("秒杀下单执行失败", e);
        }
    }

    /**
     * 处理订单（核心事务逻辑）
     * 注意：分布式锁仅保护幂等性检查阶段，不在 try-finally 包裹整个方法。
     * 原因：锁持有时间不宜过长（10秒），避免影响并发性能。
     * 防护措施：即使锁过期并发执行，MySQL 乐观锁 + Redis 去重 + 对账任务仍能保证最终一致性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOrder(SeckillMessage msg) {
        Long userId = msg.getUserId();
        Long seckillGoodsId = msg.getSeckillGoodsId();

        // 获取基于 Redis 的分布式锁 (防短时间并发消费)，过期时间设为 10 秒
        String lockKey = "seckill:lock:" + userId + ":" + seckillGoodsId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, java.util.concurrent.TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            log.warn("正在处理该用户的订单，跳过: userId={}, goodsId={}", userId, seckillGoodsId);
            return;
        }

        try {
            // 1. 幂等性检查: 仅拦截真正生效的订单 (待支付或已支付)
            // 注意：此检查基于 MySQL，不检查 Redis bought Set，允许 Lua 扣减后 MySQL 事务失败的场景重试
            Long existCount = seckillOrderMapper.selectCount(
                    new LambdaQueryWrapper<SeckillOrder>()
                            .eq(SeckillOrder::getUserId, userId)
                            .eq(SeckillOrder::getSeckillGoodsId, seckillGoodsId)
                            .in(SeckillOrder::getStatus, SeckillOrder.STATUS_UNPAID, SeckillOrder.STATUS_PAID)
            );
            if (existCount > 0) {
                log.warn("正交/待支付订单已存在，跳过: userId={}, goodsId={}", userId, seckillGoodsId);
                return;
            }

        // 2. MySQL 扣减库存 (乐观锁: WHERE available_stock > 0)
        int affected = seckillGoodsMapper.deductStock(seckillGoodsId);
        if (affected == 0) {
            // 扣减失败：库存不足，回滚 Redis
            log.warn("MySQL库存扣减失败: goodsId={}", seckillGoodsId);
            try {
                seckillService.rollbackRedis(userId, seckillGoodsId);
            } catch (Exception e) {
                log.error("Redis 回滚失败: userId={}, goodsId={}, error={}", userId, seckillGoodsId, e.getMessage(), e);
                // 继续执行，设置失败结果
            }
            seckillService.setSeckillResult(userId, seckillGoodsId, "2");
            return;
        }

        // 3. 创建订单
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setSeckillGoodsId(seckillGoodsId);
        order.setSeckillPrice(msg.getSeckillPrice());
        order.setStatus(SeckillOrder.STATUS_UNPAID);
        seckillOrderMapper.insert(order);

        // 4. 设置秒杀结果: "1:orderNo"
        seckillService.setSeckillResult(userId, seckillGoodsId, "1:" + orderNo);

        // 5. 发送延迟消息 (订单超时取消)
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.DELAY_EXCHANGE,
                    RabbitConfig.DELAY_ROUTING_KEY,
                    orderNo
            );
            log.info("订单创建成功: orderNo={}, 已发送超时取消延迟消息({}分钟)",
                    orderNo, RabbitConfig.ORDER_TIMEOUT_MS / 60000);
        } catch (Exception e) {
            // 延迟消息发送失败不影响主流程，记录日志即可
            log.error("延迟消息发送失败: orderNo={}", orderNo, e);
        }

        log.info("秒杀订单处理完成: userId={}, goodsId={}, orderNo={}", userId, seckillGoodsId, orderNo);
        } finally {
            // 6. 释放锁
            stringRedisTemplate.delete(lockKey);
        }
    }
}
