package com.lingxi.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 工具类
 * <p>
 * 功能：
 * 1. 获取客户端真实IP
 * 2. 根据IP解析地理位置（带缓存）
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
public class IpUtil {

    private IpUtil() {
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** IP地理位置缓存（简单内存缓存，生产环境建议使用Caffeine） */
    private static final Map<String, String> locationCache = new ConcurrentHashMap<>();

    /** 缓存最大容量 */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 获取客户端真实IP
     *
     * @param request HTTP请求
     * @return 客户端IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 注意：X-Forwarded-For 可被伪造，生产环境应配置可信代理白名单
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 根据IP获取地理位置（带缓存）
     *
     * @param ip IP地址
     * @return 地理位置信息，解析失败返回"未知地址"
     */
    public static String getLocation(String ip) {
        if (isLocalIp(ip)) {
            return "本机地址";
        }

        // 查询缓存
        return locationCache.computeIfAbsent(ip, key -> queryLocation(key));
    }

    /**
     * 判断是否为本地IP
     */
    private static boolean isLocalIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("127.")
                || "localhost".equalsIgnoreCase(ip)
                || "unknown".equalsIgnoreCase(ip);
    }

    /**
     * 查询IP地理位置
     */
    private static String queryLocation(String ip) {
        // 防止缓存过大
        if (locationCache.size() >= MAX_CACHE_SIZE) {
            locationCache.clear();
        }

        try {
            // 使用 ip-api.com 免费API（HTTPS）
            String url = "https://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,country,regionName,city";
            String response = sendGet(url);

            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                if ("success".equals(json.get("status").asText())) {
                    String country = json.get("country").asText("");
                    String region = json.get("regionName").asText("");
                    String city = json.get("city").asText("");

                    // 拼接地址：国家 省份 城市
                    StringBuilder location = new StringBuilder();
                    if (!country.isEmpty()) location.append(country);
                    if (!region.isEmpty() && !region.equals(city)) {
                        location.append(" ").append(region);
                    }
                    if (!city.isEmpty()) location.append(" ").append(city);

                    return location.toString().trim();
                }
            }
        } catch (Exception e) {
            log.warn("IP地址解析失败: ip={}", ip, e);
        }

        return "未知地址";
    }

    /**
     * 发送GET请求（使用try-with-resources防止资源泄漏）
     */
    private static String sendGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            }
        } catch (Exception e) {
            log.debug("HTTP请求失败: {}", e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }
}
