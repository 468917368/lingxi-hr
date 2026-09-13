import type { RuleObject } from 'antd/es/form';

/** 手机号格式校验 */
export const isValidPhone = (phone: string): boolean => {
  return /^1[3-9]\d{9}$/.test(phone);
};

/** 手机号脱敏显示 */
export const maskPhone = (phone: string): string => {
  if (!phone || phone.length < 7) return phone;
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
};

/** 邮箱格式校验 */
export const isValidEmail = (email: string): boolean => {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
};

/**
 * 检查姓名是否可以修改（每月只能修改一次）
 * @param nameUpdatedAt 姓名最后修改时间
 * @returns { canModify: boolean, nextModifyDate: string | null }
 */
export const checkNameModifiable = (nameUpdatedAt?: string): {
  canModify: boolean;
  nextModifyDate: string | null;
} => {
  if (!nameUpdatedAt) {
    return { canModify: true, nextModifyDate: null };
  }

  const lastUpdate = new Date(nameUpdatedAt);
  const now = new Date();

  // 计算下次可修改时间（一个月后）
  const nextDate = new Date(lastUpdate);
  nextDate.setMonth(nextDate.getMonth() + 1);

  if (now < nextDate) {
    // 还在限制期内
    const formattedDate = nextDate.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
    return { canModify: false, nextModifyDate: formattedDate };
  }

  return { canModify: true, nextModifyDate: null };
};

/** 密码强度校验（8-20位，大小写字母+数字+特殊字符） */
export const validatePassword = (_: RuleObject, value: string): Promise<void> => {
  if (!value) return Promise.reject(new Error('请输入密码'));
  if (value.length < 8 || value.length > 20) {
    return Promise.reject(new Error('密码长度须在8-20位之间'));
  }
  if (!/(?=.*[a-z])/.test(value)) {
    return Promise.reject(new Error('须包含小写字母'));
  }
  if (!/(?=.*[A-Z])/.test(value)) {
    return Promise.reject(new Error('须包含大写字母'));
  }
  if (!/(?=.*\d)/.test(value)) {
    return Promise.reject(new Error('须包含数字'));
  }
  if (!/(?=.*[^a-zA-Z0-9])/.test(value)) {
    return Promise.reject(new Error('须包含特殊字符'));
  }
  return Promise.resolve();
};
