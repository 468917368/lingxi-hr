import type { UserRole } from '@/constants/enums';

/**
 * 检查角色是否拥有指定权限
 */
export function hasPermission(userRole: UserRole | undefined, requiredRole: UserRole): boolean {
  if (!userRole) return false;

  // Admin 拥有所有权限
  if (userRole === 'ADMIN' as UserRole) return true;

  return userRole === requiredRole;
}

/**
 * 根据路由判断对应的模式class
 */
export function getModeClass(pathname: string): string {
  if (pathname.startsWith('/admin')) return 'a-mode';
  if (pathname.startsWith('/candidate')) return 'c-mode';
  if (pathname.startsWith('/hr')) return 'b-mode';
  if (pathname.startsWith('/interviewer')) return 'b-mode interviewer-role';
  return '';
}
