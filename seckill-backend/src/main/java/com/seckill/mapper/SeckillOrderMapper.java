package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 查询用户订单列表(关联商品信息)
     */
    @Select("SELECT o.*, g.goods_name, g.goods_img " +
            "FROM t_seckill_order o " +
            "LEFT JOIN t_seckill_goods sg ON o.seckill_goods_id = sg.id " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "WHERE o.user_id = #{userId} " +
            "ORDER BY o.create_time DESC")
    List<SeckillOrder> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询订单详情(关联商品信息)
     */
    @Select("SELECT o.*, g.goods_name, g.goods_img " +
            "FROM t_seckill_order o " +
            "LEFT JOIN t_seckill_goods sg ON o.seckill_goods_id = sg.id " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "WHERE o.order_no = #{orderNo}")
    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 管理端分页查询全部订单(关联用户+商品)
     */
    @Select("SELECT o.*, g.goods_name, g.goods_img, u.username " +
            "FROM t_seckill_order o " +
            "LEFT JOIN t_seckill_goods sg ON o.seckill_goods_id = sg.id " +
            "LEFT JOIN t_goods g ON sg.goods_id = g.id " +
            "LEFT JOIN t_user u ON o.user_id = u.id " +
            "ORDER BY o.create_time DESC")
    IPage<SeckillOrder> selectPageAll(Page<SeckillOrder> page);

    /**
     * 乐观锁/状态机原子更新订单状态
     */
    @org.apache.ibatis.annotations.Update("UPDATE t_seckill_order SET status = #{newStatus}, pay_time = #{payTime} " +
            "WHERE order_no = #{orderNo} AND status = #{oldStatus}")
    int updateStatusWithPayTime(@Param("orderNo") String orderNo, 
                                @Param("oldStatus") int oldStatus, 
                                @Param("newStatus") int newStatus, 
                                @Param("payTime") java.time.LocalDateTime payTime);

    @org.apache.ibatis.annotations.Update("UPDATE t_seckill_order SET status = #{newStatus} " +
            "WHERE order_no = #{orderNo} AND status = #{oldStatus}")
    int updateStatus(@Param("orderNo") String orderNo, 
                     @Param("oldStatus") int oldStatus, 
                     @Param("newStatus") int newStatus);
}
