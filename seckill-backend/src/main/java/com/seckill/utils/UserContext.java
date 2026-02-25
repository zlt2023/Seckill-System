package com.seckill.utils;

import com.seckill.entity.User;

/**
 * 用户上下文工具类 - 基于 ThreadLocal 存储当前登录用户
 */
public class UserContext {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户
     */
    public static void setCurrentUser(User user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前用户
     */
    public static User getCurrentUser() {
        return USER_HOLDER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        User user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 清除当前用户
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
}
