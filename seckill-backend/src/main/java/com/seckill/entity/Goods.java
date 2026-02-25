package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表
 */
@Data
@TableName("t_goods")
public class Goods implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名称 */
    private String goodsName;

    /** 商品标题 */
    private String goodsTitle;

    /** 商品图片 */
    private String goodsImg;

    /** 商品详情 */
    private String goodsDetail;

    /** 商品原价 */
    private BigDecimal goodsPrice;

    /** 商品库存 */
    private Integer goodsStock;

    /** 状态: 0-下架 1-上架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
