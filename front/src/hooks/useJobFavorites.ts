import { useCallback, useEffect, useRef, useState } from 'react';
import { getFavoriteIds, setJobFavorite } from '@/services/favorite';

interface UseJobFavoritesResult {
  /** 已收藏岗位 ID 集合（不可变更新） */
  favoriteIds: Set<number>;
  /** 收藏 ID 批量加载中 */
  idsLoading: boolean;
  /** 正在切换收藏的单卡片集合（允许多卡片并发，独立 loading） */
  togglingIds: Set<number>;
  /**
   * 切换目标收藏状态。
   * 返回值语义：成功 true；同一 jobId 在途拦截或请求失败 false（错误提示沿用 request.ts，调用方不重复弹错）。
   */
  toggleFavorite: (jobId: number, targetFavorited: boolean) => Promise<boolean>;
  /** 重新拉取收藏 ID 集合 */
  refreshFavoriteIds: () => Promise<void>;
}

/**
 * 页面级收藏状态（不引入全局 store）。
 * - enabled=false：清空本地集合且不请求收藏 ID 接口（未登录/非候选人场景）。
 * - 竞态：ID 加载与 toggle 共用递增请求序号，toggle 成功后递增序号使在途的 ID 加载响应过期，
 *   避免「初始 ID 慢响应覆盖用户刚完成的收藏」。
 */
export function useJobFavorites(enabled: boolean): UseJobFavoritesResult {
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());
  const [idsLoading, setIdsLoading] = useState(false);
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set());

  // 递增请求序号：ID 加载 与 toggle 共用，任意成功的最新操作使旧请求数据过期
  const seqRef = useRef(0);
  // 在途 toggle 的 jobId 集合（防同一 jobId 重复请求）
  const inflightRef = useRef<Set<number>>(new Set());

  const refreshFavoriteIds = useCallback(async () => {
    const seq = ++seqRef.current;
    setIdsLoading(true);
    try {
      const ids = await getFavoriteIds();
      // 过期响应丢弃（期间有更新的请求/toggle 成功）
      if (seq !== seqRef.current) return;
      setFavoriteIds(new Set(ids));
    } catch {
      // 拦截器已提示，Hook 不重复弹窗
    } finally {
      setIdsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (enabled) {
      refreshFavoriteIds();
    } else {
      // 关闭门控：使在途请求过期 + 清空集合，不请求接口
      seqRef.current++;
      setFavoriteIds(new Set());
      setIdsLoading(false);
    }
  }, [enabled, refreshFavoriteIds]);

  const toggleFavorite = useCallback(
    async (jobId: number, targetFavorited: boolean): Promise<boolean> => {
      if (inflightRef.current.has(jobId)) return false;
      inflightRef.current.add(jobId);
      setTogglingIds((prev) => {
        const next = new Set(prev);
        next.add(jobId);
        return next;
      });
      try {
        await setJobFavorite(jobId, targetFavorited);
        // 成功：递增序号使在途 ID 加载响应过期，避免旧快照覆盖本次收藏
        seqRef.current++;
        setFavoriteIds((prev) => {
          const next = new Set(prev);
          if (targetFavorited) next.add(jobId);
          else next.delete(jobId);
          return next;
        });
        return true;
      } catch {
        // 拦截器已提示，不乐观修改集合
        return false;
      } finally {
        inflightRef.current.delete(jobId);
        setTogglingIds((prev) => {
          const next = new Set(prev);
          next.delete(jobId);
          return next;
        });
      }
    },
    [],
  );

  return { favoriteIds, idsLoading, togglingIds, toggleFavorite, refreshFavoriteIds };
}
