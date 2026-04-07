package com.seckill.utils;

import com.seckill.entity.User;

/**
 * 用户上下文 (基于 ThreadLocal)
 * 在 JWT 拦截器中设置，Controller 中获取当前登录用户
 */
public class UserContext {

    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    public static void set(User user) {
        CURRENT_USER.set(user);
    }

    public static User get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        User user = get();
        return user != null ? user.getId() : null;
    }

    public static boolean isAdmin() {
        User user = get();
        return user != null && user.isAdmin();
    }

    public static void remove() {
        CURRENT_USER.remove();
    }
}
