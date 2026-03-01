package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

        /**
         * 扣减库存 (乐观锁: stock_count > 0)
         */
        @Update("UPDATE t_seckill_goods SET stock_count = stock_count - 1 WHERE id = #{id} AND stock_count > 0")
        int reduceStock(@Param("id") Long id);

        /**
         * 恢复库存（订单取消时调用）
         */
        @Update("UPDATE t_seckill_goods SET stock_count = stock_count + 1 WHERE id = #{id}")
        int restoreStock(@Param("id") Long id);

        /**
         * 获取已超过 end_date 且状态为"进行中(1)"的秒杀活动ID
         */
        @org.apache.ibatis.annotations.Select("SELECT id FROM t_seckill_goods WHERE seckill_status = 1 AND end_date < NOW() AND deleted = 0")
        java.util.List<Long> getExpiredIdsToEnded();

        /**
         * 将已超过 end_date 且状态为"进行中(1)"的秒杀活动更新为"已结束(2)"
         */
        @Update("UPDATE t_seckill_goods SET seckill_status = 2 " +
                        "WHERE seckill_status = 1 AND end_date < NOW() AND deleted = 0")
        int updateExpiredToEnded();

        /**
         * 将已到 start_date、end_date 未过，但状态误标为 2 的活动恢复为进行中(1)
         * （边界补偿：理论上不应发生，作为保险）
         */
        @Update("UPDATE t_seckill_goods SET seckill_status = 1 " +
                        "WHERE seckill_status = 2 AND start_date <= NOW() AND end_date >= NOW() AND deleted = 0")
        int updateReactivatedToOngoing();

        /**
         * 将状态未发布(0)但已到达 start_date 的商品，自动更改为进行中(1)
         */
        @Update("UPDATE t_seckill_goods SET seckill_status = 1 " +
                        "WHERE seckill_status = 0 AND start_date <= NOW() AND end_date >= NOW() AND deleted = 0")
        int updatePublishedToOngoing();
}
