package com.seckill.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.vo.SeckillGoodsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Transactional;
import com.seckill.dto.SeckillGoodsDTO;

/**
 * 商品服务（单表统一样式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService extends ServiceImpl<SeckillGoodsMapper, SeckillGoods> {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GOODS_DETAIL_KEY = "seckill:goods:detail:";
    private static final String GOODS_LIST_KEY = "seckill:goods:list";
    private static final String STOCK_KEY = "seckill:stock:";

    /**
     * 获取秒杀商品列表
     */
    @SuppressWarnings("unchecked")
    public List<SeckillGoodsVo> listSeckillGoods() {
        // 先从缓存获取
        Object cached = redisTemplate.opsForValue().get(GOODS_LIST_KEY);
        if (cached != null) {
            return (List<SeckillGoodsVo>) cached;
        }

        // 查询所有上架的秒杀商品
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);
        List<SeckillGoodsVo> voList = new ArrayList<>();

        for (SeckillGoods sg : seckillGoodsList) {
            if (sg.getGoodsStatus() != 1)
                continue;

            SeckillGoodsVo vo = buildSeckillGoodsVo(sg);
            voList.add(vo);
        }

        // 缓存60秒
        redisTemplate.opsForValue().set(GOODS_LIST_KEY, voList, 60, TimeUnit.SECONDS);
        return voList;
    }

    /**
     * 获取秒杀商品详情
     */
    public SeckillGoodsVo getSeckillGoodsDetail(Long seckillGoodsId) {
        String key = GOODS_DETAIL_KEY + seckillGoodsId;

        // 先从缓存获取
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached instanceof String && "".equals(cached)) {
                return null;
            }
            return (SeckillGoodsVo) cached;
        }

        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null) {
            redisTemplate.opsForValue().set(key, "", 30, TimeUnit.SECONDS);
            return null;
        }

        SeckillGoodsVo vo = buildSeckillGoodsVo(sg);

        // 缓存60秒
        redisTemplate.opsForValue().set(key, vo, 60, TimeUnit.SECONDS);
        return vo;
    }

    /**
     * 构建秒杀商品VO（计算秒杀状态和倒计时）
     */
    private SeckillGoodsVo buildSeckillGoodsVo(SeckillGoods sg) {
        SeckillGoodsVo vo = new SeckillGoodsVo();
        vo.setGoodsId(sg.getId());
        vo.setSeckillGoodsId(sg.getId());
        vo.setGoodsName(sg.getGoodsName());
        vo.setGoodsTitle(sg.getGoodsTitle());
        vo.setGoodsImg(sg.getGoodsImg());
        vo.setGoodsDetail(sg.getGoodsDetail());
        vo.setGoodsPrice(sg.getGoodsPrice());
        vo.setSeckillPrice(sg.getSeckillPrice());
        vo.setStockCount(sg.getStockCount());
        vo.setStartDate(sg.getStartDate());
        vo.setEndDate(sg.getEndDate());

        // 计算秒杀状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sg.getStartDate())) {
            vo.setSeckillStatus(0);
            vo.setRemainSeconds(Duration.between(now, sg.getStartDate()).getSeconds());
        } else if (now.isAfter(sg.getEndDate())) {
            vo.setSeckillStatus(2);
            vo.setRemainSeconds(-1L);
        } else {
            vo.setSeckillStatus(1);
            vo.setRemainSeconds(0L);
        }

        return vo;
    }

    /**
     * 添加秒杀商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillGoods(SeckillGoodsDTO dto) {
        SeckillGoods sg = new SeckillGoods();
        sg.setGoodsName(dto.getGoodsName());
        sg.setGoodsTitle(dto.getGoodsTitle());
        sg.setGoodsImg(dto.getGoodsImg());
        sg.setGoodsDetail(dto.getGoodsDetail());
        sg.setGoodsPrice(dto.getGoodsPrice());

        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setStockCount(dto.getStockCount());
        sg.setStartDate(dto.getStartDate());
        sg.setEndDate(dto.getEndDate());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getStartDate())) {
            sg.setSeckillStatus(0);
        } else if (now.isAfter(dto.getEndDate())) {
            sg.setSeckillStatus(2);
        } else {
            sg.setSeckillStatus(1);
        }
        sg.setGoodsStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        seckillGoodsMapper.insert(sg);

        clearCache();
    }

    /**
     * 更新秒杀商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillGoods(Long seckillGoodsId, SeckillGoodsDTO dto) {
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null)
            return;

        sg.setGoodsName(dto.getGoodsName());
        sg.setGoodsTitle(dto.getGoodsTitle());
        sg.setGoodsImg(dto.getGoodsImg());
        sg.setGoodsDetail(dto.getGoodsDetail());
        sg.setGoodsPrice(dto.getGoodsPrice());

        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setStockCount(dto.getStockCount());
        sg.setStartDate(dto.getStartDate());
        sg.setEndDate(dto.getEndDate());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dto.getStartDate())) {
            sg.setSeckillStatus(0);
        } else if (now.isAfter(dto.getEndDate())) {
            sg.setSeckillStatus(2);
        } else {
            sg.setSeckillStatus(1);
        }
        if (dto.getStatus() != null) {
            sg.setGoodsStatus(dto.getStatus());
        }
        seckillGoodsMapper.updateById(sg);

        clearCache(seckillGoodsId);
    }

    /**
     * 删除秒杀商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSeckillGoods(Long seckillGoodsId) {
        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg != null) {
            seckillGoodsMapper.deleteById(seckillGoodsId);
            clearCache(seckillGoodsId);
        }
    }

    private void clearCache() {
        redisTemplate.delete(GOODS_LIST_KEY);
    }

    private void clearCache(Long seckillGoodsId) {
        redisTemplate.delete(GOODS_LIST_KEY);
        redisTemplate.delete(GOODS_DETAIL_KEY + seckillGoodsId);
        redisTemplate.delete(STOCK_KEY + seckillGoodsId);
    }
}
