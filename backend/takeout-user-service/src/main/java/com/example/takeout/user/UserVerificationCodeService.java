package com.example.takeout.user;

import com.example.takeout.common.exception.ServiceException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 简易验证码服务，用于用户端登录、注册和找回密码演示。
 * 真实项目里可以替换为短信平台实现，这里先用内存存储便于本地联调。
 */
@Service
public class UserVerificationCodeService {

    private static final Duration EXPIRE_AFTER = Duration.ofMinutes(10);
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Map<String, Object> sendCode(String phone, String purpose) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedPurpose = normalizePurpose(purpose);
        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expireAt = LocalDateTime.now().plus(EXPIRE_AFTER);
        codes.put(key(normalizedPhone, normalizedPurpose), new CodeEntry(code, expireAt));
        return Map.of(
                "phone", normalizedPhone,
                "purpose", normalizedPurpose,
                "code", code,
                "expireAt", expireAt.toString()
        );
    }

    public void verifyCode(String phone, String purpose, String code) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedPurpose = normalizePurpose(purpose);
        String normalizedCode = normalizeCode(code);
        CodeEntry entry = codes.get(key(normalizedPhone, normalizedPurpose));
        if (entry == null) {
            throw new ServiceException(400, "验证码不存在，请重新获取");
        }
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            codes.remove(key(normalizedPhone, normalizedPurpose));
            throw new ServiceException(400, "验证码已过期，请重新获取");
        }
        if (!entry.code.equals(normalizedCode)) {
            throw new ServiceException(400, "验证码不正确");
        }
        codes.remove(key(normalizedPhone, normalizedPurpose));
    }

    private String key(String phone, String purpose) {
        return phone + ":" + purpose;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new ServiceException(400, "请输入手机号");
        }
        return phone.trim();
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toUpperCase();
        return normalized.isEmpty() ? "LOGIN" : normalized;
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new ServiceException(400, "请输入验证码");
        }
        return code.trim();
    }

    private record CodeEntry(String code, LocalDateTime expireAt) {
    }
}
