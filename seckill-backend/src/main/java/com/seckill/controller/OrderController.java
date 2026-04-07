package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.entity.SeckillOrder;
import com.seckill.exception.BusinessException;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillGoodsService;
import com.seckill.service.SeckillService;
import com.seckill.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单控制器 (用户端)
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SeckillGoodsService seckillGoodsService;
    private final SeckillService seckillService;

    /**
     * 我的订单列表
     */
    @GetMapping("/list")
    public Result<List<SeckillOrder>> list() {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.listByUserId(userId));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<SeckillOrder> detail(@PathVariable String orderNo) {
        SeckillOrder order = orderService.getByOrderNo(orderNo);
        // 校验订单归属
        if (!order.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.success(order);
    }

    /**
     * 模拟支付
     */
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@PathVariable String orderNo) {
        SeckillOrder order = orderService.getByOrderNo(orderNo);
        // 校验归属
        if (!order.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 校验状态
        if (order.getStatus() != SeckillOrder.STATUS_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR,
                    "订单状态异常，无法支付");
        }

        // 更新为已支付
        com.seckill.mapper.SeckillOrderMapper mapper = (com.seckill.mapper.SeckillOrderMapper) orderService.getBaseMapper();
        int updated = mapper.updateStatusWithPayTime(orderNo, SeckillOrder.STATUS_UNPAID, SeckillOrder.STATUS_PAID, LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单状态异常，已被处理");
        }

        log.info("订单支付成功: orderNo={}", orderNo);
        return Result.success();
    }

    /**
     * 取消订单（一人一单，取消后可重新购买）
     * 注意：Redis 操作不在 MySQL 事务中，失败时依赖定时对账任务修复
     */
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        SeckillOrder order = orderService.getByOrderNo(orderNo);
        // 校验归属
        if (!order.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 只有待支付状态可以取消
        if (order.getStatus() != SeckillOrder.STATUS_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR,
                    "只有待支付订单可以取消");
        }

        // 更新为已取消（乐观锁，防止支付与取消并发冲突）
        com.seckill.mapper.SeckillOrderMapper mapper = (com.seckill.mapper.SeckillOrderMapper) orderService.getBaseMapper();
        int updated = mapper.updateStatus(orderNo, SeckillOrder.STATUS_UNPAID, SeckillOrder.STATUS_CANCELLED);
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单已被处理，无法取消");
        }

        // MySQL 库存回滚 (+1)，带边界检查
        int rollbackResult = seckillGoodsService.getBaseMapper().rollbackStock(order.getSeckillGoodsId());
        if (rollbackResult == 0) {
            log.warn("MySQL 库存回滚失败，可能已达总库存上限: orderNo={}, goodsId={}", orderNo, order.getSeckillGoodsId());
            // 不抛异常，订单状态已正确更新为取消，库存问题依赖对账任务修复
        }

        // Redis 库存回滚 + 移除购买标记（允许重新购买）
        try {
            seckillService.rollbackRedis(order.getUserId(), order.getSeckillGoodsId());
        } catch (Exception e) {
            // Redis 回滚失败不影响 MySQL 状态，依赖定时对账任务修复
            log.error("Redis 库存回滚失败，将对账任务修复: orderNo={}, goodsId={}, error={}",
                    orderNo, order.getSeckillGoodsId(), e.getMessage(), e);
        }

        log.info("订单取消成功: orderNo={}, 库存已回滚，允许重新购买", orderNo);
        return Result.success();
    }
}
