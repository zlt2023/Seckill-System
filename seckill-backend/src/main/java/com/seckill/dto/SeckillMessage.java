package com.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀消息体 - 发送到 RabbitMQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    /** 用户ID */
    private Long userId;

    /** 秒杀商品ID */
    private Long seckillGoodsId;

    /** 秒杀价格 */
    private BigDecimal seckillPrice;
}
