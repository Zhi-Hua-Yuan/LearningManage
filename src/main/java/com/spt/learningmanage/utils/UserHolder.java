package com.spt.learningmanage.utils;

/**
 * 用户信息线程存储工具类。
 */
public class UserHolder {
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的用户ID
     */
    public static void set(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前线程的用户ID
     */
    public static Long get() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前线程的用户ID
     * <p>注意：在拦截器处理完请求后必须调用此方法，防止内存泄漏！</p>
     */
    public static void remove() {
        USER_ID_HOLDER.remove();
    }
}

