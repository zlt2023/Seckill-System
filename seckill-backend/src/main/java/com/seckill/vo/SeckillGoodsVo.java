package com.seckill.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品详情 VO
 */
@Data
public class SeckillGoodsVo {

    /** 商品ID */
    private Long goodsId;

    /** 秒杀商品ID */
    private Long seckillGoodsId;

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

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 秒杀库存 */
    private Integer stockCount;

    /** 秒杀开始时间 */
    private LocalDateTime startDate;

    /** 秒杀结束时间 */
    private LocalDateTime endDate;

    /**
     * 秒杀状态
     * 0-未开始 1-进行中 2-已结束
     */
    private Integer seckillStatus;

    /** 倒计时(秒) 正数:距开始 0:已开始 -1:已结束 */
    private Long remainSeconds;
}
