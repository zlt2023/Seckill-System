package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.OrderInfo;
import com.seckill.service.OrderService;
import com.seckill.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单控制器
 */
@Tag(name = "订单模块")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "获取用户订单列表(支持状态筛选)")
    @GetMapping("/list")
    public Result<List<OrderInfo>> listOrders(
            @RequestParam(required = false) Integer status) {
        Long userId = UserContext.getCurrentUserId();
        List<OrderInfo> orders = orderService.getUserOrders(userId, status);
        return Result.success(orders);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/detail/{orderId}")
    public Result<OrderInfo> getOrderDetail(@PathVariable Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        OrderInfo order = orderService.getOrderDetail(userId, orderId);
        return Result.success(order);
    }

    @Operation(summary = "模拟支付")
    @PostMapping("/pay/{orderId}")
    public Result<Void> payOrder(@PathVariable Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        orderService.payOrder(userId, orderId);
        return Result.success("支付成功", null);
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel/{orderId}")
    public Result<Void> cancelOrder(@PathVariable Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        orderService.cancelOrder(userId, orderId);
        return Result.success("订单已取消", null);
    }

    @Operation(summary = "订单统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getOrderStats() {
        Long userId = UserContext.getCurrentUserId();
        Map<String, Object> stats = orderService.getOrderStats(userId);
        return Result.success(stats);
    }
}
