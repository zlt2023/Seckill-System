package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.entity.OrderInfo;
import com.seckill.entity.SeckillOrder;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.OrderInfoMapper;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService extends ServiceImpl<OrderInfoMapper, OrderInfo> {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_KEY = "seckill:stock:";
    private static final String ORDER_KEY = "seckill:order:";
    private static final String SECKILL_RESULT_KEY = "seckill:result:";

    /**
     * 获取用户订单列表（支持按状态筛选）
     */
    public List<OrderInfo> getUserOrders(Long userId, Integer status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .orderByDesc(OrderInfo::getCreateTime);
        if (status != null && status >= 0) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        return list(wrapper);
    }

    /**
     * 获取订单详情
     */
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        return getOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getUserId, userId));
    }

    /**
     * 根据用户ID和商品ID查询秒杀订单
     */
    public SeckillOrder getSeckillOrder(Long userId, Long goodsId) {
        return seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getGoodsId, goodsId));
    }

    /**
     * 模拟支付
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, Long orderId) {
        OrderInfo order = getOrderDetail(userId, orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_PAID);
        }
        order.setStatus(1); // 已支付
        order.setPayTime(LocalDateTime.now());
        updateById(order);
        log.info("订单支付成功: orderId={}, userId={}", orderId, userId);
    }

    /**
     * 取消订单（用户主动取消）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        OrderInfo order = getOrderDetail(userId, orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("只有未支付的订单才能取消");
        }
        doCancelOrder(order);
        log.info("用户主动取消订单: orderId={}, userId={}", orderId, userId);
    }

    /**
     * 超时取消订单（MQ消费者调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutCancelOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null || order.getStatus() != 0) {
            return; // 已支付或已取消，不处理
        }
        doCancelOrder(order);
        log.info("订单超时自动取消: orderId={}", orderId);
    }

    /**
     * 执行取消订单逻辑：更新状态 + 恢复库存
     */
    private void doCancelOrder(OrderInfo order) {
        // 1. 更新订单状态为已取消
        order.setStatus(4);
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        // 2. 恢复数据库库存
        if (order.getSeckillGoodsId() != null) {
            seckillGoodsMapper.restoreStock(order.getSeckillGoodsId());
        }

        // 3. 恢复Redis库存
        String stockKey = STOCK_KEY + order.getSeckillGoodsId();
        redisTemplate.opsForValue().increment(stockKey, 1);

        // 4. 清除秒杀相关Redis标记（允许用户再次秒杀）
        String orderKey = ORDER_KEY + order.getUserId() + ":" + order.getGoodsId();
        redisTemplate.delete(orderKey);

        // 5. 清除秒杀结果缓存
        String resultKey = SECKILL_RESULT_KEY + order.getUserId() + ":" + order.getSeckillGoodsId();
        redisTemplate.delete(resultKey);
    }

    /**
     * 用户订单统计
     */
    public Map<String, Object> getOrderStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", count(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getUserId, userId)));
        stats.put("unpaid", count(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getUserId, userId).eq(OrderInfo::getStatus, 0)));
        stats.put("paid", count(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getUserId, userId).eq(OrderInfo::getStatus, 1)));
        stats.put("cancelled", count(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getUserId, userId).eq(OrderInfo::getStatus, 4)));
        return stats;
    }
}
