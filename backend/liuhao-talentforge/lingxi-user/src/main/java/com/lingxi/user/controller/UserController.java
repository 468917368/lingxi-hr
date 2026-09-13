package com.lingxi.user.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.PageResult;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.IpUtil;
import com.lingxi.user.domain.dto.ChangePhoneRequest;
import com.lingxi.user.domain.dto.SendEmailCodeRequest;
import com.lingxi.user.domain.dto.SendPhoneCodeRequest;
import com.lingxi.user.domain.dto.UpdatePrivacyRequest;
import com.lingxi.user.domain.dto.UpdateProfileRequest;
import com.lingxi.user.domain.dto.UpdateUserInfoRequest;
import com.lingxi.user.domain.dto.VerifyEmailRequest;
import com.lingxi.user.agent.AgentConversationManager;
import com.lingxi.user.domain.entity.SysAgentConversation;
import com.lingxi.user.domain.vo.LoginLogVO;
import com.lingxi.user.domain.vo.PrivacyVO;
import com.lingxi.user.domain.vo.UserDashboardVO;
import com.lingxi.user.domain.vo.UserInfoVO;
import com.lingxi.user.domain.vo.UserPublicInfoVO;
import com.lingxi.user.service.AuthService;
import com.lingxi.user.service.EmailService;
import com.lingxi.user.service.FileService;
import com.lingxi.user.service.LoginLogService;
import com.lingxi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 用户信息控制器
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@RequireLogin
@RequireRole({"CANDIDATE"})
public class UserController {

    private final UserService userService;
    private final FileService fileService;
    private final EmailService emailService;
    private final AuthService authService;
    private final LoginLogService loginLogService;
    private final AgentConversationManager conversationManager;

    /**
     * 获取当前用户信息（含画像）
     * GET /api/v1/user/info
     *
     * @return 用户信息（含画像）
     */
    @RequireLogin
    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * 获取用户公开信息（不含隐私数据）
     * GET /api/v1/user/{userId}/public
     *
     * @param userId 用户ID
     * @return 用户公开信息
     */
    @RequireRole({"CANDIDATE", "HR", "INTERVIEWER"})
    @GetMapping("/{userId}/public")
    public Result<UserPublicInfoVO> getUserPublicInfo(@PathVariable Long userId) {
        return Result.success(userService.getUserPublicInfo(userId));
    }

