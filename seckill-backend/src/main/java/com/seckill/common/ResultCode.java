package com.seckill.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ===== 通用 =====
    SUCCESS(200, "操作成功"),
    FAIL(400, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    // ===== 用户模块 =====
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXIST(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    PHONE_EXIST(1004, "手机号已被注册"),

    // ===== 秒杀模块 =====
    SECKILL_NOT_START(2001, "秒杀活动未开始"),
    SECKILL_ENDED(2002, "秒杀活动已结束"),
    SECKILL_SOLD_OUT(2003, "商品已售罄"),
    SECKILL_REPEAT(2004, "请勿重复购买"),
    SECKILL_PATH_INVALID(2005, "秒杀路径无效"),
    SECKILL_QUEUING(2006, "排队中，请等待结果"),
    SECKILL_LIMIT(2007, "访问过于频繁，请稍后再试"),

    // ===== 订单模块 =====
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),
    ORDER_ALREADY_PAID(3003, "订单已支付"),
    ORDER_ALREADY_CANCELLED(3004, "订单已取消"),

    // ===== 商品模块 =====
    GOODS_NOT_FOUND(4001, "商品不存在"),
    SECKILL_GOODS_NOT_FOUND(4002, "秒杀商品不存在"),
    SECKILL_OFF_SHELF(4003, "秒杀活动已下架");

    private final int code;
    private final String message;
}
