import { useCallback } from 'react';
import { useNavigate } from 'umi';
import { message } from 'antd';
import useUserStore from '@/stores/userStore';
import { tokenManager } from '@/utils/token';
import { mapLoginResponseToUserInfo } from '@/utils/userMapper';
import type { LoginResponse, UserRole, CertStatus } from '@/constants/apiTypes';

export function useAuth() {
  const navigate = useNavigate();
  const { userInfo, isLogin, setUserInfo, setToken, logout: storeLogout } = useUserStore();

  const redirectByRole = useCallback(
    (role: UserRole, certStatus?: CertStatus) => {
      switch (role) {
        case 'CANDIDATE':
          navigate('/candidate/home');
          break;
        case 'HR':
          if (certStatus === 'APPROVED') {
            navigate('/hr/dashboard');
          } else if (certStatus === 'PENDING') {
            message.warning('您的企业认证正在审核中，请等待审核通过');
            navigate('/certification/pending');
          } else if (certStatus === 'REJECTED') {
            message.error('您的企业认证未通过，请重新提交');
            navigate('/certification/pending');
          } else {
            message.warning('您还未完成企业认证，请先提交企业信息');
            navigate('/certification/pending');
          }
          break;
        case 'INTERVIEWER':
          navigate('/interviewer/interview');
          break;
        case 'ADMIN':
          navigate('/admin/dashboard');
          break;
        default:
          navigate('/login');
      }
    },
    [navigate],
  );

  const login = useCallback(
    (response: LoginResponse) => {
      const { accessToken, refreshToken, expiresIn } = response;

      // 存储Token
      tokenManager.setToken(accessToken, expiresIn);
      tokenManager.setRefreshToken(refreshToken);

      // 存储用户信息（使用共享映射函数）
      setToken(accessToken);
      setUserInfo(mapLoginResponseToUserInfo(response));

      // 根据角色跳转
      redirectByRole(response.user.role, response.user.company?.certStatus);
    },
    [setToken, setUserInfo, redirectByRole],
  );

  const logout = useCallback(() => {
    tokenManager.clearAll();
    storeLogout();
    navigate('/login');
  }, [storeLogout, navigate]);

  return {
    userInfo,
    isLogin,
    login,
    logout,
    redirectByRole,
  };
}
