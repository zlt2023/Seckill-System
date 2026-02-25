package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.GoodsService;
import com.seckill.vo.SeckillGoodsVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品控制器
 */
@Tag(name = "商品模块")
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    @Operation(summary = "获取秒杀商品列表")
    @GetMapping("/list")
    public Result<List<SeckillGoodsVo>> listSeckillGoods() {
        List<SeckillGoodsVo> list = goodsService.listSeckillGoods();
        return Result.success(list);
    }

    @Operation(summary = "获取秒杀商品详情")
    @GetMapping("/detail/{seckillGoodsId}")
    public Result<SeckillGoodsVo> getSeckillGoodsDetail(@PathVariable Long seckillGoodsId) {
        SeckillGoodsVo vo = goodsService.getSeckillGoodsDetail(seckillGoodsId);
        return Result.success(vo);
    }
}
