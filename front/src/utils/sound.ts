/**
 * 声音和通知工具类
 */

// 消息提示音（使用AudioContext生成简单的提示音）
let audioContext: AudioContext | null = null;

function getAudioContext(): AudioContext {
  if (!audioContext) {
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
  }
  return audioContext;
}

/**
 * 播放消息提示音
 */
export function playMessageSound() {
  try {
    const ctx = getAudioContext();
    const oscillator = ctx.createOscillator();
    const gainNode = ctx.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(ctx.destination);

    // 设置音调和音量
    oscillator.frequency.value = 800; // 频率
    oscillator.type = 'sine'; // 正弦波

    // 音量渐变
    gainNode.gain.setValueAtTime(0.3, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.3);

    // 播放
    oscillator.start(ctx.currentTime);
    oscillator.stop(ctx.currentTime + 0.3);
  } catch (error) {
    console.warn('[Sound] Failed to play message sound:', error);
  }
}

/**
 * 播放通知提示音
 */
export function playNotificationSound() {
  try {
    const ctx = getAudioContext();
    const oscillator = ctx.createOscillator();
    const gainNode = ctx.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(ctx.destination);

    // 设置音调和音量
    oscillator.frequency.value = 600;
    oscillator.type = 'sine';

    // 音量渐变
    gainNode.gain.setValueAtTime(0.2, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5);

    // 播放
    oscillator.start(ctx.currentTime);
    oscillator.stop(ctx.currentTime + 0.5);
  } catch (error) {
    console.warn('[Sound] Failed to play notification sound:', error);
  }
}

/**
 * 检查是否支持音频
 */
export function isAudioSupported(): boolean {
  return !!(window.AudioContext || (window as any).webkitAudioContext);
}

/**
 * 检查是否在安全上下文中（HTTPS或localhost）
 */
function isSecureContext(): boolean {
  return window.isSecureContext || location.hostname === 'localhost' || location.hostname === '127.0.0.1';
}

/**
 * 请求桌面通知权限
 */
export async function requestNotificationPermission(): Promise<boolean> {
  if (!('Notification' in window)) {
    console.warn('[Notification] Browser does not support notifications');
    return false;
  }

  // 检查安全上下文（HTTPS或localhost）
  if (!isSecureContext()) {
    console.warn('[Notification] Not in secure context (HTTPS required for notifications)');
    console.warn('[Notification] Current protocol:', location.protocol);
    console.warn('[Notification] Current hostname:', location.hostname);
    return false;
  }

  if (Notification.permission === 'granted') {
    return true;
  }

  if (Notification.permission === 'denied') {
    console.warn('[Notification] Permission denied by user');
    return false;
  }

  try {
    const permission = await Notification.requestPermission();
    return permission === 'granted';
  } catch (error) {
    console.error('[Notification] Failed to request permission:', error);
    return false;
  }
}

/**
 * 发送桌面通知
 *
 * @param onClick 点击回调（如跳转页面）；缺省时仅聚焦窗口。
 *                消息场景不传（用户自己进会话看），诊断等场景传导航跳转。
 */
export function sendDesktopNotification(
  title: string,
  options?: NotificationOptions,
  onClick?: () => void,
) {
  // 检查浏览器支持
  if (!('Notification' in window)) {
    console.warn('[Notification] Browser does not support notifications');
    return;
  }

  // 检查权限
  if (Notification.permission !== 'granted') {
    console.warn('[Notification] Permission not granted, current:', Notification.permission);
    return;
  }

  // 检查安全上下文
  if (!isSecureContext()) {
    console.warn('[Notification] Not in secure context, skipping notification');
    return;
  }

  try {
    const notification = new Notification(title, {
      icon: '/favicon.ico',
      badge: '/favicon.ico',
      ...options,
    });

    // 点击通知：聚焦窗口 + 执行自定义跳转（如直达诊断页）
    notification.onclick = () => {
      window.focus();
      if (onClick) {
        onClick();
      }
      notification.close();
    };

    // 5秒后自动关闭
    setTimeout(() => notification.close(), 5000);
  } catch (error) {
    console.error('[Notification] Failed to send notification:', error);
  }
}

/**
 * 检查是否支持桌面通知
 */
export function isNotificationSupported(): boolean {
  return 'Notification' in window && isSecureContext();
}

/**
 * 获取通知权限状态
 */
export function getNotificationPermission(): NotificationPermission | 'unsupported' | 'insecure' {
  if (!('Notification' in window)) {
    return 'unsupported';
  }
  if (!isSecureContext()) {
    return 'insecure';
  }
  return Notification.permission;
}

/**
 * 页面标题闪烁提醒（当桌面通知不可用时的降级方案）
 */
let titleTimer: ReturnType<typeof setInterval> | null = null;
let originalTitle = document.title;

export function flashTitle(message: string) {
  // 保存原始标题（首次调用时）
  if (!titleTimer) {
    originalTitle = document.title;
  }

  let isOriginal = true;
  titleTimer = setInterval(() => {
    document.title = isOriginal ? `【新消息】${message}` : originalTitle;
    isOriginal = !isOriginal;
  }, 1000);

  // 点击页面或回到页面时停止闪烁
  const stopFlash = () => {
    if (titleTimer) {
      clearInterval(titleTimer);
      titleTimer = null;
      document.title = originalTitle;
    }
  };

  // 移除旧监听器，添加新监听器
  const onVisibilityChange = () => {
    if (!document.hidden) stopFlash();
  };
  document.addEventListener('visibilitychange', onVisibilityChange, { once: true });
  document.addEventListener('click', stopFlash, { once: true });
}

/**
 * 停止标题闪烁（手动调用）
 */
export function stopFlashTitle() {
  if (titleTimer) {
    clearInterval(titleTimer);
    titleTimer = null;
    document.title = originalTitle;
  }
}

/**
 * favicon角标提醒（在favicon上显示未读数）
 */
let badgeCanvas: HTMLCanvasElement | null = null;

export function setFaviconBadge(count: number) {
  // 获取当前favicon
  const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]') ||
               document.querySelector<HTMLLinkElement>('link[rel="shortcut icon"]');
  const originalHref = link?.href || '/favicon.ico';

  if (count <= 0) {
    // 清除角标，恢复原始favicon
    if (link) link.href = originalHref;
    return;
  }

  // 创建canvas绘制角标
  if (!badgeCanvas) {
    badgeCanvas = document.createElement('canvas');
    badgeCanvas.width = 32;
    badgeCanvas.height = 32;
  }

  const canvas = badgeCanvas;
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  // 加载原始favicon
  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.onload = () => {
    // 绘制原始favicon
    ctx.clearRect(0, 0, 32, 32);
    ctx.drawImage(img, 0, 0, 32, 32);

    // 绘制红色角标
    const badgeSize = 14;
    const badgeX = 32 - badgeSize;
    const badgeY = 0;

    // 红色圆形背景
    ctx.beginPath();
    ctx.arc(badgeX + badgeSize / 2, badgeY + badgeSize / 2, badgeSize / 2, 0, Math.PI * 2);
    ctx.fillStyle = '#FF4D4F';
    ctx.fill();

    // 白色数字
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 10px Arial';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(count > 99 ? '99+' : String(count), badgeX + badgeSize / 2, badgeY + badgeSize / 2);

    // 更新favicon
    if (link) {
      link.href = canvas.toDataURL('image/png');
    }
  };
  img.src = originalHref;
}

/**
 * 清除favicon角标
 */
export function clearFaviconBadge() {
  setFaviconBadge(0);
}
