import request from '@/utils/request';
import type { UserInfo, UpdateUserInfoRequest, UpdateProfileRequest, PrivacySettings, LoginLog, PageResult } from '@/constants/apiTypes';
import type { UserPublicInfo } from '@/components/UserProfileCard';

/** 获取当前用户信息 */
export async function getUserInfo(): Promise<UserInfo> {
  return request.get('/v1/user/info');
}

/** 获取用户公开信息（不含隐私数据） */
export async function getUserPublicInfo(userId: number): Promise<UserPublicInfo> {
  return request.get(`/v1/user/${userId}/public`);
}

/** 更新用户信息 */
export async function updateUserInfo(data: UpdateUserInfoRequest): Promise<void> {
  return request.put('/v1/user/info', data);
}

/** 更新求职意向 */
export async function updateProfile(data: UpdateProfileRequest): Promise<void> {
  return request.put('/v1/user/profile', data);
}

/** 获取隐私设置 */
export async function getPrivacySettings(): Promise<PrivacySettings> {
  return request.get('/v1/user/privacy');
}

/** 更新隐私设置 */
export async function updatePrivacySettings(data: Partial<PrivacySettings>): Promise<void> {
  return request.put('/v1/user/privacy', data);
}

/** 上传头像 */
export async function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/v1/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 发送邮箱验证码 */
export async function sendEmailCode(email: string): Promise<void> {
  return request.post('/v1/user/email/send-code', { email });
}

/** 验证邮箱 */
export async function verifyEmail(email: string, code: string): Promise<void> {
  return request.post('/v1/user/email/verify', { email, code });
}

/** 发送手机号验证码（用于修改手机号） */
export async function sendPhoneCode(newPhone: string): Promise<void> {
  return request.post('/v1/user/phone/send-code', { newPhone });
}

/** 修改手机号 */
export async function changePhone(newPhone: string, code: string): Promise<void> {
  return request.post('/v1/user/phone', { newPhone, code });
}

/** 获取登录日志 */
export async function getLoginLogs(page = 1, size = 10): Promise<PageResult<LoginLog>> {
  return request.get('/v1/user/login-logs', { params: { page, size } });
}
