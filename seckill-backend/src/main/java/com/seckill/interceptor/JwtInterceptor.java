package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.entity.User;
import com.seckill.mapper.UserMapper;
import com.seckill.utils.JwtUtils;
import com.seckill.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 验证 Token 有效性，设置当前用户到 UserContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取 Token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            writeError(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        // 解析 Token
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null) {
            writeError(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        // 获取用户信息
        Long userId = claims.get("userId", Long.class);
        User user = userMapper.selectById(userId);
        if (user == null) {
            writeError(response, ResultCode.USER_NOT_FOUND);
            return false;
        }

        // 设置当前用户到 ThreadLocal
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除 ThreadLocal 防止内存泄漏
        UserContext.remove();
    }

    /**
     * 从 Header 提取 Token
     * 支持格式: "Bearer xxx" 或 直接 "xxx"
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header)) {
            if (header.startsWith("Bearer ")) {
                return header.substring(7);
            }
            return header;
        }
        // 备用: 从参数中获取
        return request.getParameter("token");
    }

    /**
     * 写入错误响应
     */
    private void writeError(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode)));
    }
}
