import { useState, useCallback, useEffect, useRef } from 'react';
import { message } from 'antd';
import useUserStore from '@/stores/userStore';
import { tokenManager } from '@/utils/token';
import { register, registerHr } from '@/services/auth';
import { mapLoginResponseToUserInfo } from '@/utils/userMapper';
import type { LoginResponse } from '@/constants/apiTypes';

const isValidPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone);

const validatePassword = (pwd: string): string[] => {
  const errors: string[] = [];
  if (pwd.length < 8 || pwd.length > 20) errors.push('密码长度须在8-20位之间');
  if (!/(?=.*[a-z])/.test(pwd)) errors.push('须包含小写字母');
  if (!/(?=.*[A-Z])/.test(pwd)) errors.push('须包含大写字母');
  if (!/(?=.*\d)/.test(pwd)) errors.push('须包含数字');
  if (!/(?=.*[^a-zA-Z0-9])/.test(pwd)) errors.push('须包含特殊字符');
  return errors;
};

export interface RegisterFormState {
  phone: string;
  code: string;
  password: string;
  confirmPassword: string;
  name: string;
}

export function useRegister() {
  const { setUserInfo, setToken } = useUserStore();
  const [loading, setLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [registerResponse, setRegisterResponse] = useState<LoginResponse | null>(null);
  const isMountedRef = useRef(true);

  // 倒计时副作用
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  // 组件卸载标记
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /** 校验基础表单 */
  const validateStep1 = useCallback((form: RegisterFormState): boolean => {
    if (!isValidPhone(form.phone)) { message.warning('请输入正确的手机号'); return false; }
    if (!form.code || form.code.length !== 6) { message.warning('请输入6位验证码'); return false; }
    const pwdErrors = validatePassword(form.password);
    if (pwdErrors.length > 0) { message.warning(pwdErrors[0]); return false; }
    if (form.password !== form.confirmPassword) { message.warning('两次输入的密码不一致'); return false; }
    if (!form.name.trim()) { message.warning('请输入真实姓名'); return false; }
    return true;
  }, []);

  /** 执行注册（只存Token，不设置用户信息） */
  const doRegister = useCallback(async (form: RegisterFormState, role: 'CANDIDATE' | 'HR'): Promise<boolean> => {
    setLoading(true);
    try {
      // 求职者走 lingxi-user 注册；HR 走 lingxi-hr 注册（角色固定 HR）
      const response = role === 'HR'
        ? await registerHr({
            phone: form.phone,
            code: form.code,
            password: form.password,
            name: form.name,
          })
        : await register({
            phone: form.phone,
            code: form.code,
            password: form.password,
            name: form.name,
            role,
          });
      if (!isMountedRef.current) return false;
      // 保存注册响应
      setRegisterResponse(response);
      // 存储Token
      const { accessToken, refreshToken, expiresIn } = response;
      tokenManager.setToken(accessToken, expiresIn);
      tokenManager.setRefreshToken(refreshToken);
      setToken(accessToken);
      message.success('注册成功，请继续完善信息');
      return true;
    } catch {
      if (!isMountedRef.current) return false;
      return false;
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, [setToken]);

  /** 完成注册（设置用户信息） */
  const completeRegistration = useCallback(() => {
    if (!registerResponse) {
      message.error('注册信息丢失，请重新注册');
      return false;
    }
    const userInfo = mapLoginResponseToUserInfo(registerResponse);
    setUserInfo(userInfo);
    return true;
  }, [registerResponse, setUserInfo]);

  return {
    loading,
    setLoading,
    countdown,
    setCountdown,
    sending,
    setSending,
    registerResponse,
    validateStep1,
    doRegister,
    completeRegistration,
  };
}
