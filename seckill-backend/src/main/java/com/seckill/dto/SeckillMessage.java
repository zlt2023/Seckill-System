package com.seckill.dto;

import lombok.Data;

/**
 * 秒杀消息DTO - 发送到MQ的消息体
 */
@Data
public class SeckillMessage {

    /** 用户ID */
    private Long userId;

    /** 秒杀商品ID */
    private Long seckillGoodsId;
}
