package com.seckill.controller.admin;

import com.seckill.common.PageResult;
import com.seckill.common.Result;
import com.seckill.entity.User;
import com.seckill.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 用户管理
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /**
     * 用户列表(分页)
     */
    @GetMapping
    public Result<PageResult<User>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(PageResult.of(userService.pageUsers(current, size)));
    }
}
