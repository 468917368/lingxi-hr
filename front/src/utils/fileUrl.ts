/**
 * 获取完整的文件URL
 * @param url 文件URL（可能是相对路径或绝对路径）
 * @returns 完整的文件URL
 */
export function getFullFileUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;

  // 如果已经是完整URL，直接返回
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }

  // 如果是相对路径，添加当前域名前缀
  if (url.startsWith('/')) {
    return url;
  }

  // 其他情况，添加/files前缀
  return `/files/${url}`;
}

/**
 * 获取头像URL
 * @param avatar 头像URL
 * @returns 完整的头像URL
 */
export function getAvatarUrl(avatar: string | null | undefined): string | undefined {
  return getFullFileUrl(avatar);
}
