import request from '@/utils/request';
import type {
  LoginResponse,
  SendCodeRequest,
  RegisterRequest,
  HrRegisterRequest,
  LoginByCodeRequest,
  LoginByPasswordRequest,
  ChangePasswordRequest,
  AdminLoginRequest,
} from '@/constants/apiTypes';

/** 发送验证码 */
export async function sendCode(data: SendCodeRequest): Promise<void> {
  return request.post('/v1/auth/send-code', data);
}

/** 用户注册 */
export async function register(data: RegisterRequest): Promise<LoginResponse> {
  return request.post('/v1/auth/register', data);
}

/** HR注册（角色固定HR，走 lingxi-hr 服务） */
export async function registerHr(data: HrRegisterRequest): Promise<LoginResponse> {
  return request.post('/v1/hr/register', data);
}

/** 验证码登录 */
export async function loginByCode(data: LoginByCodeRequest): Promise<LoginResponse> {
  return request.post('/v1/auth/login', data);
}

/** 密码登录 */
export async function loginByPassword(data: LoginByPasswordRequest): Promise<LoginResponse> {
  return request.post('/v1/auth/login-by-password', data);
}

/** 修改密码 */
export async function changePassword(data: ChangePasswordRequest): Promise<void> {
  return request.post('/v1/auth/change-password', data);
}

/** 管理员登录 */
export async function adminLogin(data: AdminLoginRequest): Promise<LoginResponse> {
  return request.post('/v1/admin-auth/login', data);
}

/** 管理员修改密码 */
export async function adminChangePassword(data: ChangePasswordRequest): Promise<void> {
  return request.post('/v1/admin-auth/change-password', data);
}
