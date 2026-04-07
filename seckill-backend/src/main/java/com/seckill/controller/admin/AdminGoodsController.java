package com.seckill.controller.admin;

import com.seckill.common.PageResult;
import com.seckill.common.Result;
import com.seckill.dto.GoodsDTO;
import com.seckill.entity.Goods;
import com.seckill.service.GoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 商品管理
 */
@RestController
@RequestMapping("/api/admin/goods")
@RequiredArgsConstructor
public class AdminGoodsController {

    private final GoodsService goodsService;

    /**
     * 商品列表(分页)
     */
    @GetMapping
    public Result<PageResult<Goods>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(PageResult.of(goodsService.pageGoods(current, size)));
    }

    /**
     * 商品详情
     */
    @GetMapping("/{id}")
    public Result<Goods> detail(@PathVariable Long id) {
        return Result.success(goodsService.getById(id));
    }

    /**
     * 新增商品
     */
    @PostMapping
    public Result<Goods> add(@Valid @RequestBody GoodsDTO dto) {
        return Result.success(goodsService.addGoods(dto));
    }

    /**
     * 修改商品
     */
    @PutMapping("/{id}")
    public Result<Goods> update(@PathVariable Long id, @Valid @RequestBody GoodsDTO dto) {
        return Result.success(goodsService.updateGoods(id, dto));
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        goodsService.deleteGoods(id);
        return Result.success();
    }
}
