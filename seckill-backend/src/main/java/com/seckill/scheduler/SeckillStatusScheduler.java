package com.seckill.scheduler;

import com.seckill.mapper.SeckillGoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀活动状态同步定时任务
 *
 * <p>
 * 问题背景：t_seckill_goods.status 是静态字段，活动到期后不会自动更新，
 * 导致前端仍显示"进行中"。本调度器每分钟批量同步一次数据库状态。
 * </p>
 *
 * <p>
 * 状态说明：
 * 0 - 未发布（管理员手动设置）
 * 1 - 进行中（start_date <= now <= end_date）
 * 2 - 已结束（now > end_date）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStatusScheduler {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GOODS_LIST_KEY = "seckill:goods:list";

    /**
     * 每分钟执行一次：将超期活动的 status 改为 2（已结束）
     */
    @Scheduled(fixedRate = 60_000)
    public void syncExpiredStatus() {
        try {
            int ended = seckillGoodsMapper.updateExpiredToEnded();
            int reactivated = seckillGoodsMapper.updateReactivatedToOngoing();

            if (ended > 0 || reactivated > 0) {
                log.info("[状态同步] 已结束: {} 条, 重新激活: {} 条", ended, reactivated);
                // 清除商品列表缓存，让前端下次刷新能获取最新状态
                redisTemplate.delete(GOODS_LIST_KEY);
                log.info("[状态同步] 已清除 Redis 商品列表缓存");
            }
        } catch (Exception e) {
            log.error("[状态同步] 执行异常", e);
        }
    }
}
