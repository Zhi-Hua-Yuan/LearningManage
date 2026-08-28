package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * 平台级系统角色。
 *
 * <p>该角色来自 {@code user.user_role}，与团队角色及尚未启用的租户 RBAC
 * 相互独立。V2 已完成历史值规范化，因此运行时只接受精确的规范值。</p>
 */
@Getter
public enum SystemRoleEnum {

    USER("USER", "普通用户"),
    SYSTEM_ADMIN("SYSTEM_ADMIN", "系统管理员");

    private final String value;

    private final String text;

    SystemRoleEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 使用区分大小写的规范值解析系统角色。
     *
     * @param value 数据库存储的系统角色值
     * @return 对应角色；值为空、旧版小写值或未知值时返回 {@code null}
     */
    public static SystemRoleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SystemRoleEnum systemRole : SystemRoleEnum.values()) {
            if (systemRole.value.equals(value)) {
                return systemRole;
            }
        }
        return null;
    }

    /**
     * 判断是否为 V2 允许的规范系统角色值。
     */
    public static boolean isValidValue(String value) {
        return fromValue(value) != null;
    }

    /**
     * 判断是否为规范的系统管理员角色。
     */
    public static boolean isSystemAdmin(String value) {
        return SYSTEM_ADMIN == fromValue(value);
    }
}
