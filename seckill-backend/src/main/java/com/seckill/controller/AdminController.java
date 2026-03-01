package com.seckill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.Result;
import com.seckill.entity.OrderInfo;
import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.service.GoodsService;
import com.seckill.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.*;

import com.seckill.annotation.AdminOnly;
import com.seckill.service.SeckillService;

/**
 * 管理后台控制器
 * 提供系统监控、商品管理、活动管理等功能
 */
@Slf4j
@Tag(name = "管理后台")
@AdminOnly
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsService goodsService;
    private final OrderService orderService;
    private final SeckillService seckillService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_KEY = "seckill:stock:";

    @Operation(summary = "系统仪表盘数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        // 商品统计
        List<SeckillGoods> allSeckillGoods = seckillGoodsMapper.selectList(null);
        int totalGoods = allSeckillGoods.size();
        LocalDateTime now = LocalDateTime.now();
        long activeGoods = allSeckillGoods.stream()
                .filter(sg -> sg.getSeckillStatus() != 0 // 未发布的不算
                        && sg.getGoodsStatus() != 0 // 下架的不算
                        && sg.getStartDate().isBefore(now)
                        && sg.getEndDate().isAfter(now))
                .count();
        int totalStock = allSeckillGoods.stream().mapToInt(SeckillGoods::getStockCount).sum();

        Map<String, Object> goodsStats = new LinkedHashMap<>();
        goodsStats.put("total", totalGoods);
        goodsStats.put("active", activeGoods);
        goodsStats.put("totalStock", totalStock);
        dashboard.put("goods", goodsStats);

        // 订单统计
        long totalOrders = orderService.count();
        long unpaidOrders = orderService.count(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 0));
        long paidOrders = orderService.count(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 1));
        long cancelledOrders = orderService.count(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getStatus, 4));

        Map<String, Object> orderStats = new LinkedHashMap<>();
        orderStats.put("total", totalOrders);
        orderStats.put("unpaid", unpaidOrders);
        orderStats.put("paid", paidOrders);
        orderStats.put("cancelled", cancelledOrders);
        dashboard.put("orders", orderStats);

        // 各商品库存详情（含Redis实时库存）
        List<Map<String, Object>> stockDetails = new ArrayList<>();
        for (SeckillGoods sg : allSeckillGoods) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seckillGoodsId", sg.getId());
            item.put("goodsName", sg.getGoodsName() != null ? sg.getGoodsName() : "未知");
            item.put("dbStock", sg.getStockCount());
            Object redisStock = redisTemplate.opsForValue().get(STOCK_KEY + sg.getId());
            item.put("redisStock", redisStock != null ? Integer.parseInt(redisStock.toString()) : 0);
            item.put("seckillPrice", sg.getSeckillPrice());
            item.put("startDate", sg.getStartDate());
            item.put("endDate", sg.getEndDate());
            // 动态计算实时状态（不依赖数据库静态 seckill_status 字段）
            int realStatus;
            if (sg.getSeckillStatus() == 0) {
                realStatus = 0; // 管理员设为未发布
            } else if (now.isBefore(sg.getStartDate())) {
                realStatus = 3; // 未到开始时间（即将开始）
            } else if (now.isAfter(sg.getEndDate())) {
                realStatus = 2; // 已结束
            } else {
                realStatus = 1; // 进行中
            }
            item.put("status", realStatus);
            item.put("dbStatus", sg.getSeckillStatus()); // t_seckill_goods.seckill_status
            item.put("goodsStatus", sg.getGoodsStatus() != null ? sg.getGoodsStatus() : 0); // t_seckill_goods.goods_status
            stockDetails.add(item);
        }
        dashboard.put("stockDetails", stockDetails);

        // 系统时间
        dashboard.put("serverTime", LocalDateTime.now());

        return Result.success(dashboard);
    }

    @Operation(summary = "获取所有订单列表(管理员)")
    @GetMapping("/orders")
    public Result<List<OrderInfo>> getAllOrders(
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .orderByDesc(OrderInfo::getCreateTime);
        if (status != null && status >= 0) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        List<OrderInfo> orders = orderService.list(wrapper);
        return Result.success(orders);
    }

    @Operation(summary = "添加秒杀商品")
    @PostMapping("/goods")
    public Result<Void> addSeckillGoods(@Valid @RequestBody com.seckill.dto.SeckillGoodsDTO dto) {
        goodsService.addSeckillGoods(dto);
        return Result.success("添加成功", null);
    }

    @Operation(summary = "更新秒杀商品")
    @PutMapping("/goods/{seckillGoodsId}")
    public Result<Void> updateSeckillGoods(@PathVariable Long seckillGoodsId,
            @Valid @RequestBody com.seckill.dto.SeckillGoodsDTO dto) {
        goodsService.updateSeckillGoods(seckillGoodsId, dto);
        seckillService.reloadSingleSeckillStock(seckillGoodsId);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除秒杀商品")
    @DeleteMapping("/goods/{seckillGoodsId}")
    public Result<Void> deleteSeckillGoods(@PathVariable Long seckillGoodsId) {
        goodsService.deleteSeckillGoods(seckillGoodsId);
        return Result.success("删除成功", null);
    }
}
