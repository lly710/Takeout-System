package com.example.takeout.common.auth;

/**
 * 当前请求的登录用户上下文，放在线程变量里方便各层直接读取。
 */
public final class AuthContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentUser currentUser) {
        HOLDER.set(currentUser);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
