package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.LoginDTO;
import com.seckill.dto.LoginVO;
import com.seckill.dto.RegisterDTO;
import com.seckill.entity.User;
import com.seckill.service.UserService;
import com.seckill.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 登录/注册/获取用户信息
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        LoginVO vo = userService.register(dto);
        return Result.success(vo);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = userService.login(dto);
        return Result.success(vo);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        User user = UserContext.get();
        user.setPassword(null); // 不返回密码
        return Result.success(user);
    }
}
