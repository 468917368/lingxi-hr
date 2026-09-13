/** Token管理 */
const TOKEN_KEY = 'lingxi_accessToken';
const REFRESH_TOKEN_KEY = 'lingxi_refreshToken';
const TOKEN_EXPIRE_KEY = 'lingxi_tokenExpireTime';

export const tokenManager = {
  getToken: (): string | null => localStorage.getItem(TOKEN_KEY),

  setToken: (token: string, expiresIn: number): void => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(TOKEN_EXPIRE_KEY, String(Date.now() + expiresIn * 1000));
  },

  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),

  setRefreshToken: (token: string): void => {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  },

  clearAll: (): void => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(TOKEN_EXPIRE_KEY);
  },

  /** 检查Token是否即将过期（5分钟内） */
  isTokenExpiringSoon: (): boolean => {
    const expireTimeStr = localStorage.getItem(TOKEN_EXPIRE_KEY);
    if (!expireTimeStr) return false; // 无过期时间信息，不触发刷新
    const expireTime = Number(expireTimeStr);
    if (isNaN(expireTime) || expireTime <= 0) return false;
    return expireTime - Date.now() < 5 * 60 * 1000;
  },

  /** 检查Token是否已过期 */
  isTokenExpired: (): boolean => {
    const expireTimeStr = localStorage.getItem(TOKEN_EXPIRE_KEY);
    if (!expireTimeStr) return true; // 无过期时间信息，视为已过期
    const expireTime = Number(expireTimeStr);
    if (isNaN(expireTime) || expireTime <= 0) return true;
    return Date.now() >= expireTime;
  },
};
