package com.example.takeout.common.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.takeout.common.exception.ServiceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 工具服务，负责生成 token 和校验 token 里的用户身份。
 */
@Component
public class JwtTokenService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expireHours;

    public JwtTokenService(@Value("${takeout.jwt.secret:takeout-demo-jwt-secret}") String secret,
                           @Value("${takeout.jwt.expire-hours:72}") long expireHours) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expireHours = expireHours;
    }

    public String createToken(Long userId, UserRole role, String displayName) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("role", role.name())
                .withClaim("displayName", displayName == null ? "" : displayName)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(expireHours, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    public CurrentUser parseToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return new CurrentUser(
                    Long.parseLong(jwt.getSubject()),
                    UserRole.valueOf(jwt.getClaim("role").asString()),
                    jwt.getClaim("displayName").asString()
            );
        } catch (Exception exception) {
            throw new ServiceException(401, "登录已失效，请重新登录");
        }
    }
}
