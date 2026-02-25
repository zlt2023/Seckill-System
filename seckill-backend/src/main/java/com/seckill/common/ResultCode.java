package com.seckill.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ==================== 通用 ====================
    SUCCESS(200, "操作成功"),
    ERROR(500, "服务器内部错误"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),

    // ==================== 用户模块 1xxx ====================
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ALREADY_EXISTS(1003, "用户已存在"),
    USER_PHONE_INVALID(1004, "手机号格式不正确"),
    USER_LOGIN_EXPIRED(1005, "登录已过期，请重新登录"),

    // ==================== 商品模块 2xxx ====================
    GOODS_NOT_FOUND(2001, "商品不存在"),
    GOODS_STOCK_EMPTY(2002, "商品库存不足"),

    // ==================== 秒杀模块 3xxx ====================
    SECKILL_NOT_START(3001, "秒杀活动尚未开始"),
    SECKILL_ENDED(3002, "秒杀活动已结束"),
    SECKILL_REPEAT(3003, "不能重复秒杀"),
    SECKILL_STOCK_EMPTY(3004, "秒杀商品已售罄"),
    SECKILL_LIMIT(3005, "访问过于频繁，请稍后再试"),
    SECKILL_PATH_INVALID(3006, "秒杀路径不合法"),
    SECKILL_CAPTCHA_ERROR(3007, "验证码错误"),
    SECKILL_QUEUING(3008, "正在排队中，请稍候"),

    // ==================== 订单模块 4xxx ====================
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_ALREADY_PAID(4002, "订单已支付"),
    ORDER_CANCELLED(4003, "订单已取消"),
    ORDER_PAY_TIMEOUT(4004, "订单支付超时");

    private final int code;
    private final String message;
}
