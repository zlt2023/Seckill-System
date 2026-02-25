package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品表
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

    /** 秒杀库存 */
    private Integer stockCount;

    /** 秒杀开始时间 */
    private LocalDateTime startDate;

    /** 秒杀结束时间 */
    private LocalDateTime endDate;

    /** 状态: 0-未发布 1-进行中 2-已结束 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
