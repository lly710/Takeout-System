package com.example.takeout.common.exception;

/**
 * 业务异常，适合表达参数错误、权限不足、资源不存在等可预期问题。
 */
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(String message) {
        this(400, message);
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
