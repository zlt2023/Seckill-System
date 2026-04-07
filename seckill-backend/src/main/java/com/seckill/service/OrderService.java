package com.seckill.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.entity.SeckillOrder;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService extends ServiceImpl<SeckillOrderMapper, SeckillOrder> {

    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * 查询用户订单列表(关联商品信息)
     */
    public List<SeckillOrder> listByUserId(Long userId) {
        return seckillOrderMapper.selectByUserId(userId);
    }

    /**
     * 根据订单编号查询订单详情
     */
    public SeckillOrder getByOrderNo(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 管理端 - 分页查询全部订单
     */
    public IPage<SeckillOrder> pageAll(int current, int size) {
        return seckillOrderMapper.selectPageAll(new Page<>(current, size));
    }
}
