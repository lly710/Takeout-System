package com.example.takeout.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 简单密码工具，当前项目用 SHA-256 做演示级密码摘要和比对。
 */
public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static String hash(String raw) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash password", exception);
        }
    }

    public static boolean matches(String raw, String hashed) {
        return hash(raw).equalsIgnoreCase(hashed);
    }
}
