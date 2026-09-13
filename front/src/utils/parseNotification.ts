import { history } from 'umi';
import { ROUTES } from '@/constants/routes';
import { sendDesktopNotification } from './sound';

/**
 * 简历解析完成/失败桌面通知（QQ 式系统弹窗，独立于前端页面）
 *
 * <p>复用 {@link sound.ts} 的 Notification 封装（与 A 模块消息弹窗、诊断完成通知同一套
 * 实现：favicon 图标、5 秒自动关闭、tag 去重）。解析场景的差异化逻辑：
 * <ul>
 *   <li>点击通知 → 直达简历管理页（解析结果在列表/详情，无独立结果页）</li>
 *   <li>「已提醒标记」——弹窗后记录，避免 CandidateLayout 每 10s 扫描重复打扰；
 *       发起新解析时重置（resetParseNotified），保证下次完成重新提醒</li>
 * </ul>
 */

/** 已提醒标记 key 前缀：parse_notified:{resumeId}（与活跃任务 key 同编码约定） */
const NOTIFIED_PREFIX = 'parse_notified:';

const storageKeyOf = (resumeId: string) => `${NOTIFIED_PREFIX}${resumeId}`;

/** 标记「已完成且已提醒过」（弹窗后调用，防止重复打扰） */
export function markParseNotified(resumeId: string): void {
  try {
    window.localStorage.setItem(storageKeyOf(resumeId), '1');
  } catch {
    // 忽略
  }
}

/** 重置已提醒标记（发起新解析时调用，保证下次完成重新提醒） */
export function resetParseNotified(resumeId: string): void {
  try {
    window.localStorage.removeItem(storageKeyOf(resumeId));
  } catch {
    // 忽略
  }
}

/** 本次完成/失败是否已提醒过 */
export function isParseNotified(resumeId: string): boolean {
  try {
    return window.localStorage.getItem(storageKeyOf(resumeId)) !== null;
  } catch {
    return false;
  }
}

/**
 * 弹桌面通知（未授权/不支持时静默）；点击通知直达简历管理页
 *
 * @param failed true=解析失败（引导回来处理：失败记录保留在列表，需手动删除）
 */
export function showParseNotification(resumeId: string, failed: boolean): void {
  sendDesktopNotification(
    failed ? '简历解析失败' : '简历解析完成',
    {
      body: failed ? '简历解析失败，可删除后重新上传' : '简历解析已完成，点击查看',
      // 同 tag 通知浏览器只保留一条（去重）
      tag: `parse-${resumeId}`,
    },
    () => {
      history.push(ROUTES.CANDIDATE_RESUME_UPLOAD);
    },
  );
}
