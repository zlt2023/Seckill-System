package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.common.ResultCode;
import com.seckill.dto.LoginDTO;
import com.seckill.dto.RegisterDTO;
import com.seckill.entity.User;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.UserMapper;
import com.seckill.utils.JwtUtils;
import com.seckill.utils.Md5Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_TOKEN_KEY = "seckill:user:token:";

    /**
     * 用户注册
     */
    public void register(RegisterDTO dto) {
        // 检查手机号是否已注册
        long count = count(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (count > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 生成随机盐值
        String salt = Md5Utils.generateSalt();
        // 第二次MD5加密
        String dbPassword = Md5Utils.midToDb(dto.getPassword(), salt);

        // 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setPassword(dbPassword);
        user.setSalt(salt);
        user.setNickname(dto.getUsername());
        user.setRole(0); // 默认注册为客户
        user.setStatus(1);
        user.setDeleted(0);

        save(user);
        log.info("用户注册成功: phone={}", dto.getPhone());
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(LoginDTO dto) {
        // 查询用户
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));

        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 验证密码
        String dbPassword = Md5Utils.midToDb(dto.getPassword(), user.getSalt());
        if (!dbPassword.equals(user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 生成Token（包含角色信息）
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 存入Redis (支持踢人下线 - 同一用户只保留最后一个Token)
        redisTemplate.opsForValue().set(
                REDIS_TOKEN_KEY + user.getId(),
                token,
                24, TimeUnit.HOURS);

        // 返回用户信息和Token
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("phone", user.getPhone());
        result.put("role", user.getRole() != null ? user.getRole() : 0);

        log.info("用户登录成功: userId={}, phone={}, role={}", user.getId(), dto.getPhone(), user.getRole());
        return result;
    }

    /**
     * 退出登录
     */
    public void logout(Long userId) {
        redisTemplate.delete(REDIS_TOKEN_KEY + userId);
        log.info("用户退出登录: userId={}", userId);
    }
}
