package com.seckill.utils;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 密码加密工具类 - 两次MD5加密
 * 第一次(前端): 用户输入密码 + 固定salt → MD5
 * 第二次(后端): 前端传来的MD5 + 随机salt → MD5 → 存入数据库
 */
public class Md5Utils {

    /** 前端固定盐值 */
    public static final String FRONT_SALT = "s3ck1ll";

    /**
     * 模拟前端第一次MD5（实际由前端完成）
     * 用于测试
     */
    public static String inputToMid(String input) {
        String str = FRONT_SALT.charAt(0) + input + FRONT_SALT.charAt(5);
        return DigestUtil.md5Hex(str);
    }

    /**
     * 后端第二次MD5
     * 
     * @param midPass 前端传来的第一次MD5结果
     * @param salt    数据库中的随机盐值
     * @return 最终密码
     */
    public static String midToDb(String midPass, String salt) {
        String str = salt.charAt(0) + midPass + salt.charAt(5);
        return DigestUtil.md5Hex(str);
    }

    /**
     * 从输入密码直接到数据库密码 (用于注册)
     */
    public static String inputToDb(String input, String salt) {
        String midPass = inputToMid(input);
        return midToDb(midPass, salt);
    }

    /**
     * 生成随机盐值
     */
    public static String generateSalt() {
        return cn.hutool.core.util.RandomUtil.randomString(8);
    }
}
