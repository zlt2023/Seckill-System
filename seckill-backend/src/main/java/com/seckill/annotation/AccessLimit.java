package com.seckill.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLimit {

    /**
     * 限流时间窗口(秒)
     */
    int seconds() default 5;

    /**
     * 窗口内最大请求次数
     */
    int maxCount() default 5;

    /**
     * 是否需要登录
     */
    boolean needLogin() default true;
}
