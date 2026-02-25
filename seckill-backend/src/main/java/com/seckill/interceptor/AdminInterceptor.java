package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.annotation.AdminOnly;
import com.seckill.common.Result;
import com.seckill.common.ResultCode;
import com.seckill.entity.User;
import com.seckill.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 管理员权限拦截器
 * 校验被 @AdminOnly 注解标注的方法或类，仅允许 role=1 的用户访问
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler)
            throws Exception {
        // 非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查方法或类上是否有 @AdminOnly 注解
        boolean hasAnnotation = handlerMethod.hasMethodAnnotation(AdminOnly.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);

        if (!hasAnnotation) {
            return true; // 无注解，放行
        }

        // 校验当前用户角色
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null || currentUser.getRole() == null || currentUser.getRole() != 1) {
            log.warn("非管理员尝试访问管理接口: userId={}, uri={}",
                    currentUser != null ? currentUser.getId() : "null",
                    request.getRequestURI());
            writeError(response);
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(
                Result.error(ResultCode.FORBIDDEN.getCode(), "无权操作：仅限管理员")));
        writer.flush();
        writer.close();
    }
}
