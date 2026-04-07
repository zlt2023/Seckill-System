package com.seckill.config;

import com.seckill.interceptor.AdminInterceptor;
import com.seckill.interceptor.JwtInterceptor;
import com.seckill.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 (跨域 + 拦截器)
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

        private final JwtInterceptor jwtInterceptor;
        private final AdminInterceptor adminInterceptor;
        private final RateLimitInterceptor rateLimitInterceptor;

        /**
         * 跨域配置 - 允许前端 Vue 开发服务器访问
         */
        @Override
        public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                                .allowedOriginPatterns("*")
                                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                                .allowedHeaders("*")
                                .allowCredentials(true)
                                .maxAge(3600);
        }

        /**
         * 拦截器配置
         */
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
                // 0. JWT 认证拦截器 - 最先执行，解析用户身份到 UserContext
                // 必须在限流拦截器之前，否则限流拦截器无法获取 userId
                registry.addInterceptor(jwtInterceptor)
                                .addPathPatterns("/api/**")
                                .excludePathPatterns(
                                                // 认证接口 (登录/注册)
                                                "/api/auth/login",
                                                "/api/auth/register",
                                                // 秒杀商品查询 (无需登录可浏览)
                                                "/api/seckill/goods",
                                                "/api/seckill/goods/*",
                                                "/api/seckill/time")
                                .order(0);

                // 1. 限流拦截器 - 在 JWT 之后执行，此时 UserContext 已可用
                registry.addInterceptor(rateLimitInterceptor)
                                .addPathPatterns("/api/**")
                                .order(1);

                // 2. 管理员权限拦截器 - 拦截管理端接口
                registry.addInterceptor(adminInterceptor)
                                .addPathPatterns("/api/admin/**")
                                .order(2);
        }
}
