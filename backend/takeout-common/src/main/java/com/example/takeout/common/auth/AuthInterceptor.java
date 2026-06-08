package com.example.takeout.common.auth;

import com.example.takeout.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;

    public AuthInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isPublic(handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new ServiceException(401, "请先登录");
        }

        CurrentUser currentUser = jwtTokenService.parseToken(token);
        RequireRole requireRole = findRequireRole(handlerMethod);
        if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(role -> role == currentUser.role())) {
            throw new ServiceException(403, "没有权限访问该资源");
        }
        AuthContext.set(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return AnnotationUtils.findAnnotation(handlerMethod.getMethod(), PublicApi.class) != null
                || AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), PublicApi.class) != null;
    }

    private RequireRole findRequireRole(HandlerMethod handlerMethod) {
        RequireRole methodRole = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequireRole.class);
        if (methodRole != null) {
            return methodRole;
        }
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequireRole.class);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length());
        }
        String token = request.getHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return request.getParameter("token");
    }
}
