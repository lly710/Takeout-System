package com.example.takeout.common.auth;

/**
 * 当前登录用户的最小信息载体，包含用户 ID、角色和展示名称。
 */
public record CurrentUser(Long userId, UserRole role, String displayName) {
}