    /**
     * 更新用户基本信息
     * PUT /api/v1/user/info
     *
     * @param request 更新请求
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@RequestBody @Valid UpdateUserInfoRequest request) {
        Long userId = UserContext.getUserId();
        userService.updateUserInfo(userId, request);
        return Result.success();
    }

    /**
     * 更新求职意向
     * PUT /api/v1/user/profile
     *
     * @param request 更新请求
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        Long userId = UserContext.getUserId();
        userService.updateUserProfile(userId, request);
        return Result.success();
    }

    /**
     * 获取隐私设置
     * GET /api/v1/user/privacy
     *
     * @return 隐私设置
     */
    @RequireLogin
    @GetMapping("/privacy")
    public Result<PrivacyVO> getPrivacySettings() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getPrivacySettings(userId));
    }

    /**
     * 更新隐私设置
     * PUT /api/v1/user/privacy
     *
     * @param request 更新请求
     * @return 操作结果
     */
    @RequireLogin
    @PutMapping("/privacy")
    public Result<Void> updatePrivacySettings(@RequestBody @Valid UpdatePrivacyRequest request) {
        Long userId = UserContext.getUserId();
        userService.updatePrivacySettings(userId, request);
        return Result.success();
    }

    /**
     * 上传用户头像
     * POST /api/v1/user/avatar
     *
     * @param file 头像文件
     * @return 头像URL
     */
    @RequireLogin
    @RequireRole({"CANDIDATE", "HR", "INTERVIEWER"})
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();

        // 1. 查询旧头像URL（用于后续清理）
        UserInfoVO userInfo = userService.getUserInfo(userId);
        String oldAvatarUrl = userInfo.getAvatar();

        // 2. 上传新头像文件
        String newAvatarUrl = fileService.uploadAvatar(userId, file);

        // 3. 更新用户头像URL到数据库
        userService.updateAvatar(userId, newAvatarUrl);

        // 4. 异步清理旧头像文件（不影响主流程）
        if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
            String oldObjectName = fileService.extractObjectName(oldAvatarUrl);
            if (oldObjectName != null) {
                fileService.deleteFile(oldObjectName);
            }
        }

        return Result.success(newAvatarUrl);
    }

    /**
     * 发送邮箱验证码
     * POST /api/v1/user/email/send-code
     *
     * @param request     发送验证码请求
     * @param httpRequest HTTP请求（用于获取IP）
     * @return 操作结果
     */
    @RequireLogin
    @PostMapping("/email/send-code")
    public Result<Void> sendEmailCode(@RequestBody @Valid SendEmailCodeRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = UserContext.getUserId();
        String clientIp = IpUtil.getClientIp(httpRequest);

        // IP限流（单IP每分钟最多5次）
        // 注意：这里简化处理，实际应该在EmailService中处理

        emailService.sendEmailVerificationCode(request.getEmail());
        return Result.success();
    }

    /**
     * 验证邮箱并更新
     * POST /api/v1/user/email/verify
     *
     * @param request 验证请求
     * @return 操作结果
     */
    @RequireLogin
    @PostMapping("/email/verify")
    public Result<Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        Long userId = UserContext.getUserId();

        // 1. 验证验证码
        emailService.verifyEmailCode(request.getEmail(), request.getCode());

        // 2. 验证通过，更新邮箱
        userService.updateEmail(userId, request.getEmail());

        return Result.success();
    }

    /**
     * 发送手机号验证码（用于修改手机号）
     * POST /api/v1/user/phone/send-code
     *
     * @param request     发送验证码请求
     * @param httpRequest HTTP请求（用于获取IP）
     * @return 操作结果
     */
    @RequireLogin
    @PostMapping("/phone/send-code")
    public Result<Void> sendPhoneCode(@RequestBody @Valid SendPhoneCodeRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = UserContext.getUserId();
        String clientIp = IpUtil.getClientIp(httpRequest);

        // 发送验证码到新手机号
        authService.sendVerificationCode(request.getNewPhone(), clientIp);
        return Result.success();
    }

    /**
     * 验证并修改手机号
     * POST /api/v1/user/phone
     *
     * @param request 修改手机号请求
     * @return 操作结果
     */
    @RequireLogin
    @PostMapping("/phone")
    public Result<Void> changePhone(@RequestBody @Valid ChangePhoneRequest request) {
        Long userId = UserContext.getUserId();

        // 1. 验证验证码
        authService.verifyCode(request.getNewPhone(), request.getCode());

        // 2. 验证通过，修改手机号
        userService.changePhone(userId, request.getNewPhone());

        return Result.success();
    }

    /**
     * 查询登录日志
     * GET /api/v1/user/login-logs
     *
     * @param page 页码（默认1）
     * @param size 每页条数（默认10）
     * @return 登录日志分页数据
     */
    @RequireLogin
    @GetMapping("/login-logs")
    public Result<PageResult<LoginLogVO>> getLoginLogs(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Long userId = UserContext.getUserId();

        // 参数校验
        if (page < 1) page = 1;
        if (size < 1 || size > 50) size = 10;

        // 查询数据
        List<LoginLogVO> list = loginLogService.getLoginLogs(userId, page, size);
        long total = loginLogService.countLoginLogs(userId);

        return Result.success(PageResult.of(list, total, page, size));
    }

    /**
     * 用户仪表盘（聚合接口）
     * GET /api/v1/user/dashboard
     *
     * 一次返回：用户信息 + 隐私设置 + 最近会话 + 未读数
     * 减少前端请求次数：3次 → 1次
     *
     * @return 聚合数据
     */
    @RequireLogin
    @GetMapping("/dashboard")
    public Result<UserDashboardVO> getDashboard() {
        Long userId = UserContext.getUserId();

        UserDashboardVO dashboard = new UserDashboardVO();
        dashboard.setUserInfo(userService.getUserInfo(userId));
        dashboard.setPrivacy(userService.getPrivacySettings(userId));

        // 最近5个会话
        List<SysAgentConversation> sessions = conversationManager.getUserSessions(userId, 1, 5);
        dashboard.setRecentSessions(sessions.stream().map(msg -> {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("sessionId", msg.getSessionId());
            item.put("lastMessage", msg.getContent());
            item.put("lastRole", msg.getRole());
            item.put("updatedAt", msg.getCreatedAt());
            return item;
        }).collect(java.util.stream.Collectors.toList()));

        dashboard.setUnreadCount(0); // 未读数可从聊天模块获取

        return Result.success(dashboard);
    }
}
