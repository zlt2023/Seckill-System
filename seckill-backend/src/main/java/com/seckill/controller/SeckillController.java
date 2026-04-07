package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillGoods;
import com.seckill.service.SeckillGoodsService;
import com.seckill.service.SeckillService;
import com.seckill.utils.UserContext;
import com.seckill.annotation.AccessLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 秒杀控制器 (用户端)
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillGoodsService seckillGoodsService;

    /**
     * 秒杀商品列表 (无需登录)
     */
    @GetMapping("/goods")
    public Result<List<SeckillGoods>> list() {
        return Result.success(seckillGoodsService.listWithGoods());
    }

    /**
     * 秒杀商品详情 (无需登录)
     */
    @GetMapping("/goods/{id}")
    public Result<SeckillGoods> detail(@PathVariable Long id) {
        return Result.success(seckillGoodsService.getDetailById(id));
    }

    /**
     * 获取服务器时间 (倒计时校准, 无需登录)
     */
    @GetMapping("/time")
    public Result<Map<String, Long>> getServerTime() {
        return Result.success(Map.of("serverTime", seckillService.getServerTime()));
    }

    /**
     * 获取动态秒杀路径 Token (需登录，限流：5秒内最多5次请求)
     */
    @AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
    @GetMapping("/path/{seckillGoodsId}")
    public Result<Map<String, String>> getPath(@PathVariable Long seckillGoodsId) {
        Long userId = UserContext.getUserId();
        String path = seckillService.createPath(userId, seckillGoodsId);
        return Result.success(Map.of("pathToken", path));
    }

    /**
     * 执行秒杀 (核心接口, 需登录，结合隐藏路径后，放宽限流频率或可保持严格限流)
     * 此处限制：5秒内同一用户对同一接口最多5次
     */
    @AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
    @PostMapping("/{path}/execute")
    public Result<String> doSeckill(
            @PathVariable String path,
            @RequestParam Long seckillGoodsId) {
        Long userId = UserContext.getUserId();
        String result = seckillService.doSeckill(userId, seckillGoodsId, path);
        return Result.success(result);
    }

    /**
     * 轮询秒杀结果 (需登录)
     */
    @GetMapping("/result/{seckillGoodsId}")
    public Result<Map<String, Object>> getResult(@PathVariable Long seckillGoodsId) {
        Long userId = UserContext.getUserId();
        Map<String, Object> result = seckillService.getSeckillResult(userId, seckillGoodsId);
        return Result.success(result);
    }
}
