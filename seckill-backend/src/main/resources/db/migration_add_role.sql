-- ============================================================
-- 用户角色升级迁移脚本
-- 为 t_user 表添加 role 字段并设置初始管理员
-- ============================================================

-- 1. 添加 role 列 (0=客户, 1=管理员)
ALTER TABLE `t_user` ADD COLUMN `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色: 0-客户 1-管理员' AFTER `avatar`;

-- 2. 将已有 testuser 设为普通客户 (默认已是 0)
-- UPDATE `t_user` SET `role` = 0 WHERE `username` = 'testuser';

-- 3. 插入管理员账号 (如果不存在)
-- 密码: 123456 (经过两次 MD5, salt=abcd1234)
INSERT IGNORE INTO `t_user` (`username`, `password`, `salt`, `phone`, `nickname`, `role`, `status`)
VALUES ('admin', 'e10f5da66f304f0b4e33c6aa4dfaa011', 'abcd1234', '13800138000', '系统管理员', 1, 1);

-- 4. 如果 admin 账号已存在,确保 role=1
UPDATE `t_user` SET `role` = 1 WHERE `phone` = '13800138000';

SELECT id, username, phone, role,
       CASE role WHEN 0 THEN '客户' WHEN 1 THEN '管理员' END AS role_name
FROM `t_user` WHERE `deleted` = 0;
