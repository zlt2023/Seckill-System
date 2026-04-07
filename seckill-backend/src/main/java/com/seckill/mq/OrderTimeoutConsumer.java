package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.config.RabbitConfig;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单超时取消消费者
 * 接收延迟队列中 TTL 到期的订单消息，自动取消未支付订单并回滚库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final SeckillService seckillService;

    @RabbitListener(queues = RabbitConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderTimeout(String orderNo) {
        try {
            log.info("收到订单超时消息: orderNo={}", orderNo);
            cancelTimeoutOrder(orderNo);
        } catch (Exception e) {
            log.error("订单超时取消处理失败，将触发重试: orderNo={}, error={}", orderNo, e.getMessage(), e);
            // 抛出异常，让 Spring Retry 机制截获并进行 3 次重试
            throw new RuntimeException("订单超时取消执行失败", e);
        }
    }

    /**
     * 取消超时订单
     * 注意：Redis 操作不在 MySQL 事务中，失败时依赖定时对账任务修复
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrder(String orderNo) {
        // 1. 查询订单
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("订单不存在: orderNo={}", orderNo);
            return;
        }

        // 2. 只处理待支付状态的订单
        if (order.getStatus() != SeckillOrder.STATUS_UNPAID) {
            log.info("订单非待支付状态，跳过: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        // 3. 更新订单状态为已超时（乐观锁，防止支付与取消并发冲突）
        int updated = seckillOrderMapper.updateStatus(orderNo, SeckillOrder.STATUS_UNPAID, SeckillOrder.STATUS_TIMEOUT);
        if (updated == 0) {
            log.info("订单状态已变更（可能已支付），无法取消: orderNo={}", orderNo);
            return;
        }

        // 4. MySQL 库存回滚 (+1)，带边界检查
        int rollbackResult = seckillGoodsMapper.rollbackStock(order.getSeckillGoodsId());
        if (rollbackResult == 0) {
            log.warn("MySQL 库存回滚失败，可能已达总库存上限: orderNo={}, goodsId={}", orderNo, order.getSeckillGoodsId());
            // 不抛异常，订单状态已正确更新为超时，库存问题依赖对账任务修复
        }

        // 5. Redis 库存回滚 + 移除购买标记
        try {
            seckillService.rollbackRedis(order.getUserId(), order.getSeckillGoodsId());
        } catch (Exception e) {
            // Redis 回滚失败不影响 MySQL 状态，依赖定时对账任务修复
            log.error("Redis 库存回滚失败，将对账任务修复: orderNo={}, goodsId={}, error={}",
                    orderNo, order.getSeckillGoodsId(), e.getMessage(), e);
        }

        log.info("订单超时取消成功: orderNo={}, userId={}, goodsId={}, MySQL库存已回滚",
                orderNo, order.getUserId(), order.getSeckillGoodsId());
    }
}
