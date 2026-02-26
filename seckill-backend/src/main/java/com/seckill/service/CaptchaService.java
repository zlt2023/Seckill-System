package com.seckill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 验证码服务 - 从 CaptchaController 中提取（P1-1 修复：Service 不应依赖 Controller）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CAPTCHA_KEY = "captcha:seckill:";

    /**
     * 验证验证码（用后即删，防止重放）
     *
     * @param userId         用户ID
     * @param seckillGoodsId 秒杀商品ID
     * @param userAnswer     用户输入的验证码答案
     * @return 验证码是否正确
     */
    public boolean verifyCaptcha(Long userId, Long seckillGoodsId, int userAnswer) {
        String key = CAPTCHA_KEY + userId + ":" + seckillGoodsId;
        Object stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return false;
        }
        redisTemplate.delete(key); // 用后即删
        return Integer.parseInt(stored.toString()) == userAnswer;
    }
}
