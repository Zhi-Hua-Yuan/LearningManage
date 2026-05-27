CREATE TABLE team_member
(
    id          BIGINT      NOT NULL COMMENT '团队成员关系ID',
    team_id     BIGINT      NOT NULL COMMENT '团队ID',
    user_id     BIGINT      NOT NULL COMMENT '成员用户ID',
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：OWNER/ADMIN/MEMBER',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at  DATETIME             DEFAULT NULL COMMENT '删除时间',
    is_delete   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_user (team_id, user_id),
    KEY idx_user_id (user_id),
    KEY idx_team_id (team_id),
    KEY idx_team_role (team_id, role),
    KEY idx_is_delete (is_delete)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队成员关系表';