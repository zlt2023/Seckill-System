package com.seckill.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品 DTO（P1-4 修复：添加参数校验）
 */
@Data
public class SeckillGoodsDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称不超过100字符")
    private String goodsName;

    @Size(max = 200, message = "商品标题不超过200字符")
    private String goodsTitle;

    private String goodsImg;

    private String goodsDetail;

    @NotNull(message = "商品原价不能为空")
    @Positive(message = "商品原价必须大于0")
    private BigDecimal goodsPrice;

    @NotNull(message = "商品库存不能为空")
    @Min(value = 0, message = "商品库存不能为负数")
    private Integer goodsStock;

    @NotNull(message = "秒杀价不能为空")
    @Positive(message = "秒杀价必须大于0")
    private BigDecimal seckillPrice;

    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存至少为1")
    private Integer stockCount;

    @NotNull(message = "秒杀开始时间不能为空")
    private LocalDateTime startDate;

    @NotNull(message = "秒杀结束时间不能为空")
    private LocalDateTime endDate;

    private Integer status;
}
