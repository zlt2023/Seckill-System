package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RabbitMQ 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillConsumer {

    private final SeckillService seckillService;
    private final OrderService orderService;

    /**
     * 监听秒杀队列 - 异步创建订单
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillMessage(SeckillMessage message, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("收到秒杀消息: userId={}, seckillGoodsId={}",
                    message.getUserId(), message.getSeckillGoodsId());

            seckillService.executeSeckill(message.getUserId(), message.getSeckillGoodsId());

            // 手动ACK
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("秒杀消息处理失败: {}", e.getMessage(), e);
            // 这里非常核心：如果 executeSeckill 方法（有@Transactional注解）抛出异常发生回滚
            // Redis 中的库存已经被预扣除了，排队成功的标记也写进去了
            // 所以我们需要通过 handleSeckillFail 将 Redis 的标记和库存全数回退，保证数据一致性（防止少卖）
            try {
                seckillService.handleSeckillFail(message.getUserId(), message.getSeckillGoodsId());
            } catch (Exception compensationError) {
                log.error("补偿还原秒杀库存失败: {}", compensationError.getMessage(), compensationError);
            }
            // 拒绝并不重新入队（避免无限循环）
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听死信队列 - 处理超时未支付订单
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_QUEUE)
    public void handleOrderTimeout(Long orderId, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("收到订单超时消息: orderId={}", orderId);
            orderService.timeoutCancelOrder(orderId);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("订单超时处理失败: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
