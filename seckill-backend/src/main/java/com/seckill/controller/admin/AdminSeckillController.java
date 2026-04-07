package com.seckill.controller.admin;

import com.seckill.common.PageResult;
import com.seckill.common.Result;
import com.seckill.dto.SeckillGoodsDTO;
import com.seckill.entity.SeckillGoods;
import com.seckill.service.SeckillGoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 秒杀商品管理
 */
@RestController
@RequestMapping("/api/admin/seckill-goods")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillGoodsService seckillGoodsService;

    /**
     * 秒杀商品列表(分页, 关联商品信息)
     */
    @GetMapping
    public Result<PageResult<SeckillGoods>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(PageResult.of(seckillGoodsService.pageWithGoods(current, size)));
    }

    /**
     * 秒杀商品详情
     */
    @GetMapping("/{id}")
    public Result<SeckillGoods> detail(@PathVariable Long id) {
        return Result.success(seckillGoodsService.getDetailById(id));
    }

    /**
     * 新增秒杀活动
     */
    @PostMapping
    public Result<SeckillGoods> add(@Valid @RequestBody SeckillGoodsDTO dto) {
        return Result.success(seckillGoodsService.addSeckillGoods(dto));
    }

    /**
     * 修改秒杀活动
     */
    @PutMapping("/{id}")
    public Result<SeckillGoods> update(@PathVariable Long id, @Valid @RequestBody SeckillGoodsDTO dto) {
        return Result.success(seckillGoodsService.updateSeckillGoods(id, dto));
    }

    /**
     * 删除秒杀活动
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        seckillGoodsService.deleteSeckillGoods(id);
        return Result.success();
    }

    /**
     * 重新加载缓存
     */
    @PostMapping("/{id}/reload")
    public Result<Void> reloadCache(@PathVariable Long id) {
        seckillGoodsService.reloadCache(id);
        return Result.success();
    }

    /**
     * 上架/下架开关
     */
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        seckillGoodsService.changeStatus(id, status);
        return Result.success();
    }
}
