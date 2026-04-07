package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名(登录账号) */
    private String username;

    /** BCrypt加密密码 */
    private String password;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 角色: 0-普通用户, 1-管理员 */
    private Integer role;

    /** 注册时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 角色常量 =====
    public static final int ROLE_USER = 0;
    public static final int ROLE_ADMIN = 1;

    public boolean isAdmin() {
        return ROLE_ADMIN == this.role;
    }
}
