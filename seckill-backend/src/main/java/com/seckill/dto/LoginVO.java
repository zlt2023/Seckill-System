package com.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 VO
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色: 0-普通用户, 1-管理员 */
    private Integer role;
}
