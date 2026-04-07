package com.seckill.service;

import com.seckill.entity.SeckillGoods;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热服务
 * 项目启动时将进行中的秒杀商品加载到 Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupService implements ApplicationRunner {

    private final SeckillGoodsService seckillGoodsService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String INFO_KEY = "seckill:info:";
    private static final String BOUGHT_KEY = "seckill:bought:";

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== 秒杀缓存预热开始 ==========");
        warmup();
        log.info("========== 秒杀缓存预热完成 ==========");
    }

    /**
     * 预热：清除旧缓存，重新加载所有进行中的秒杀商品到 Redis
     */
    public void warmup() {
        // 1. 先清除所有秒杀相关的旧缓存，防止已结束活动的残留数据
        clearAllSeckillKeys();

        // 2. 重新加载所有“上架”且“未过时”的秒杀商品
        java.time.LocalDateTime nowTime = java.time.LocalDateTime.now();
        List<SeckillGoods> activeList = seckillGoodsService.list().stream()
                .filter(sg -> sg.getStatus() == SeckillGoods.STATUS_ON && sg.getEndTime().isAfter(nowTime))
                .toList();

        if (activeList.isEmpty()) {
            log.info("当前无待抢购或进行中的秒杀活动");
            return;
        }

        for (SeckillGoods sg : activeList) {
            String goodsIdStr = String.valueOf(sg.getId());
            String infoKey = INFO_KEY + goodsIdStr;

            // 聚合所有数据到唯一的 Info Hash 中
            long startTime = sg.getStartTime().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
            long endTime = sg.getEndTime().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();

            java.util.Map<String, String> infoMap = new java.util.HashMap<>();
            infoMap.put("startTime", String.valueOf(startTime));
            infoMap.put("endTime", String.valueOf(endTime));
            infoMap.put("price", sg.getSeckillPrice().toString());
            infoMap.put("stock", String.valueOf(sg.getAvailableStock()));
            infoMap.put("status", String.valueOf(sg.getStatus()));

            stringRedisTemplate.opsForHash().putAll(infoKey, infoMap);

            // 设置统一 TTL (结束时间后 1 小时自动销毁，节省内存并作为生命周期管理)
            java.time.Duration ttl = java.time.Duration.between(java.time.LocalDateTime.now(), sg.getEndTime())
                    .plusHours(1);
            if (!ttl.isNegative()) {
                stringRedisTemplate.expire(infoKey, ttl);
                stringRedisTemplate.expire(BOUGHT_KEY + goodsIdStr, ttl);
            }

            log.info("加载生命周期聚合缓存: id={}, stock={}, status={}, TTL={}s", sg.getId(), sg.getAvailableStock(),
                    sg.getStatus(), ttl.getSeconds());
        }

        log.info("共预热 {} 个秒杀商品聚合缓存", String.valueOf(activeList.size()));
    }

    private void clearAllSeckillKeys() {
        int deletedCount = 0;
        // 包含所有旧版本可能遗留的 key 模式，以及当前系统的所有动态 key
        String[] patterns = {
                "seckill:info:*",
                "seckill:bought:*",
                "seckill:path:*",
                "seckill:result:*"
        };

        for (String pattern : patterns) {
            java.util.Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                deletedCount += keys.size();
            }
        }

        log.info("已完成秒杀缓存全量深度清理 (共清理 {} 个历史/动态 Key patterns)", deletedCount);
    }
}
