package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀订单表 (用于快速判断是否重复秒杀)
 */
@Data
@TableName("t_seckill_order")
public class SeckillOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 关联订单ID */
    private Long orderId;

    /** 关联商品ID */
    private Long goodsId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
