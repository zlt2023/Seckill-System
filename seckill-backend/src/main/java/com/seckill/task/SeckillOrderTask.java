package com.seckill.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mq.OrderTimeoutConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀系统定时任务 (高级功能)
 * 1. 兜底扫描超时未支付订单 (防 MQ 消息丢失)
 * 2. 定时对账数据一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderTask {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final OrderTimeoutConsumer orderTimeoutConsumer;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String INFO_KEY = "seckill:info:";
    private static final String BOUGHT_KEY = "seckill:bought:";

    /**
     * 兜底取消超时未支付订单
     * 每 10 分钟执行一次，降低数据库查询压力 (主要依赖 MQ 延时重试，这里只是兜底)
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void scanTimeoutOrders() {
        log.info("[定时任务] 开始扫描超时未支付订单...");
        // 找出创建时间在16分钟前，且状态仍为"未支付"的订单 (给MQ 1分钟的宽限期)
        LocalDateTime timeoutLimit = LocalDateTime.now().minusMinutes(16);

        List<SeckillOrder> timeoutOrders = seckillOrderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getStatus, SeckillOrder.STATUS_UNPAID)
                        .le(SeckillOrder::getCreateTime, timeoutLimit));

        int count = 0;
        for (SeckillOrder order : timeoutOrders) {
            try {
                orderTimeoutConsumer.cancelTimeoutOrder(order.getOrderNo());
                count++;
            } catch (Exception e) {
                log.error("[定时任务] 兜底取消订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("[定时任务] 扫描完成，共兜底取消 {} 个订单", count);
    }

    /**
     * 定时对账: Redis库存 与 MySQL库存及订单数的一致性检查
     * 每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcileData() {
        log.info("[定时对账] 开始执行数据一致性检查...");
        List<SeckillGoods> goodsList = seckillGoodsMapper.selectList(null);

        for (SeckillGoods sg : goodsList) {
            Long goodsId = sg.getId();

            // 1. 获取 MySQL 当前库存
            Integer mysqlStock = sg.getAvailableStock();

            // 2. 获取 Redis 当前库存 (从聚合 Hash 中读取)
            Object redisStockObj = stringRedisTemplate.opsForHash().get(INFO_KEY + goodsId, "stock");
            if (redisStockObj == null) {
                continue;
            }
            Integer redisStock;
            try {
                redisStock = Integer.parseInt(redisStockObj.toString());
            } catch (NumberFormatException e) {
                log.error("[对账异常] Redis 库存值格式错误: goodsId={}, value={}", goodsId, redisStockObj);
                continue;
            }

            // 3. 对比库存是否一致 (由于是凌晨，假设没有并发写入)
            if (!mysqlStock.equals(redisStock)) {
                log.error("[!!对账异常!!] 库存不一致告警 - goodsId={}: MySQL({}), Redis({})",
                        goodsId, mysqlStock, redisStock);

                // 自动修复：以MySQL为准同步Redis库存
                try {
                    String infoKey = INFO_KEY + goodsId;
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(infoKey))) {
                        stringRedisTemplate.opsForHash().put(infoKey, "stock", String.valueOf(mysqlStock));
                        log.info("[自动修复] 已将 Redis 库存同步为 MySQL 值: goodsId={}, newStock={}", goodsId, mysqlStock);
                    }
                } catch (Exception e) {
                    log.error("[自动修复失败] goodsId={}, error={}", goodsId, e.getMessage(), e);
                }
            } else {
                log.info("[对账正常] 标的 {}: 库存一致均为 {}", goodsId, mysqlStock);
            }

            // 4. bought Set 一致性检查（发现已取消但仍在 Redis 中的用户，允许其重新购买）
            try {
                Object boughtSetSizeObj = stringRedisTemplate.opsForSet().size(BOUGHT_KEY + goodsId);
                if (boughtSetSizeObj != null && (Long) boughtSetSizeObj > 0) {
                    // 获取 Redis 中所有已购买用户
                    java.util.Set<String> boughtUsers = stringRedisTemplate.opsForSet().members(BOUGHT_KEY + goodsId);
                    if (boughtUsers != null && !boughtUsers.isEmpty()) {
                        for (String userIdStr : boughtUsers) {
                            Long userId = Long.parseLong(userIdStr);
                            // 检查该用户是否有已取消或已超时的订单
                            Long cancelledCount = seckillOrderMapper.selectCount(
                                    new LambdaQueryWrapper<SeckillOrder>()
                                            .eq(SeckillOrder::getUserId, userId)
                                            .eq(SeckillOrder::getSeckillGoodsId, goodsId)
                                            .in(SeckillOrder::getStatus, SeckillOrder.STATUS_CANCELLED, SeckillOrder.STATUS_TIMEOUT)
                            );
                            if (cancelledCount > 0) {
                                // 用户订单已取消/超时，但仍在 Redis bought Set 中，移除以允许重新购买
                                stringRedisTemplate.opsForSet().remove(BOUGHT_KEY + goodsId, userIdStr);
                                log.info("[bought Set 修复] 已移除已取消/超时订单用户: goodsId={}, userId={}", goodsId, userId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[bought Set 对账失败] goodsId={}, error={}", goodsId, e.getMessage(), e);
            }

            // 5. 销量对账（售出总量 = 总库存 - 剩余库存，应等于有效订单总量：已支付+待支付）
            // 注意：已取消和已超时的订单不应计入销量
            int soldQuantity = sg.getTotalStock() - mysqlStock;
            Long orderCount = seckillOrderMapper.selectCount(
                    new LambdaQueryWrapper<SeckillOrder>()
                            .eq(SeckillOrder::getSeckillGoodsId, goodsId)
                            .notIn(SeckillOrder::getStatus, SeckillOrder.STATUS_CANCELLED, SeckillOrder.STATUS_TIMEOUT));

            if (soldQuantity != orderCount.intValue()) {
                log.error("[!!对账异常!!] 订单数不一致告警 - goodsId={}: 理论应售({}), 实际订单数({})",
                        goodsId, soldQuantity, orderCount);
            }
        }
        log.info("[定时对账] 数据一致性检查完毕.");
    }
}
