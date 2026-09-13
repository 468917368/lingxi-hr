/**
 * API 成功码常量
 * 全局统一成功码为 200，由请求拦截器（src/utils/request.ts）集中判断。
 * 页面与 service 层不得直接判断成功码，统一通过拦截器处理。
 */

/** 全局成功码 */
export const SUCCESS_CODE = 200;

/** 是否成功响应（统一判断全局成功码） */
export const isSuccess = (code: number): boolean => code === SUCCESS_CODE;
