CREATE TABLE IF NOT EXISTS `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account`     VARCHAR(256) NOT NULL COMMENT '账号',
    `username`    VARCHAR(256) NOT NULL COMMENT '用户名',
    `password`    VARCHAR(512) NOT NULL COMMENT '密码（加密存储）',
    `user_role`   VARCHAR(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_account (`account`) -- 保证账号不重复
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';