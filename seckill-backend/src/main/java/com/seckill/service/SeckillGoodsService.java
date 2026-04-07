package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.dto.SeckillGoodsDTO;
import com.seckill.entity.SeckillGoods;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.SeckillGoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀商品服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillGoodsService extends ServiceImpl<SeckillGoodsMapper, SeckillGoods> {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // ===== Redis Key 前缀 =====
    private static final String BOUGHT_KEY = "seckill:bought:";
    private static final String INFO_KEY = "seckill:info:"; // 新增：生命周期和数据缓存

    /**
     * 查询秒杀商品列表(关联商品信息) - 用户端
     */
    public List<SeckillGoods> listWithGoods() {
        return seckillGoodsMapper.selectAllWithGoods();
    }

    /**
     * 分页查询秒杀商品 - 管理端
     */
    public IPage<SeckillGoods> pageWithGoods(int current, int size) {
        return seckillGoodsMapper.selectPageWithGoods(new Page<>(current, size));
    }

    /**
     * 查询单个秒杀商品详情(关联商品信息)
     */
    public SeckillGoods getDetailById(Long id) {
        SeckillGoods sg = seckillGoodsMapper.selectByIdWithGoods(id);
        if (sg == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }
        return sg;
    }

    /**
     * 新增秒杀活动
     */
    public SeckillGoods addSeckillGoods(SeckillGoodsDTO dto) {
        // 校验时间
        validateTime(dto);

        // 检查该商品是否已有秒杀活动
        long count = count(new LambdaQueryWrapper<SeckillGoods>()
                .eq(SeckillGoods::getGoodsId, dto.getGoodsId()));
        if (count > 0) {
            throw new BusinessException("该商品已存在秒杀活动");
        }

        // 设置属性
        SeckillGoods sg = new SeckillGoods();
        sg.setGoodsId(dto.getGoodsId());
        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setTotalStock(dto.getTotalStock());
        sg.setAvailableStock(dto.getTotalStock());
        sg.setStartTime(dto.getStartTime());
        sg.setEndTime(dto.getEndTime());
        // 创建完默认为下架状态
        sg.setStatus(SeckillGoods.STATUS_OFF);
        save(sg);

        // 如果活动尚未结束，则预热进 Redis
        if (sg.getEndTime().isAfter(LocalDateTime.now())) {
            loadToRedis(sg);
        }

        log.info("新增秒杀活动: id={}, goodsId={}, stock={}", sg.getId(), sg.getGoodsId(), sg.getTotalStock());
        return sg;
    }

    /**
     * 修改秒杀活动
     */
    public SeckillGoods updateSeckillGoods(Long id, SeckillGoodsDTO dto) {
        SeckillGoods sg = getById(id);
        if (sg == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }

        if (sg.getStatus() == SeckillGoods.STATUS_ON) {
            throw new BusinessException("已上架的活动不允许编辑，请先下架！");
        }

        validateTime(dto);

        // 如果更换了关联商品，检查新商品是否已有活动
        if (!sg.getGoodsId().equals(dto.getGoodsId())) {
            long count = count(new LambdaQueryWrapper<SeckillGoods>()
                    .eq(SeckillGoods::getGoodsId, dto.getGoodsId())
                    .ne(SeckillGoods::getId, id));
            if (count > 0) {
                throw new BusinessException("该商品已存在秒杀活动");
            }
        }

        // 清除旧缓存
        clearRedisCache(sg.getId());

        sg.setGoodsId(dto.getGoodsId());
        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setTotalStock(dto.getTotalStock());
        sg.setAvailableStock(dto.getTotalStock());
        sg.setStartTime(dto.getStartTime());
        sg.setEndTime(dto.getEndTime());
        if (dto.getStatus() != null) {
            sg.setStatus(dto.getStatus());
        }
        updateById(sg);

        // 如果活动尚未结束，则同步/预热进 Redis
        if (sg.getEndTime().isAfter(LocalDateTime.now())) {
            loadToRedis(sg);
        }

        log.info("修改秒杀活动: id={}", id);
        return sg;
    }

    /**
     * 删除秒杀活动
     */
    public void deleteSeckillGoods(Long id) {
        SeckillGoods sg = getById(id);
        if (sg == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }
        if (sg.getStatus() == SeckillGoods.STATUS_ON) {
            throw new BusinessException("已上架的活动不允许删除，请先下架！");
        }
        // 清除 Redis 缓存
        clearRedisCache(id);
        removeById(id);
        log.info("删除秒杀活动: id={}", id);
    }

    /**
     * 上架/下架切换
     */
    public void changeStatus(Long id, Integer status) {
        if (status != SeckillGoods.STATUS_ON && status != SeckillGoods.STATUS_OFF) {
            throw new BusinessException("非法状态值");
        }

        SeckillGoods sg = getById(id);
        if (sg == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }

        // 1. 更新数据库状态
        sg.setStatus(status);
        updateById(sg);

        // 2. 将控制指令同步到 Redis (避免全量覆盖库存等变量)
        String infoKey = INFO_KEY + id;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(infoKey))) {
            stringRedisTemplate.opsForHash().put(infoKey, "status", String.valueOf(status));
            log.info("同步 Redis 开关: id={}, status={}", id, status);
        } else if (status == SeckillGoods.STATUS_ON && sg.getEndTime().isAfter(LocalDateTime.now())) {
            // 如果缓存不存在且准备上架，全量预热一下
            loadToRedis(sg);
        }
    }

    /**
     * 重新加载单个秒杀商品到 Redis (仅下架可执行，避免覆盖在线运行状态)
     */
    public void reloadCache(Long id) {
        SeckillGoods sg = getById(id);
        if (sg == null) {
            throw new BusinessException(ResultCode.SECKILL_GOODS_NOT_FOUND);
        }
        if (sg.getStatus() == SeckillGoods.STATUS_ON) {
            throw new BusinessException("必须下架后才能执行重载全量缓存，避免影响运行内存记录");
        }

        clearRedisCache(id);
        if (sg.getEndTime().isAfter(LocalDateTime.now())) {
            loadToRedis(sg);
        }
        log.info("重新加载缓存: id={}, stock={}", id, sg.getAvailableStock());
    }

    /**
     * 按状态查询
     */
    public List<SeckillGoods> listByStatus(int status) {
        return seckillGoodsMapper.selectByStatus(status);
    }

    // ===== 私有方法 =====

    /**
     * 加载秒杀商品到 Redis
     */
    private void loadToRedis(SeckillGoods sg) {
        String goodsIdStr = String.valueOf(sg.getId());
        String infoKey = INFO_KEY + goodsIdStr;

        // 1. 计算过期时间 (TTL = 结束时间 + 1小时兜底缓冲)
        java.time.Duration ttl = java.time.Duration.between(LocalDateTime.now(), sg.getEndTime()).plusHours(1);

        // 2. 聚合所有数据到唯一的 Info Hash 中
        long startTime = sg.getStartTime().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
        long endTime = sg.getEndTime().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();

        java.util.Map<String, String> infoMap = new java.util.HashMap<>();
        infoMap.put("startTime", String.valueOf(startTime));
        infoMap.put("endTime", String.valueOf(endTime));
        infoMap.put("price", sg.getSeckillPrice().toString());
        infoMap.put("stock", String.valueOf(sg.getAvailableStock()));
        infoMap.put("status", String.valueOf(sg.getStatus()));

        stringRedisTemplate.opsForHash().putAll(infoKey, infoMap);

        // 3. 设置统一 TTL
        if (!ttl.isNegative()) {
            stringRedisTemplate.expire(infoKey, ttl);
            stringRedisTemplate.expire(BOUGHT_KEY + goodsIdStr, ttl);
        }

        log.info("加载生命周期聚合缓存: id={}, stock={}, status={}, TTL={}s", sg.getId(), sg.getAvailableStock(), sg.getStatus(),
                ttl.getSeconds());
    }

    /**
     * 清除 Redis 缓存
     */
    private void clearRedisCache(Long id) {
        String goodsIdStr = String.valueOf(id);
        int deletedCount = 0;
        String[] patterns = {
                INFO_KEY + goodsIdStr,
                BOUGHT_KEY + goodsIdStr,
                "seckill:path:*:" + goodsIdStr,
                "seckill:result:*:" + goodsIdStr,
        };

        for (String pattern : patterns) {
            java.util.Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                deletedCount += keys.size();
            }
        }

        log.info("完全清除秒杀活动缓存 (包括 Hash, Set 及所有动态路径/结果): id={}, 清理Key数量={}", id, deletedCount);
    }

    /**
     * 校验秒杀时间
     */
    private void validateTime(SeckillGoodsDTO dto) {
        if (dto.getEndTime().isBefore(dto.getStartTime()) || dto.getEndTime().isEqual(dto.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }
}
