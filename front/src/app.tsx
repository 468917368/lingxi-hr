import React from 'react';
import { history } from 'umi';
import ErrorBoundary from '@/components/ErrorBoundary';
import { tokenManager } from '@/utils/token';
import useUserStore from '@/stores/userStore';
import type { UserRole } from '@/constants/apiTypes';

export function rootContainer(container: React.ReactNode) {
  return React.createElement(ErrorBoundary, null, container);
}

// 路由守卫
interface RouteChangeEvent {
  location: { pathname: string };
}

// 公开路径（不需要登录）
const publicPaths = ['/login', '/register'];

// 角色与可访问路径前缀的映射
const rolePathMap: Record<UserRole, string[]> = {
  CANDIDATE: ['/candidate'],
  HR: ['/hr', '/certification'],
  INTERVIEWER: ['/interviewer'],
  ADMIN: ['/admin'],
};

// 角色首页
const roleHome: Record<UserRole, string> = {
  CANDIDATE: '/candidate/home',
  HR: '/hr/dashboard',
  INTERVIEWER: '/interviewer/interview',
  ADMIN: '/admin/dashboard',
};

export function onRouteChange({ location }: RouteChangeEvent) {
  const token = tokenManager.getToken();
  const isPublicPath = publicPaths.some((path) => location.pathname.startsWith(path));

  // 一次性读取 store 状态（避免多次 getState）
  const { userInfo, isLogin } = useUserStore.getState();

  // 未登录访问受保护页面 → 跳转登录
  if (!token && !isPublicPath) {
    history.push('/login');
    return;
  }

  // 已登录访问公开页面 → 根据角色跳转到对应首页
  if (token && isPublicPath && isLogin && userInfo) {
    if (userInfo.role === 'HR') {
      history.push(userInfo.companyId ? '/hr/dashboard' : '/certification/pending');
    } else {
      history.push(roleHome[userInfo.role] || '/login');
    }
    return;
  }

  // 有token但未完成注册（isLogin=false）→ 跳转注册页
  // 但排除已经在登录/注册页面的情况，避免死循环
  if (token && !isLogin && !isPublicPath) {
    // 如果store中没有userInfo，说明是残留token，清除并跳转登录
    if (!userInfo) {
      tokenManager.clearAll();
      history.push('/login');
      return;
    }
    history.push('/register');
    return;
  }

  // 角色权限检查
  if (token && isLogin && userInfo && !isPublicPath) {
    const allowedPaths = rolePathMap[userInfo.role] || [];
    const isAllowed = allowedPaths.some((path) => location.pathname.startsWith(path));
    if (!isAllowed) {
      // 无权限，跳转到对应角色的首页
      if (userInfo.role === 'HR') {
        history.push(userInfo.companyId ? '/hr/dashboard' : '/certification/pending');
      } else {
        history.push(roleHome[userInfo.role] || '/login');
      }
    }
  }
}
