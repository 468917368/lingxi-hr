package com.lingxi.user.service;

import com.lingxi.user.domain.vo.LoginLogVO;

import java.util.List;

/**
 * 登录日志服务接口
 *
 * @author 成员A
 * @since 2026-08-02
 */
public interface LoginLogService {

    /**
     * 记录登录日志
     *
     * @param userId      用户ID（失败时可为null）
     * @param ip          登录IP
     * @param userAgent   浏览器UA
     * @param status      登录状态：1=成功 0=失败
     * @param failReason  失败原因（成功时为null）
     */
    void recordLog(Long userId, String ip, String userAgent, int status, String failReason);

    /**
     * 分页查询用户登录日志
     *
     * @param userId 用户ID
     * @param page   页码（从1开始）
     * @param size   每页条数
     * @return 登录日志列表
     */
    List<LoginLogVO> getLoginLogs(Long userId, int page, int size);

    /**
     * 统计用户登录日志总数
     *
     * @param userId 用户ID
     * @return 总数
     */
    long countLoginLogs(Long userId);
}
