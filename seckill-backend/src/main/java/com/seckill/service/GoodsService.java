package com.seckill.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.dto.GoodsDTO;
import com.seckill.entity.Goods;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService extends ServiceImpl<GoodsMapper, Goods> {

    /**
     * 分页查询商品
     */
    public IPage<Goods> pageGoods(int current, int size) {
        return page(new Page<>(current, size));
    }

    /**
     * 新增商品
     */
    public Goods addGoods(GoodsDTO dto) {
        Goods goods = new Goods();
        goods.setGoodsName(dto.getGoodsName());
        goods.setGoodsPrice(dto.getGoodsPrice());
        goods.setGoodsImg(dto.getGoodsImg());
        goods.setGoodsDetail(dto.getGoodsDetail());
        save(goods);
        log.info("新增商品: id={}, name={}", goods.getId(), goods.getGoodsName());
        return goods;
    }

    /**
     * 修改商品
     */
    public Goods updateGoods(Long id, GoodsDTO dto) {
        Goods goods = getById(id);
        if (goods == null) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }
        goods.setGoodsName(dto.getGoodsName());
        goods.setGoodsPrice(dto.getGoodsPrice());
        goods.setGoodsImg(dto.getGoodsImg());
        goods.setGoodsDetail(dto.getGoodsDetail());
        updateById(goods);
        log.info("修改商品: id={}", id);
        return goods;
    }

    /**
     * 删除商品
     */
    public void deleteGoods(Long id) {
        Goods goods = getById(id);
        if (goods == null) {
            throw new BusinessException(ResultCode.GOODS_NOT_FOUND);
        }
        removeById(id);
        log.info("删除商品: id={}", id);
    }
}
