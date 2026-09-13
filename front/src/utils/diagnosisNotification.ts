import { history } from 'umi';
import { ROUTES } from '@/constants/routes';
import { requestNotificationPermission, sendDesktopNotification } from './sound';

/**
 * 简历诊断完成桌面通知（QQ 式系统弹窗，独立于前端页面）
 *
 * <p>复用 {@link sound.ts} 的 Notification 封装（与 A 模块消息弹窗同一套实现：
 * favicon 图标、5 秒自动关闭、tag 去重）。本文件只承载诊断场景的差异化逻辑：
 * <ul>
 *   <li>点击通知 → 直达诊断页（消息场景只聚焦窗口，用户自己进会话）</li>
 *   <li>「已见标记」——在线完成（用户在诊断页看到报告）时抑制弹窗，避免重复打扰</li>
 * </ul>
 */

/** 已见标记 key 前缀：diagnosis_notified:{resumeId}:{careerBase64}（与活跃任务 key 同编码约定） */
const NOTIFIED_PREFIX = 'diagnosis_notified:';

const storageKeyOf = (resumeId: string, career: string) =>
  `${NOTIFIED_PREFIX}${resumeId}:${btoa(encodeURIComponent(career))}`;

/** 在用户手势内请求通知授权（首次诊断时调用；已决/不可用则无操作） */
export function ensureNotificationPermission(): void {
  if (!('Notification' in window) || Notification.permission !== 'default') {
    return;
  }
  // 必须在用户手势中调用，否则 Chrome 忽略
  void requestNotificationPermission();
}

/** 标记「已完成且用户已看到」（在线完成/回页查看后调用，抑制系统弹窗） */
export function markDiagnosisNotified(resumeId: string, career: string): void {
  try {
    window.localStorage.setItem(storageKeyOf(resumeId, career), '1');
  } catch {
    // 忽略
  }
}

/** 重置已见标记（发起新诊断时调用，保证下次完成重新提醒） */
export function resetDiagnosisNotified(resumeId: string, career: string): void {
  try {
    window.localStorage.removeItem(storageKeyOf(resumeId, career));
  } catch {
    // 忽略
  }
}

/** 本次完成是否已提醒过/已见 */
export function isDiagnosisNotified(resumeId: string, career: string): boolean {
  try {
    return window.localStorage.getItem(storageKeyOf(resumeId, career)) !== null;
  } catch {
    return false;
  }
}

/** 弹桌面通知（未授权/不支持时静默）；点击通知直达诊断页 */
export function showDiagnosisNotification(opts: { resumeId: string; career: string }): void {
  sendDesktopNotification(
    '简历诊断完成',
    {
      body: `「${opts.career}」诊断报告已生成，点击查看`,
      // 同 tag 通知浏览器只保留一条（去重）
      tag: `diagnosis-${opts.resumeId}-${opts.career}`,
    },
    () => {
      history.push(`${ROUTES.CANDIDATE_RESUME_DIAGNOSIS}/${opts.resumeId}/diagnosis`);
    },
  );
}
