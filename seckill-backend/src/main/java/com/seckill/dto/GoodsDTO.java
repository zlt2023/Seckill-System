package com.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品新增/修改 DTO
 */
@Data
public class GoodsDTO {

    @NotBlank(message = "商品名称不能为空")
    private String goodsName;

    @NotNull(message = "商品价格不能为空")
    @Positive(message = "商品价格必须大于0")
    private BigDecimal goodsPrice;

    /** 商品图片URL */
    private String goodsImg;

    /** 商品详情 */
    private String goodsDetail;
}
