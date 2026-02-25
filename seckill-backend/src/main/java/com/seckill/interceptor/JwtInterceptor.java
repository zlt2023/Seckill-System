package com.seckill.interceptor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.entity.User;
import com.seckill.utils.JwtUtils;
import com.seckill.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * JWT 拦截器 - Token校验 + 用户信息注入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String REDIS_TOKEN_KEY = "seckill:user:token:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        // 检查Token是否存在
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)) {
            writeError(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());

        try {
            // 验证Token是否过期
            if (jwtUtils.isTokenExpired(token)) {
                writeError(response, ResultCode.USER_LOGIN_EXPIRED);
                return false;
            }

            // 获取用户信息
            Long userId = jwtUtils.getUserId(token);
            String username = jwtUtils.getUsername(token);
            Integer role = jwtUtils.getRole(token);

            // 验证Redis中的Token是否一致 (支持踢人下线)
            String redisToken = (String) redisTemplate.opsForValue().get(REDIS_TOKEN_KEY + userId);
            if (redisToken == null || !redisToken.equals(token)) {
                writeError(response, ResultCode.USER_LOGIN_EXPIRED);
                return false;
            }

            // 将用户信息放入ThreadLocal
            User user = new User();
            user.setId(userId);
            user.setUsername(username);
            user.setRole(role != null ? role : 0);
            UserContext.setCurrentUser(user);

            return true;
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            writeError(response, ResultCode.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        // 请求结束后清除ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    /**
     * 输出错误响应
     */
    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(Result.error(resultCode)));
        writer.flush();
        writer.close();
    }
}
