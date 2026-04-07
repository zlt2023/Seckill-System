package com.seckill.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.dto.LoginDTO;
import com.seckill.dto.LoginVO;
import com.seckill.dto.RegisterDTO;
import com.seckill.entity.User;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.UserMapper;
import com.seckill.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final JwtUtils jwtUtils;

    /**
     * 用户注册
     */
    public LoginVO register(RegisterDTO dto) {
        // 检查用户名是否已存在
        long count = count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXIST);
        }

        // 检查手机号是否已存在
        if (StringUtils.hasText(dto.getPhone())) {
            long phoneCount = count(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, dto.getPhone()));
            if (phoneCount > 0) {
                throw new BusinessException(ResultCode.PHONE_EXIST);
            }
        }

        // 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setRole(User.ROLE_USER);
        save(user);

        log.info("用户注册成功: {}", user.getUsername());

        // 生成 Token 并返回
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    /**
     * 用户登录
     */
    public LoginVO login(LoginDTO dto) {
        // 查询用户
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 校验密码
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        log.info("用户登录成功: {}", user.getUsername());

        // 生成 Token 并返回
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    /**
     * 管理端 - 分页查询用户列表
     */
    public IPage<User> pageUsers(int current, int size) {
        Page<User> page = new Page<>(current, size);
        IPage<User> result = page(page, new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime));
        // 清除密码字段
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }
}
