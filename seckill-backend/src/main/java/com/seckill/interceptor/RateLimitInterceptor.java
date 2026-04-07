package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.annotation.AccessLimit;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 接口限流拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> limitScript;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod hm = (HandlerMethod) handler;
        AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);
        if (accessLimit == null) {
            return true;
        }

        int seconds = accessLimit.seconds();
        int maxCount = accessLimit.maxCount();
        boolean needLogin = accessLimit.needLogin();

        String key = "limit:" + request.getRequestURI() + ":";
        if (needLogin) {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                writeError(response, ResultCode.UNAUTHORIZED);
                return false;
            }
            key += userId;
        } else {
            // 如果不需要登录，用 IP 作为限流维度
            key += request.getRemoteAddr();
        }

        // 使用 StringRedisTemplate 执行 Lua 脚本，保证参数是纯字符串
        Long result = stringRedisTemplate.execute(
                limitScript,
                Collections.singletonList(key),
                String.valueOf(seconds),
                String.valueOf(maxCount)
        );

        if (result == null || result == 0L) {
            log.warn("接口被限流: key={}", key);
            writeError(response, ResultCode.SECKILL_LIMIT);
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode)));
    }
}

