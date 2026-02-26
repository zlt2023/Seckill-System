package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.annotation.RateLimit;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Collections;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 限流拦截器 - 基于Redis实现计数器限流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> rateLimitScript;

    private static final String RATE_LIMIT_KEY = "rate_limit:";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        // 构建限流key
        String key;
        if (rateLimit.needLogin()) {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return true; // 未登录的用户由JWT拦截器处理
            }
            key = RATE_LIMIT_KEY + request.getRequestURI() + ":" + userId;
        } else {
            String ip = getClientIp(request);
            key = RATE_LIMIT_KEY + request.getRequestURI() + ":" + ip;
        }

        // 【P0-1 修复】使用 Lua 脚本原子计数+设置过期
        Long count = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                (long) rateLimit.seconds());
        if (count == null) {
            return true;
        }

        if (count > rateLimit.maxCount()) {
            log.warn("接口限流触发: key={}, count={}, limit={}", key, count, rateLimit.maxCount());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Result.error(ResultCode.SECKILL_LIMIT)));
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
