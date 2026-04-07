package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 */
@Data
@TableName("t_seckill_order")
public class SeckillOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号(雪花算法) */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 秒杀商品ID */
    private Long seckillGoodsId;

    /** 成交价格(快照) */
    private BigDecimal seckillPrice;

    /** 状态: 0-待支付, 1-已支付, 2-已取消, 3-已超时 */
    private Integer status;

    /** 下单时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 非数据库字段: 关联查询用 =====
    @TableField(exist = false)
    private String goodsName;

    @TableField(exist = false)
    private String goodsImg;

    @TableField(exist = false)
    private String username;

    // ===== 状态常量 =====
    public static final int STATUS_UNPAID = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_CANCELLED = 2;
    public static final int STATUS_TIMEOUT = 3;
}
