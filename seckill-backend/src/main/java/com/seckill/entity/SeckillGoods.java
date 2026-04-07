package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品实体
 */
@Data
@TableName("t_seckill_goods")
public class SeckillGoods implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联商品ID */
    private Long goodsId;

    /** 秒杀价格 */
    private BigDecimal seckillPrice;

    /** 秒杀总库存(初始值,不变) */
    private Integer totalStock;

    /** 剩余可用库存 */
    private Integer availableStock;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 状态: 0-下架, 1-上架 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 非数据库字段: 关联查询用 =====
    @TableField(exist = false)
    private String goodsName;

    @TableField(exist = false)
    private BigDecimal goodsPrice;

    @TableField(exist = false)
    private String goodsImg;

    // ===== 状态常量 =====
    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;
}

