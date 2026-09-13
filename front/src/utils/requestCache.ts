/**
 * 简单的请求缓存工具
 * 用于缓存不经常变化的数据，减少重复请求
 */

interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

class RequestCache {
  private cache = new Map<string, CacheEntry<any>>();
  private defaultTTL = 30 * 1000; // 30秒默认缓存

  /**
   * 获取缓存数据
   * @param key 缓存键
   * @param ttl 缓存时间（毫秒）
   * @returns 缓存数据或 null
   */
  get<T>(key: string, ttl?: number): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    const maxAge = ttl ?? this.defaultTTL;
    if (Date.now() - entry.timestamp > maxAge) {
      this.cache.delete(key);
      return null;
    }

    return entry.data as T;
  }

  /**
   * 设置缓存数据
   * @param key 缓存键
   * @param data 缓存数据
   */
  set<T>(key: string, data: T): void {
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
    });
  }

  /**
   * 删除缓存
   * @param key 缓存键
   */
  delete(key: string): void {
    this.cache.delete(key);
  }

  /**
   * 清除所有缓存
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * 清除过期缓存
   */
  cleanup(): void {
    const now = Date.now();
    for (const [key, entry] of this.cache.entries()) {
      if (now - entry.timestamp > this.defaultTTL) {
        this.cache.delete(key);
      }
    }
  }
}

export const requestCache = new RequestCache();

/**
 * 带缓存的请求包装器
 * @param key 缓存键
 * @param fetcher 请求函数
 * @param ttl 缓存时间（毫秒）
 * @returns 请求结果
 */
export async function cachedRequest<T>(
  key: string,
  fetcher: () => Promise<T>,
  ttl?: number,
): Promise<T> {
  const cached = requestCache.get<T>(key, ttl);
  if (cached) {
    return cached;
  }

  const data = await fetcher();
  requestCache.set(key, data);
  return data;
}
