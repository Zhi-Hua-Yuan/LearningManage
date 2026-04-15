package com.spt.learningmanage.constant;

/**
 * 删除来源标记。
 */
public final class DeleteSourceConstant {

    private DeleteSourceConstant() {
    }

    /** 未删除/正常状态 */
    public static final int NORMAL = 0;

    /** 用户手动删除 */
    public static final int MANUAL = 1;

    /** 项目级联删除 */
    public static final int PROJECT_CASCADE = 2;
}

