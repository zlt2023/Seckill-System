package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表
 */
@Data
@TableName("t_order_info")
public class OrderInfo implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long goodsId;

    /** 秒杀商品ID */
    private Long seckillGoodsId;

    /** 收货地址ID */
    private Long deliveryAddrId;

    /** 商品名称 */
    private String goodsName;

    /** 购买数量 */
    private Integer goodsCount;

    /** 商品价格 */
    private BigDecimal goodsPrice;

    /**
     * 订单状态
     * 0-未支付 1-已支付 2-已发货 3-已收货 4-已取消 5-已退款
     */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
