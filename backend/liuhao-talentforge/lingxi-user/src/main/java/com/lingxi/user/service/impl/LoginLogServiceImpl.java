package com.lingxi.user.service.impl;

import com.lingxi.common.util.IpUtil;
import com.lingxi.user.domain.entity.SysLoginLog;
import com.lingxi.user.domain.vo.LoginLogVO;
import com.lingxi.user.mapper.SysLoginLogMapper;
import com.lingxi.user.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录日志服务实现
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Async("asyncExecutor")
    public void recordLog(Long userId, String ip, String userAgent, int status, String failReason) {
        // userId为空时跳过记录（如验证码过期等未识别用户的场景）
        if (userId == null) {
            log.debug("登录日志跳过: userId为空, status={}, reason={}", status, failReason);
            return;
        }
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUserId(userId);
            loginLog.setLoginIp(ip);
            loginLog.setLoginDevice(userAgent);
            loginLog.setLoginStatus(status);
            loginLog.setFailReason(failReason);

            // 解析IP地址为地理位置
            String location = IpUtil.getLocation(ip);
            loginLog.setLoginLocation(location);

            loginLogMapper.insert(loginLog);
            log.debug("登录日志记录成功: userId={}, status={}, location={}", userId, status, location);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
            log.error("登录日志记录失败: userId={}", userId, e);
        }
    }

    @Override
    public List<LoginLogVO> getLoginLogs(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;  // 限制最大每页条数

        int offset = (page - 1) * size;
        List<SysLoginLog> logs = loginLogMapper.selectByUserId(userId, offset, size);
        return logs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public long countLoginLogs(Long userId) {
        return loginLogMapper.countByUserId(userId);
    }

    /**
     * 实体转VO
     *
     * @param entity 登录日志实体
     * @return 登录日志VO
     */
    private LoginLogVO convertToVO(SysLoginLog entity) {
        return LoginLogVO.builder()
                .id(entity.getId())
                .loginIp(entity.getLoginIp())
                .loginDevice(entity.getLoginDevice())
                .loginLocation(entity.getLoginLocation())
                .loginTime(entity.getLoginTime() != null ? entity.getLoginTime().format(DATE_FORMATTER) : null)
                .loginStatus(entity.getLoginStatus())
                .failReason(entity.getFailReason())
                .build();
    }
}
