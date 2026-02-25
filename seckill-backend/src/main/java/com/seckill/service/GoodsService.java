package com.seckill.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.entity.Goods;
import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.GoodsMapper;
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
 * 商品服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService extends ServiceImpl<GoodsMapper, Goods> {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GOODS_DETAIL_KEY = "seckill:goods:detail:";
    private static final String GOODS_LIST_KEY = "seckill:goods:list";

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

        // 查询所有秒杀商品
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);
        List<SeckillGoodsVo> voList = new ArrayList<>();

        for (SeckillGoods sg : seckillGoodsList) {
            Goods goods = getById(sg.getGoodsId());
            if (goods == null || goods.getStatus() != 1)
                continue;

            SeckillGoodsVo vo = buildSeckillGoodsVo(goods, sg);
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
            return (SeckillGoodsVo) cached;
        }

        SeckillGoods sg = seckillGoodsMapper.selectById(seckillGoodsId);
        if (sg == null)
            return null;

        Goods goods = getById(sg.getGoodsId());
        if (goods == null)
            return null;

        SeckillGoodsVo vo = buildSeckillGoodsVo(goods, sg);

        // 缓存60秒
        redisTemplate.opsForValue().set(key, vo, 60, TimeUnit.SECONDS);
        return vo;
    }

    /**
     * 构建秒杀商品VO（计算秒杀状态和倒计时）
     */
    private SeckillGoodsVo buildSeckillGoodsVo(Goods goods, SeckillGoods sg) {
        SeckillGoodsVo vo = new SeckillGoodsVo();
        vo.setGoodsId(goods.getId());
        vo.setSeckillGoodsId(sg.getId());
        vo.setGoodsName(goods.getGoodsName());
        vo.setGoodsTitle(goods.getGoodsTitle());
        vo.setGoodsImg(goods.getGoodsImg());
        vo.setGoodsDetail(goods.getGoodsDetail());
        vo.setGoodsPrice(goods.getGoodsPrice());
        vo.setSeckillPrice(sg.getSeckillPrice());
        vo.setStockCount(sg.getStockCount());
        vo.setStartDate(sg.getStartDate());
        vo.setEndDate(sg.getEndDate());

        // 计算秒杀状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sg.getStartDate())) {
            // 秒杀未开始
            vo.setSeckillStatus(0);
            vo.setRemainSeconds(Duration.between(now, sg.getStartDate()).getSeconds());
        } else if (now.isAfter(sg.getEndDate())) {
            // 秒杀已结束
            vo.setSeckillStatus(2);
            vo.setRemainSeconds(-1L);
        } else {
            // 秒杀进行中
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
        // 1. 添加普通商品
        Goods goods = new Goods();
        goods.setGoodsName(dto.getGoodsName());
        goods.setGoodsTitle(dto.getGoodsTitle());
        goods.setGoodsImg(dto.getGoodsImg());
        goods.setGoodsDetail(dto.getGoodsDetail());
        goods.setGoodsPrice(dto.getGoodsPrice());
        goods.setGoodsStock(dto.getGoodsStock());
        goods.setStatus(dto.getStatus());
        save(goods);

        // 2. 添加秒杀商品
        SeckillGoods sg = new SeckillGoods();
        sg.setGoodsId(goods.getId());
        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setStockCount(dto.getStockCount());
        sg.setStartDate(dto.getStartDate());
        sg.setEndDate(dto.getEndDate());
        // 使用 DTO 中的状态值（不硬编码为1），让调度任务按时间自动同步
        sg.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
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

        // 更新普通商品
        Goods goods = getById(sg.getGoodsId());
        if (goods != null) {
            goods.setGoodsName(dto.getGoodsName());
            goods.setGoodsTitle(dto.getGoodsTitle());
            goods.setGoodsImg(dto.getGoodsImg());
            goods.setGoodsDetail(dto.getGoodsDetail());
            goods.setGoodsPrice(dto.getGoodsPrice());
            goods.setGoodsStock(dto.getGoodsStock());
            goods.setStatus(dto.getStatus());
            updateById(goods);
        }

        // 更新秒杀商品
        sg.setSeckillPrice(dto.getSeckillPrice());
        sg.setStockCount(dto.getStockCount());
        sg.setStartDate(dto.getStartDate());
        sg.setEndDate(dto.getEndDate());
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
            // 删除秒杀商品（软删除或物理删除皆可，MybatisPlus 配置了软删）
            seckillGoodsMapper.deleteById(seckillGoodsId);
            // 删除主商品
            removeById(sg.getGoodsId());
            clearCache(seckillGoodsId);
        }
    }

    private void clearCache() {
        redisTemplate.delete(GOODS_LIST_KEY);
    }

    private void clearCache(Long seckillGoodsId) {
        redisTemplate.delete(GOODS_LIST_KEY);
        redisTemplate.delete(GOODS_DETAIL_KEY + seckillGoodsId);
    }
}
