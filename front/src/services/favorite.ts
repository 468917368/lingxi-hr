import request from '@/utils/request';
import type { PageResult } from '@/constants/apiTypes';

// ================================================================
// C 端岗位收藏 — 对齐后端 lingxi-job FavoriteController（阶段6.2）
// 仅三个接口：设定收藏状态 / 收藏 ID 批量回显 / 我的收藏分页
// ================================================================

/** 设定收藏状态响应 */
export interface FavoriteResponse {
  /** 岗位ID */
  jobId: number;
  /** 目标收藏状态 */
  favorited: boolean;
}

/** 我的收藏列表项（严格对齐后端 FavoriteJobVO；扁平字段，不含 skillTags/recommendScore） */
export interface FavoriteJobItem {
  jobId: number;
  title: string | null;
  companyName: string | null;
  industryGroupCode: string | null;
  industryCode: string | null;
  industryName: string | null;
  cityCode: string | null;
  cityName: string | null;
  salaryMinAmount: number | null;
  salaryMaxAmount: number | null;
  salaryCurrency: string | null;
  salaryPeriod: string | null;
  salaryMonths: number | null;
  /** 是否面议（0/1 标志，展示层归一为 boolean） */
  salaryNegotiable: number | null;
  salaryRawText: string | null;
  minExperienceYears: number | null;
  educationRequirement: string | null;
  publishedAt: string | null;
  /** 收藏时间 */
  favoritedAt: string;
  /** 岗位已非 PUBLISHED（不允许跳详情，可取消收藏） */
  isOffline: boolean;
  /** 岗位已逻辑删除或物理缺失（不允许跳详情，可取消收藏） */
  deleted: boolean;
}

/** 我的收藏分页参数 */
export interface FavoriteListParams {
  /** 页码（1~100） */
  page?: number;
  /** 每页条数（1~50） */
  size?: number;
}

/** 设定收藏状态（目标状态幂等；favorited=true 对已下线岗位返回 2101，false 允许取消任何历史收藏） */
export async function setJobFavorite(jobId: number, favorited: boolean): Promise<FavoriteResponse> {
  return request.post(`/v1/jobs/${jobId}/favorite`, { favorited });
}

/** 收藏 ID 批量回显（已登录候选人调用） */
export async function getFavoriteIds(): Promise<number[]> {
  return request.get('/v1/favorites/ids');
}

/** 我的收藏分页列表 */
export async function getFavorites(params: FavoriteListParams): Promise<PageResult<FavoriteJobItem>> {
  return request.get('/v1/favorites', { params });
}
