package com.seckill.controller;

import com.seckill.annotation.RateLimit;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.service.SeckillService;
import com.seckill.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀控制器
 * 安全层级:
 * 1. 验证码 → 分散请求
 * 2. 获取秒杀path → 接口隐藏
 * 3. @RateLimit → 限流
 * 4. 内存标记+Redis预减+MQ异步 → 高并发处理
 */
@Tag(name = "秒杀模块")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "获取秒杀路径(验证码通过后)")
    @GetMapping("/path/{seckillGoodsId}")
    @RateLimit(seconds = 5, maxCount = 5)
    public Result<Map<String, String>> getSeckillPath(
            @PathVariable Long seckillGoodsId,
            @RequestParam int captcha) {
        Long userId = UserContext.getCurrentUserId();
        String path = seckillService.createSeckillPath(userId, seckillGoodsId, captcha);
        return Result.success(Map.of("path", path));
    }

    @Operation(summary = "执行秒杀(需动态path)")
    @PostMapping("/{path}/do/{seckillGoodsId}")
    @RateLimit(seconds = 5, maxCount = 3)
    public Result<Void> doSeckill(
            @PathVariable String path,
            @PathVariable Long seckillGoodsId) {
        Long userId = UserContext.getCurrentUserId();

        // 验证秒杀路径
        boolean validPath = seckillService.validateSeckillPath(userId, seckillGoodsId, path);
        if (!validPath) {
            return Result.error(ResultCode.SECKILL_PATH_INVALID);
        }

        seckillService.doSeckill(userId, seckillGoodsId);
        return Result.success("秒杀请求已提交，请等待结果", null);
    }

    @Operation(summary = "查询秒杀结果")
    @GetMapping("/result/{seckillGoodsId}")
    public Result<Long> getResult(@PathVariable Long seckillGoodsId) {
        Long userId = UserContext.getCurrentUserId();
        Long result = seckillService.getResult(userId, seckillGoodsId);
        if (result == 0L) {
            return Result.error(ResultCode.SECKILL_QUEUING);
        } else if (result < 0) {
            return Result.error(ResultCode.SECKILL_STOCK_EMPTY);
        }
        return Result.success("秒杀成功", result);
    }
}
