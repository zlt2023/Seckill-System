package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体
 */
@Data
@TableName("t_goods")
public class Goods implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名称 */
    private String goodsName;

    /** 商品原价 */
    private BigDecimal goodsPrice;

    /** 商品图片URL */
    private String goodsImg;

    /** 商品详情(富文本) */
    private String goodsDetail;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
