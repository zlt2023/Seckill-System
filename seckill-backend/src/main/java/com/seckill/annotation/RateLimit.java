package com.seckill.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 基于 Redis 滑动窗口实现，限制用户在指定时间窗口内的请求次数
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 限流时间窗口（秒）
     */
    int seconds() default 5;

    /**
     * 时间窗口内最大请求次数
     */
    int maxCount() default 5;

    /**
     * 是否需要登录用户（基于userId限流）
     * false = 基于IP限流
     */
    boolean needLogin() default true;
}
