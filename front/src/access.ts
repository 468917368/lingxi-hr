import type { UserRole } from '@/constants/apiTypes';

interface UserInfo {
  id: number;
  name: string;
  role: UserRole;
  companyId?: number;
}

export default function access(initialState: { currentUser?: UserInfo }) {
  const { currentUser } = initialState || {};

  return {
    // 基础角色判断
    canCandidate: currentUser?.role === 'CANDIDATE',
    canHR: currentUser?.role === 'HR',
    canInterviewer: currentUser?.role === 'INTERVIEWER',
    canAdmin: currentUser?.role === 'ADMIN',

    // 组合权限
    canHROrAdmin: currentUser?.role === 'HR' || currentUser?.role === 'ADMIN',
    canInterviewerOrHR: currentUser?.role === 'INTERVIEWER' || currentUser?.role === 'HR',
    canBEnd: currentUser?.role === 'HR' || currentUser?.role === 'INTERVIEWER',
    canAll: !!currentUser,  // 已登录即可
  };
}
