package com.seckill.controller;

import com.seckill.annotation.RateLimit;
import com.seckill.common.Result;
import com.seckill.utils.UserContext;
import com.seckill.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器 - 数学验证码，分散秒杀请求
 */
@Slf4j
@Tag(name = "验证码模块")
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CaptchaService captchaService;

    private static final String CAPTCHA_KEY = "captcha:seckill:";
    private static final int WIDTH = 160;
    private static final int HEIGHT = 50;
    private static final Random RANDOM = new Random();

    @Operation(summary = "获取秒杀验证码")
    @GetMapping("/seckill/{seckillGoodsId}")
    @RateLimit(seconds = 5, maxCount = 3)
    public Result<Map<String, String>> getCaptcha(@PathVariable Long seckillGoodsId) {
        Long userId = UserContext.getCurrentUserId();

        // 生成数学表达式
        int num1 = RANDOM.nextInt(9) + 1;
        int num2 = RANDOM.nextInt(9) + 1;
        int op = RANDOM.nextInt(3); // 0: + 1: - 2: ×

        String expression;
        int answer;
        switch (op) {
            case 0:
                expression = num1 + " + " + num2;
                answer = num1 + num2;
                break;
            case 1:
                if (num1 < num2) {
                    int temp = num1;
                    num1 = num2;
                    num2 = temp;
                }
                expression = num1 + " - " + num2;
                answer = num1 - num2;
                break;
            default:
                expression = num1 + " × " + num2;
                answer = num1 * num2;
                break;
        }

        expression += " = ?";

        // 存入Redis (2分钟有效)
        String key = CAPTCHA_KEY + userId + ":" + seckillGoodsId;
        redisTemplate.opsForValue().set(key, answer, 2, TimeUnit.MINUTES);

        // 生成验证码图片
        String base64Img = generateCaptchaImage(expression);

        Map<String, String> result = new HashMap<>();
        result.put("captchaImage", "data:image/png;base64," + base64Img);

        return Result.success(result);
    }

    /**
     * 验证验证码（委托给 CaptchaService，保持向后兼容）
     */
    public boolean verifyCaptcha(Long userId, Long seckillGoodsId, int userAnswer) {
        return captchaService.verifyCaptcha(userId, seckillGoodsId, userAnswer);
    }

    /**
     * 生成验证码图片（Base64）
     */
    private String generateCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景 - 深色主题匹配
        g2d.setColor(new Color(30, 30, 46));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g2d.setColor(new Color(60 + RANDOM.nextInt(40), 60 + RANDOM.nextInt(40), 80 + RANDOM.nextInt(40)));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }

        // 干扰点
        for (int i = 0; i < 50; i++) {
            g2d.setColor(new Color(80 + RANDOM.nextInt(60), 80 + RANDOM.nextInt(60), 100 + RANDOM.nextInt(60)));
            g2d.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 2, 2);
        }

        // 绘制文字
        g2d.setFont(new Font("Arial", Font.BOLD, 26));
        g2d.setColor(new Color(139, 92, 246)); // accent purple
        // 居中绘制
        FontMetrics fm = g2d.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(text)) / 2;
        int y = (HEIGHT - fm.getHeight()) / 2 + fm.getAscent();

        // 轻微旋转
        g2d.rotate(Math.toRadians(RANDOM.nextInt(5) - 2), (double) WIDTH / 2, (double) HEIGHT / 2);
        g2d.drawString(text, x, y);

        g2d.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("生成验证码图片失败", e);
            return "";
        }
    }
}
