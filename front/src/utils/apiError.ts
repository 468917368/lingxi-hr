/** API 错误类型 */
export interface ApiError extends Error {
  code?: number;
  message: string;
}

/** 判断是否为 ApiError */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && 'code' in error;
}

/** 安全地获取错误信息 */
export function getErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  if (typeof error === 'string') {
    return error;
  }
  return fallback;
}

/** 安全地获取错误码 */
export function getErrorCode(error: unknown): number | undefined {
  if (isApiError(error)) {
    return error.code;
  }
  return undefined;
}
