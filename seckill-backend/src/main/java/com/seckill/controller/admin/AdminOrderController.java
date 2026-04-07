package com.seckill.controller.admin;

import com.seckill.common.PageResult;
import com.seckill.common.Result;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 订单管理
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * 全部订单列表(分页, 关联用户+商品信息)
     */
    @GetMapping
    public Result<PageResult<SeckillOrder>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(PageResult.of(orderService.pageAll(current, size)));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<SeckillOrder> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }
}
