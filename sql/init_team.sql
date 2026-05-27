CREATE TABLE team
(
    id          BIGINT      NOT NULL COMMENT '团队ID',
    name        VARCHAR(60) NOT NULL COMMENT '团队名称',
    description VARCHAR(200)         DEFAULT NULL COMMENT '团队描述',
    owner_id    BIGINT      NOT NULL COMMENT '团队创建者用户ID',
    invite_code VARCHAR(60) NOT NULL COMMENT '团队邀请码',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at  DATETIME             DEFAULT NULL COMMENT '删除时间',
    is_delete   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_code (invite_code),
    KEY idx_owner_id (owner_id),
    KEY idx_is_delete (is_delete)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队表';