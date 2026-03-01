-- =============================================
-- 秒杀系统数据库初始化脚本
-- 服务器: 192.168.150.103:3306
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `seckill` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `seckill`;

-- =============================================
-- 1. 用户表
-- =============================================
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码(两次MD5)',
    `salt`        VARCHAR(16)  NOT NULL COMMENT '盐值',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色: 0-客户 1-管理员',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================
-- 2. 秒杀商品表
-- =============================================
DROP TABLE IF EXISTS `t_seckill_goods`;
CREATE TABLE `t_seckill_goods` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '秒杀商品ID',
    `goods_name`   VARCHAR(100)  NOT NULL COMMENT '商品名称',
    `goods_title`  VARCHAR(200)  DEFAULT NULL COMMENT '商品标题',
    `goods_img`    VARCHAR(500)  DEFAULT NULL COMMENT '商品图片',
    `goods_detail` TEXT          DEFAULT NULL COMMENT '商品详情',
    `goods_price`  DECIMAL(10,2) NOT NULL COMMENT '商品原价',

    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `stock_count`   INT           NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    `start_date`    DATETIME      NOT NULL COMMENT '秒杀开始时间',
    `end_date`      DATETIME      NOT NULL COMMENT '秒杀结束时间',
    `goods_status`  TINYINT       NOT NULL DEFAULT 1 COMMENT '秒杀商品状态: 0-下架 1-上架',
    `seckill_status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '秒杀活动状态: 0-未发布 1-进行中 2-已结束',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀商品表';

-- =============================================
-- 4. 订单表
-- =============================================
DROP TABLE IF EXISTS `t_order_info`;
CREATE TABLE `t_order_info` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `user_id`          BIGINT        NOT NULL COMMENT '用户ID',
    `goods_id`         BIGINT        NOT NULL COMMENT '商品ID',
    `delivery_addr_id` BIGINT        DEFAULT NULL COMMENT '收货地址ID',
    `goods_name`       VARCHAR(100)  DEFAULT NULL COMMENT '商品名称',
    `goods_count`      INT           NOT NULL DEFAULT 1 COMMENT '购买数量',
    `goods_price`      DECIMAL(10,2) NOT NULL COMMENT '商品价格',
    `status`           TINYINT       NOT NULL DEFAULT 0 COMMENT '订单状态: 0-未支付 1-已支付 2-已发货 3-已收货 4-已取消 5-已退款',
    `pay_time`         DATETIME      DEFAULT NULL COMMENT '支付时间',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- =============================================
-- 5. 秒杀订单表 (用于快速判断重复秒杀)
-- =============================================
DROP TABLE IF EXISTS `t_seckill_order`;
CREATE TABLE `t_seckill_order` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '秒杀订单ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `order_id`    BIGINT   NOT NULL COMMENT '关联订单ID',
    `goods_id`    BIGINT   NOT NULL COMMENT '关联商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`) COMMENT '用户+商品唯一索引，防止重复秒杀'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀订单表';

-- =============================================
-- 初始化测试数据
-- =============================================

-- 插入测试秒杀商品
INSERT INTO `t_seckill_goods` (`goods_name`, `goods_title`, `goods_img`, `goods_detail`, `goods_price`, `seckill_price`, `stock_count`, `start_date`, `end_date`, `goods_status`, `seckill_status`) VALUES
('iPhone 16 Pro Max', 'Apple iPhone 16 Pro Max 256GB 沙漠钛金属', 'https://picsum.photos/id/1/400/400', '全新 A18 Pro芯片，超强性能。4800万像素四合一主摄，支持5倍光学变焦。钛金属设计，超瓷晶面板。', 9999.00, 6999.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1),
('MacBook Pro M4', 'Apple MacBook Pro 14英寸 M4芯片 16GB+512GB', 'https://picsum.photos/id/2/400/400', 'M4芯片带来突破性性能，Liquid Retina XDR显示屏，长达18小时电池续航。', 12999.00, 8999.00, 50,  NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1),
('AirPods Pro 3', 'Apple AirPods Pro 第三代 主动降噪', 'https://picsum.photos/id/3/400/400', '全新H3芯片，自适应降噪，个性化空间音频，USB-C充电盒，长达30小时续航。', 1999.00, 999.00,  200, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1),
('iPad Air M3', 'Apple iPad Air 11英寸 M3芯片 256GB', 'https://picsum.photos/id/4/400/400', 'M3芯片驱动，11英寸Liquid Retina显示屏，支持Apple Pencil Pro。', 5499.00, 3999.00, 80,  NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1),
('Sony PS5 Pro', 'Sony PlayStation 5 Pro 游戏主机', 'https://picsum.photos/id/5/400/400', '全新GPU架构，支持8K游戏，光线追踪，2TB SSD存储，向下兼容PS4/PS5游戏。', 4999.00, 3499.00, 30,  DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), 1, 0),
('华为 Mate 70 Pro+', '华为 Mate 70 Pro+ 麒麟芯片 512GB', 'https://picsum.photos/id/6/400/400', '麒麟旗舰芯片，超感知影像系统，卫星通信，鸿蒙操作系统，超长续航。', 8999.00, 5999.00, 60,  DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), 1, 0);

-- 插入测试用户 (密码: 123456, 经过两次MD5)
-- 注意: 实际使用时请通过注册接口创建用户
INSERT INTO `t_user` (`username`, `password`, `salt`, `phone`, `nickname`, `role`, `status`) VALUES
('admin',    'e10f5da66f304f0b4e33c6aa4dfaa011', 'abcd1234', '13800138000', '系统管理员', 1, 1),
('testuser', 'e10f5da66f304f0b4e33c6aa4dfaa011', 'abcd1234', '13800138001', '测试客户',   0, 1);
