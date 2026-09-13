package com.lingxi.common.constant;

/**
 * 公共常量
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class CommonConstant {

    private CommonConstant() {
    }

    /** 成功状态码 */
    public static final int SUCCESS_CODE = 200;

    /** 成功消息 */
    public static final String SUCCESS_MESSAGE = "success";

    /** 系统错误状态码 */
    public static final int SYSTEM_ERROR_CODE = 500;

    /** 系统错误消息 */
    public static final String SYSTEM_ERROR_MESSAGE = "系统内部错误";

    /** 请求头 - Token */
    public static final String HEADER_TOKEN = "Authorization";

    /** 请求头 - 用户ID */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 请求头 - 用户角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 请求头 - 企业ID */
    public static final String HEADER_COMPANY_ID = "X-Company-Id";

    /** 请求头 - 请求ID */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 分页默认页码 */
    public static final int DEFAULT_PAGE = 1;

    /** 分页默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页最大每页条数 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 系统操作人ID */
    public static final Long SYSTEM_OPERATOR_ID = 0L;

    /** 系统操作人角色 */
    public static final String SYSTEM_OPERATOR_ROLE = "SYSTEM";
}
