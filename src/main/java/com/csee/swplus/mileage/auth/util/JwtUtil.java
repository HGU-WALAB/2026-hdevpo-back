package com.csee.swplus.mileage.auth.util;

//import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import com.csee.swplus.mileage.auth.exception.WrongTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Slf4j
public class JwtUtil {
    private static final long EXPIRE_TIME_MS = 1000 * 60 * 60 * 2;  // 2 hours
    private static final long REFRESH_EXPIRE_TIME_MS = 1000 * 60 * 60 * 24;  // 1 day

    public static Key getSigningKey(String secretKey) {
        byte[] keyBytes = secretKey.getBytes();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, "HmacSHA256");
    }

    public static String createToken(String uniqueId, String name, String email, Key signingKey) {
        Claims claims = Jwts.claims();
        claims.put("uniqueId", uniqueId);
        claims.put("name", name);
        claims.put("email", email);
        log.info("Creating JWT access token for user: {}", name);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME_MS))
                .signWith(SignatureAlgorithm.HS256, signingKey)
                .compact();
    }

    public static String createRefreshToken(String uniqueId, String name, Key signingKey) {
        Claims claims = Jwts.claims();
        claims.put("uniqueId", uniqueId);
        claims.put("name", name);
        log.info("Creating JWT refresh token for user: {}", name);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRE_TIME_MS))
                .signWith(SignatureAlgorithm.HS256, signingKey)
                .compact();
    }

    public static String getUserId(String token, Key signingKey) {
        if (token == null || token.isEmpty()) {
            log.error("토큰이 null이거나 비어있습니다");
            throw new WrongTokenException("토큰이 없습니다.");
        }

        log.debug("토큰 검증 시도: {}", token.substring(0, Math.min(10, token.length())) + "...");
        try {
            Claims claims = extractClaims(token, signingKey);
            String userId = claims.get("uniqueId", String.class);
            log.debug("사용자 {}의 토큰 검증 성공", userId);
            return userId;
        } catch (WrongTokenException e) {
            log.error("토큰 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    private static Claims extractClaims(String token, Key signingKey) {
        try {
            return Jwts.parser().setSigningKey(signingKey).parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            throw new WrongTokenException("만료된 토큰입니다.");
        } catch (Exception e) {
            throw new WrongTokenException("유효하지 않은 토큰입니다.");
        }
    }
}