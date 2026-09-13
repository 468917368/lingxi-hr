package com.lingxi.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
public class JwtUtil {

    private JwtUtil() {
    }

    /** 默认密钥（生产环境应从配置中心获取） */
    private static final String DEFAULT_SECRET = "lingxi-backend-jwt-secret-key-2026-minimum-256-bits";

    /** AccessToken有效期（2小时） */
    private static final long ACCESS_TOKEN_EXPIRATION = 2 * 60 * 60 * 1000L;

    /** RefreshToken有效期（7天） */
    private static final long REFRESH_TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 获取签名密钥
     */
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成AccessToken
     *
     * @param userId     用户ID
     * @param phone      手机号
     * @param role       角色
     * @param companyId  企业ID（可为null）
     * @param certStatus 认证状态（可为null）
     * @return AccessToken
     */
    public static String generateAccessToken(Long userId, String phone, String role,
                                              Long companyId, String certStatus) {
        return generateAccessToken(userId, phone, role, companyId, certStatus, null);
    }

    /**
     * 生成AccessToken（带用户状态）
     *
     * @param userId     用户ID
     * @param phone      手机号
     * @param role       角色
     * @param companyId  企业ID（可为null）
     * @param certStatus 认证状态（可为null）
     * @param status     用户状态（可为null）
     * @return AccessToken
     */
    public static String generateAccessToken(Long userId, String phone, String role,
                                              Long companyId, String certStatus, String status) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("phone", phone);
        claims.put("role", role);
        claims.put("type", "access");
        if (companyId != null) {
            claims.put("companyId", companyId);
        }
        if (certStatus != null) {
            claims.put("certStatus", certStatus);
        }
        if (status != null) {
            claims.put("status", status);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成RefreshToken
     *
     * @param userId 用户ID
     * @return RefreshToken
     */
    public static String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        claims.put("jti", UUID.randomUUID().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析Token
     *
     * @param token Token字符串
     * @return Claims
     */
    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否有效
     *
     * @param token Token字符串
     * @return 是否有效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Token无效: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token Token字符串
     * @return 用户ID
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取角色
     *
     * @param token Token字符串
     * @return 角色
     */
    public static String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从Token中获取企业ID
     *
     * @param token Token字符串
     * @return 企业ID
     */
    public static Long getCompanyIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("companyId", Long.class);
    }

    /**
     * 从Token中获取手机号
     *
     * @param token Token字符串
     * @return 手机号
     */
    public static String getPhoneFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("phone", String.class);
    }

    /**
     * 获取Token类型
     *
     * @param token Token字符串
     * @return Token类型（access/refresh）
     */
    public static String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    /**
     * 判断Token是否即将过期（5分钟内）
     *
     * @param token Token字符串
     * @return 是否即将过期
     */
    public static boolean isTokenExpiringSoon(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long timeUntilExpiration = expiration.getTime() - System.currentTimeMillis();
            return timeUntilExpiration < 5 * 60 * 1000L;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取AccessToken过期时间（秒）
     */
    public static long getAccessTokenExpirationInSeconds() {
        return ACCESS_TOKEN_EXPIRATION / 1000;
    }
}
