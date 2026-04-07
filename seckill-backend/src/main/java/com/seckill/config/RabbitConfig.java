package com.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 队列拓扑:
 *   seckill.exchange → seckill.order.queue (秒杀下单)
 *   seckill.dlx.exchange → seckill.dlq.queue (死信)
 *   delay.exchange → delay.queue (TTL) → seckill.dlx.exchange → order.cancel.queue (超时取消)
 */
@Configuration
public class RabbitConfig {

    // ===== 秒杀下单 =====
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // ===== 死信 =====
    public static final String DLX_EXCHANGE = "seckill.dlx.exchange";
    public static final String DLQ_QUEUE = "seckill.dlq.queue";
    public static final String DLX_ROUTING_KEY = "seckill.dlx";

    // ===== 延迟队列 (订单超时取消) =====
    public static final String DELAY_EXCHANGE = "delay.exchange";
    public static final String DELAY_QUEUE = "delay.queue";
    public static final String DELAY_ROUTING_KEY = "delay.order";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";
    /** 订单超时时间: 15分钟 */
    public static final int ORDER_TIMEOUT_MS = 15 * 60 * 1000;

    // ================================================================
    // JSON 消息转换器
    // ================================================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // ================================================================
    // 秒杀下单队列 (带死信配置)
    // ================================================================

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    // ================================================================
    // 死信队列 (消费失败的消息最终到这里)
    // ================================================================

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    // ================================================================
    // 延迟队列 (TTL + DLX 实现订单超时取消)
    // ================================================================

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    /**
     * 延迟队列: 消息TTL到期后转发到 DLX → order.cancel.queue
     */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .ttl(ORDER_TIMEOUT_MS)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue())
                .to(delayExchange())
                .with(DELAY_ROUTING_KEY);
    }

    /**
     * 订单取消队列: 接收超时消息
     */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(dlxExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }
}
