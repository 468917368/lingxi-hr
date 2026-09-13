import axios, { type InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { tokenManager } from './token';
import { JSONBigString } from './json';
import { isSuccess } from '@/constants/apiCodes';
import type { ApiError } from './apiError';

// 请求级自定义配置：silent=true 时业务错误不弹全局 toast，由调用方自行处理
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean;
  }
}

// 模块级状态（仅CSR模式，SSR需改为请求级状态）
let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

// 独立的刷新Token请求实例（避免循环依赖）
const refreshRequest = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  // 用 json-bigint 替换默认 JSON.parse：雪花 ID（19 位）超 JS Number 安全范围，
  // 需解析为字符串避免精度丢失（详见 utils/json.ts）
  transformResponse: [
    (data) => {
      if (typeof data !== 'string') return data; // blob / FormData 等原样返回
      try {
        return JSONBigString.parse(data);
      } catch {
        return data; // 非 JSON 原样返回
      }
    },
  ],
});

// 不需要token的白名单路径
const AUTH_WHITELIST = ['/v1/auth/send-code', '/v1/auth/login', '/v1/auth/login-by-password', '/v1/auth/register', '/v1/auth/refresh-token', '/v1/admin-auth/login', '/v1/hr/register'];

// 请求拦截器
request.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // 白名单路径跳过token刷新逻辑
    const isAuthPath = AUTH_WHITELIST.some((p) => config.url?.startsWith(p));

    const token = tokenManager.getToken();

    // Token即将过期，自动刷新（仅非登录接口）
    if (!isAuthPath && token && tokenManager.isTokenExpiringSoon()) {
      if (!isRefreshing) {
        isRefreshing = true;
        try {
          const refreshTokenValue = tokenManager.getRefreshToken();
          const res = await refreshRequest.post('/v1/auth/refresh-token', {
            refreshToken: refreshTokenValue,
          });
          const { accessToken, refreshToken: newRefreshToken, expiresIn } = res.data.data;

          tokenManager.setToken(accessToken, expiresIn);
          tokenManager.setRefreshToken(newRefreshToken);

          // 重发等待中的请求
          pendingRequests.forEach((callback) => callback(accessToken));
          pendingRequests = [];

          config.headers.Authorization = `Bearer ${accessToken}`;
        } catch {
          // 刷新失败，清空等待队列
          pendingRequests = [];
          tokenManager.clearAll();
          message.error('登录已过期，请重新登录');
          window.location.href = '/login';
          return Promise.reject(new Error('Token刷新失败'));
        } finally {
          isRefreshing = false;
        }
      } else {
        // 正在刷新中，将当前请求加入队列等待
        return new Promise<InternalAxiosRequestConfig>((resolve) => {
          pendingRequests.push((newToken: string) => {
            config.headers.Authorization = `Bearer ${newToken}`;
            resolve(config);
          });
        });
      }
    }

    // 非登录接口才带token
    if (!isAuthPath && token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截器
// 后端统一响应: { code: number, message: string, data: T }
// 成功码集中判断于 @/constants/apiCodes（统一成功码 200）
request.interceptors.response.use(
  (response) => {
    // 防御性检查响应结构
    if (!response.data || typeof response.data !== 'object') {
      const error = new Error('服务器返回数据格式错误') as ApiError;
      error.code = -1;
      return Promise.reject(error);
    }

    const { code, message: msg, data } = response.data;

    // 操作成功（isSuccess 集中判断统一成功码 200）
    if (isSuccess(code)) {
      return data;
    }

    // 业务错误码401 → Token过期
    if (code === 401) {
      tokenManager.clearAll();
      message.error('登录已过期，请重新登录');
      window.location.href = '/login';
      const error = new Error('Token已过期') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 无权限
    if (code === 403) {
      message.error('无权限访问');
      const error = new Error('无权限') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 用户被禁用（错误码1004）
    if (code === 1004) {
      tokenManager.clearAll();
      message.error(msg || '账号已被禁用，请联系管理员');
      window.location.href = '/login';
      const error = new Error(msg || '账号已被禁用，请联系管理员') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // ===== job 服务错误码（成员B，20xx/21xx/22xx/23xx）=====
    // 所有业务错误分支统一保留 error.code，供页面用 getErrorCode 差异化处理

    // 岗位不存在或无权访问
    if (code === 2101) {
      message.error(msg || '岗位不存在或已关闭');
      const error = new Error(msg || '岗位不存在或已关闭') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 乐观锁版本冲突
    if (code === 2102) {
      message.warning(msg || '数据已被其他人修改，请刷新后重试');
      const error = new Error(msg || '数据已被其他人修改，请刷新后重试') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 409 通用冲突（状态迁移非法、HC冲突等）
    if (code >= 2103 && code <= 2204) {
      message.warning(msg || '操作冲突，请刷新后重试');
      const error = new Error(msg || '操作冲突，请刷新后重试') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // AI 服务不可用
    if (code >= 2301 && code <= 2304) {
      message.warning(msg || 'AI服务暂不可用，请稍后重试或手动填写');
      const error = new Error(msg || 'AI服务暂不可用，请稍后重试或手动填写') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // ===== 企业题库错误码（阶段6.1，题库管理）=====

    // 题目不存在或无权访问（含跨企业）
    if (code === 2401) {
      message.error(msg || '题目不存在或无权访问');
      const error = new Error(msg || '题目不存在或无权访问') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 乐观锁版本冲突（2402 重点：页面需关闭编辑态并刷新列表）
    if (code === 2402) {
      message.warning(msg || '题目已被其他人修改，请刷新后重试');
      const error = new Error(msg || '题目已被其他人修改，请刷新后重试') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 非法状态流转 / 同企业重复题干 / 审核操作不合法（2403~2406）
    if (code >= 2403 && code <= 2406) {
      message.warning(msg || '操作被拒绝，请刷新后重试');
      const error = new Error(msg || '操作被拒绝，请刷新后重试') as ApiError;
      error.code = code;
      return Promise.reject(error);
    }

    // 其他业务错误
    const error = new Error(msg || '请求失败') as ApiError;
    error.code = code;
    if (!response.config?.silent) {
      message.error(error.message);
    }
    return Promise.reject(error);
  },
  (error) => {
    // HTTP 401 → Token过期
    if (error.response?.status === 401) {
      tokenManager.clearAll();
      message.error('登录已过期，请重新登录');
      window.location.href = '/login';
      return Promise.reject(error);
    }

    // HTTP 403 → 无权限（可能是用户被禁用）
    if (error.response?.status === 403) {
      const errorData = error.response.data;
      const errorMsg = errorData?.message || '无权限访问';
      // 如果是用户被禁用，清除token并跳转登录
      if (errorMsg.includes('禁用')) {
        tokenManager.clearAll();
        message.error(errorMsg);
        window.location.href = '/login';
      } else {
        message.error(errorMsg);
      }
      const err = new Error(errorMsg) as ApiError;
      err.code = 403;
      return Promise.reject(err);
    }

    // HTTP 400 → 参数校验错误
    if (error.response?.status === 400) {
      const errorData = error.response.data;
      const errorMsg = errorData?.message || errorData?.data || '请求参数错误';
      message.error(errorMsg);
      const err = new Error(errorMsg) as ApiError;
      err.code = 400;
      return Promise.reject(err);
    }

    // HTTP 405 → 方法不允许
    if (error.response?.status === 405) {
      message.error('请求方式错误');
      return Promise.reject(error);
    }

    // HTTP 500 → 服务器错误
    if (error.response?.status === 500) {
      message.error('服务器内部错误，请稍后重试');
      return Promise.reject(error);
    }

    // 其他网络错误
    message.error('网络异常，请稍后重试');
    return Promise.reject(error);
  },
);

export default request;
