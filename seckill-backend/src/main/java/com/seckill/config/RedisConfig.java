package com.seckill.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // JSON序列化配置
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key使用String序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value使用JSON序列化
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis Lua 脚本 - 秒杀原子操作（P0-3 修复）
     * 将"重复秒杀判断 + 库存预减"合并为一个原子操作，
     * 避免两步分离导致的竞态条件（中间崩溃会造成少卖）
     *
     * KEYS[1]: 库存key (seckill:stock:{seckillGoodsId})
     * KEYS[2]: 订单标记key (seckill:order:{userId}:{goodsId})
     * ARGV[1]: 订单标记TTL (秒)
     * 返回: 1-成功 0-库存不足 -1-重复秒杀
     */
    @Bean
    public DefaultRedisScript<Long> seckillScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "-- 1. 检查是否重复秒杀\n" +
                        "if redis.call('exists', KEYS[2]) == 1 then\n" +
                        "    return -1\n" +
                        "end\n" +
                        "-- 2. 检查并扣减库存\n" +
                        "local stock = redis.call('get', KEYS[1])\n" +
                        "if stock and tonumber(stock) > 0 then\n" +
                        "    redis.call('decr', KEYS[1])\n" +
                        "    redis.call('setex', KEYS[2], ARGV[1], '1')\n" +
                        "    return 1\n" +
                        "end\n" +
                        "return 0");
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Redis Lua 脚本 - 原子限流计数器（P0-1 修复）
     * 将"计数器递增 + 设置过期时间"合并为原子操作，
     * 避免 increment 成功但 expire 失败导致 key 永不过期
     *
     * KEYS[1]: 限流key
     * ARGV[1]: 过期时间(秒)
     * 返回: 当前计数值
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local count = redis.call('incr', KEYS[1])\n" +
                        "if count == 1 then\n" +
                        "    redis.call('expire', KEYS[1], ARGV[1])\n" +
                        "end\n" +
                        "return count");
        script.setResultType(Long.class);
        return script;
    }
}
