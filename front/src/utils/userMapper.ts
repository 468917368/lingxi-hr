import type { LoginResponse, UserInfo as ApiUserInfo } from '@/constants/apiTypes';
import type { UserInfo } from '@/stores/userStore';

/** 将 API 用户信息（apiTypes.UserInfo）映射为 Store 格式（userStore.UserInfo，null→undefined） */
export function mapUserInfoToStore(user: ApiUserInfo): UserInfo {
  return {
    id: user.id,
    name: user.name,
    phone: user.phone,
    role: user.role,
    avatar: user.avatar || undefined,
    email: user.email || undefined,
    companyId: user.companyId ?? user.company?.id,
    companyName: user.company?.name,
    profile: user.profile ? {
      gender: user.profile.gender ?? undefined,
      city: user.profile.city ?? undefined,
      workYears: user.profile.workYears ?? undefined,
      education: user.profile.education ?? undefined,
      jobStatus: user.profile.jobStatus ?? undefined,
      desiredJob: user.profile.desiredJob ?? undefined,
      desiredCity: user.profile.desiredCity ?? undefined,
      desiredSalaryMin: user.profile.desiredSalaryMin ?? undefined,
      desiredSalaryMax: user.profile.desiredSalaryMax ?? undefined,
      availableFrom: user.profile.availableFrom ?? undefined,
      resumePublic: user.profile.resumePublic,
      blindMode: user.profile.blindMode,
    } : undefined,
  };
}

/** 将 LoginResponse 中的用户信息映射为 Store 格式 */
export function mapLoginResponseToUserInfo(response: LoginResponse): UserInfo {
  return mapUserInfoToStore(response.user);
}
