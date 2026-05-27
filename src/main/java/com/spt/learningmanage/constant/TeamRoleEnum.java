package com.spt.learningmanage.constant;

import lombok.Getter;

@Getter
public enum TeamRoleEnum {

    OWNER("OWNER", "团队拥有者"),
    ADMIN("ADMIN", "管理员"),
    MEMBER("MEMBER", "普通成员");

    private final String value;

    private final String text;

    TeamRoleEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     */
    public static TeamRoleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TeamRoleEnum teamRoleEnum : TeamRoleEnum.values()) {
            if (teamRoleEnum.value.equals(value)) {
                return teamRoleEnum;
            }
        }
        return null;
    }

    /**
     * 判断是否为合法角色
     */
    public static boolean isValidValue(String value) {
        return fromValue(value) != null;
    }

    /**
     * 是否为 OWNER
     */
    public static boolean isOwner(String value) {
        return OWNER.value.equals(value);
    }

    /**
     * 是否为 ADMIN
     */
    public static boolean isAdmin(String value) {
        return ADMIN.value.equals(value);
    }

    /**
     * 是否为 MEMBER
     */
    public static boolean isMember(String value) {
        return MEMBER.value.equals(value);
    }

    /**
     * 是否具备团队项目管理权限
     * OWNER / ADMIN 可以管理团队项目
     */
    public static boolean canManageProject(String value) {
        return OWNER.value.equals(value) || ADMIN.value.equals(value);
    }
}
