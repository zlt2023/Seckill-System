package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

    /**
     * 查询秒杀商品列表(关联商品信息)
     */
    @Select("SELECT sg.*, g.goods_name, g.goods_price, g.goods_img " +
            "FROM t_seckill_goods sg " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "ORDER BY sg.start_time DESC")
    List<SeckillGoods> selectAllWithGoods();

    /**
     * 分页查询秒杀商品(关联商品信息) - 管理端
     */
    @Select("SELECT sg.*, g.goods_name, g.goods_price, g.goods_img " +
            "FROM t_seckill_goods sg " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "ORDER BY sg.create_time DESC")
    IPage<SeckillGoods> selectPageWithGoods(Page<SeckillGoods> page);

    /**
     * 查询单个秒杀商品(关联商品信息)
     */
    @Select("SELECT sg.*, g.goods_name, g.goods_price, g.goods_img, g.goods_detail " +
            "FROM t_seckill_goods sg " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "WHERE sg.id = #{id}")
    SeckillGoods selectByIdWithGoods(@Param("id") Long id);

    /**
     * 扣减库存 (乐观锁: WHERE available_stock > 0)
     * 业务规则：一人一单（Redis bought Set 保证），取消订单后可重新购买
     */
    @Update("UPDATE t_seckill_goods SET available_stock = available_stock - 1 " +
            "WHERE id = #{id} AND status = 1 AND available_stock > 0")
    int deductStock(@Param("id") Long id);

    /**
     * 回滚库存 (+1)
     * 业务规则：一人一单，取消订单后可重新购买（需要回滚 Redis bought Set）
     * 边界保护：防止库存超过总库存，避免极端并发场景下的超量回滚
     */
    @Update("UPDATE t_seckill_goods SET available_stock = available_stock + 1 " +
            "WHERE id = #{id} AND available_stock < total_stock")
    int rollbackStock(@Param("id") Long id);

    /**
     * 按状态查询
     */
    @Select("SELECT * FROM t_seckill_goods WHERE status = #{status}")
    List<SeckillGoods> selectByStatus(@Param("status") Integer status);
}
