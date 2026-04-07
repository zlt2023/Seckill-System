-- ================================================================
-- 秒杀系统数据库建表脚本
-- ================================================================

CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill;

-- ========================================
-- 1. 用户表
-- ========================================
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名(登录账号)',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    nickname    VARCHAR(50)  DEFAULT '' COMMENT '昵称',
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色: 0-普通用户, 1-管理员',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入默认管理员 (密码: admin123, BCrypt加密)
INSERT INTO t_user (username, password, nickname, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKtBGGfR8S3pMYBsYmI1FhQxYVu', '管理员', 1);

-- ========================================
-- 2. 商品表
-- ========================================
DROP TABLE IF EXISTS t_goods;
CREATE TABLE t_goods (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
    goods_name  VARCHAR(100)  NOT NULL COMMENT '商品名称',
    goods_price DECIMAL(10,2) NOT NULL COMMENT '商品原价',
    goods_img   VARCHAR(255)  DEFAULT '' COMMENT '商品图片URL',
    goods_detail TEXT          COMMENT '商品详情(富文本)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 插入测试商品
INSERT INTO t_goods (goods_name, goods_price, goods_img, goods_detail) VALUES
('iPhone 16 Pro', 8999.00, '/img/iphone16.jpg', 'Apple iPhone 16 Pro 256GB 钛金属'),
('MacBook Air M3', 9499.00, '/img/macbook.jpg', 'Apple MacBook Air 15寸 M3芯片 16GB+512GB'),
('Sony PS5 Slim', 3499.00, '/img/ps5.jpg', 'Sony PlayStation 5 Slim 国行光驱版'),
('AirPods Pro 2', 1799.00, '/img/airpods.jpg', 'Apple AirPods Pro 第二代 USB-C'),
('Nintendo Switch 2', 2599.00, '/img/switch2.jpg', 'Nintendo Switch 2 标准版');

-- ========================================
-- 3. 秒杀商品表
-- ========================================
DROP TABLE IF EXISTS t_seckill_goods;
CREATE TABLE t_seckill_goods (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '秒杀商品ID',
    goods_id        BIGINT        NOT NULL COMMENT '关联商品ID',
    seckill_price   DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    total_stock     INT           NOT NULL COMMENT '秒杀总库存(初始值,不变)',
    available_stock INT           NOT NULL COMMENT '剩余可用库存',
    start_time      DATETIME      NOT NULL COMMENT '秒杀开始时间',
    end_time        DATETIME      NOT NULL COMMENT '秒杀结束时间',
    status          INT           DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_goods_id (goods_id),
    KEY idx_status_start (status, start_time),
    KEY idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- 插入测试秒杀活动 (时间请根据实际调整)
INSERT INTO t_seckill_goods (goods_id, seckill_price, total_stock, available_stock, start_time, end_time, status) VALUES
(1, 6999.00, 10, 10, '2026-04-02 10:00:00', '2026-04-02 12:00:00', 0),
(2, 7499.00, 5,  5,  '2026-04-02 10:00:00', '2026-04-02 12:00:00', 0),
(3, 2499.00, 20, 20, '2026-04-02 14:00:00', '2026-04-02 16:00:00', 0);

-- ========================================
-- 4. 秒杀订单表
-- ========================================
DROP TABLE IF EXISTS t_seckill_order;
CREATE TABLE t_seckill_order (
    id               BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单编号(雪花算法)',
    user_id          BIGINT        NOT NULL COMMENT '用户ID',
    seckill_goods_id BIGINT        NOT NULL COMMENT '秒杀商品ID',
    seckill_price    DECIMAL(10,2) NOT NULL COMMENT '成交价格(快照)',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付, 1-已支付, 2-已取消, 3-已超时',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

