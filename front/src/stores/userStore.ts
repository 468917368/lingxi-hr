import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserRole } from '@/constants/enums';
import { wsManager } from '@/utils/websocket-singleton';

export interface UserProfile {
  gender?: string;
  city?: string;
  workYears?: string;
  education?: string;
  jobStatus?: string;
  desiredJob?: string;
  desiredCity?: string;
  desiredSalaryMin?: number;
  desiredSalaryMax?: number;
  availableFrom?: string;
  resumePublic?: boolean;
  blindMode?: boolean;
}

export interface UserInfo {
  id: number;
  name: string;
  phone: string;
  email?: string;
  avatar?: string;
  role: UserRole;
  companyId?: number;
  companyName?: string;
  profile?: UserProfile;
}

interface UserState {
  userInfo: UserInfo | null;
  token: string | null;
  isLogin: boolean;

  setUserInfo: (user: UserInfo) => void;
  setToken: (token: string) => void;
  logout: () => void;
}

const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      userInfo: null,
      token: null,
      isLogin: false,

      setUserInfo: (user) => set({ userInfo: user, isLogin: true }),
      setToken: (token) => set({ token }),
      logout: () => {
        // 断开WebSocket连接
        wsManager.disconnect();
        set({ userInfo: null, token: null, isLogin: false });
      },
    }),
    {
      name: 'lingxi-user-storage',
    },
  ),
);

export default useUserStore;
