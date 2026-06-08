package com.example.takeout.common.exception;

import com.example.takeout.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Global exception handler that converts backend exceptions into a consistent API response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<?>> handleServiceException(ServiceException exception,
                                                                 HttpServletRequest request) {
        printToConsole("业务异常", request, exception);
        log.warn("业务异常 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return buildResponse(exception.getCode(), firstNonBlank(exception.getMessage(), "请求处理失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleMessageNotReadable(HttpMessageNotReadableException exception,
                                                                   HttpServletRequest request) {
        printToConsole("请求体格式错误", request, exception);
        log.warn("请求体格式错误 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return buildResponse(400, "请求体格式不正确");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                       HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "请求参数不正确";
        }
        printToConsole("参数校验失败", request, exception);
        log.warn("参数校验失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(400, "请求参数不正确");
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException exception,
                                                              HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "请求参数不正确";
        }
        printToConsole("参数绑定失败", request, exception);
        log.warn("参数绑定失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(400, "请求参数不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                 HttpServletRequest request) {
        String message = "缺少必填参数：" + exception.getParameterName();
        printToConsole("缺少必填参数", request, exception);
        log.warn("缺少必填参数 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(400, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                             HttpServletRequest request) {
        String message = "参数类型不正确：" + exception.getName();
        printToConsole("参数类型不正确", request, exception);
        log.warn("参数类型不正确 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return buildResponse(400, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                   HttpServletRequest request) {
        printToConsole("请求方法不支持", request, exception);
        log.warn("请求方法不支持 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return buildResponse(405, "当前接口不支持该请求方法");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandlerFound(NoHandlerFoundException exception,
                                                               HttpServletRequest request) {
        printToConsole("接口不存在", request, exception);
        log.warn("接口不存在 [{} {}]", request.getMethod(), request.getRequestURI());
        return buildResponse(404, "请求的资源不存在");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException exception,
                                                                HttpServletRequest request) {
        printToConsole("非法参数", request, exception);
        log.warn("非法参数 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return buildResponse(400, "请求参数不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception exception, HttpServletRequest request) {
        printToConsole("未处理异常", request, exception);
        log.error("未处理异常 [{} {}]", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(500, "系统异常");
    }

    private ResponseEntity<ApiResponse<?>> buildResponse(int code, String message) {
        HttpStatusCode status = HttpStatus.resolve(code);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    private String firstNonBlank(String value, String fallback) {
        return Objects.toString(value, "").isBlank() ? fallback : value;
    }

    private void printToConsole(String title, HttpServletRequest request, Exception exception) {
        System.err.printf("[全局异常处理] %s [%s %s]: %s%n",
                title,
                request.getMethod(),
                request.getRequestURI(),
                firstNonBlank(exception.getMessage(), exception.getClass().getName()));
        exception.printStackTrace(System.err);
    }
}
