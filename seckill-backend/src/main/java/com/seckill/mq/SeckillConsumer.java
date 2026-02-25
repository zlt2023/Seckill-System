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
