package com.seckill.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品新增/修改 DTO
 */
@Data
public class SeckillGoodsDTO {

    @NotNull(message = "关联商品ID不能为空")
    private Long goodsId;

    @NotNull(message = "秒杀价格不能为空")
    @Positive(message = "秒杀价格必须大于0")
    private BigDecimal seckillPrice;

    @NotNull(message = "库存不能为空")
    @Positive(message = "库存必须大于0")
    private Integer totalStock;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 状态: 0-下架, 1-上架 */
    private Integer status;
}
