import React, { useCallback, useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'umi';
import { BellOutlined, UserOutlined, MessageOutlined, SafetyOutlined, StarOutlined } from '@ant-design/icons';
import { Badge, Dropdown, Avatar, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { ROUTES } from '@/constants/routes';
import { useAuth } from '@/hooks/useAuth';
import useUserStore from '@/stores/userStore';
import { useUnreadCount } from '@/hooks/useNotification';
import { useWebSocketConnect } from '@/hooks/useWebSocket';
import { getAvatarUrl } from '@/utils/fileUrl';
import useMessageStore from '@/stores/messageStore';
import * as messageService from '@/services/message';
import {
  getDiagnosisTaskStatus,
  getParseTaskStatus,
  PENDING_DIAGNOSIS_PREFIX,
  PENDING_PARSE_PREFIX,
} from '@/services/resume';
import type { DiagnosisTaskStatus, ParseTaskStatus } from '@/services/resume';
import {
  showDiagnosisNotification,
  isDiagnosisNotified,
  markDiagnosisNotified,
} from '@/utils/diagnosisNotification';
import {
  showParseNotification,
  isParseNotified,
  markParseNotified,
} from '@/utils/parseNotification';
import styles from './CandidateLayout.less';

/** 方案C：导航栏诊断提示。activeTask 非空时「简历管理」菜单显示彩点（橙=进行中 / 绿=已完成） */
interface PendingDiagnosisHint {
  resumeId: string;
  career: string;
  state: 'RUNNING' | 'COMPLETED';
}

/** 解析任务提示：同理，「简历管理」菜单彩点（橙=解析中 / 绿=已完成） */
interface PendingParseHint {
  resumeId: string;
  state: 'RUNNING' | 'COMPLETED';
}

/** 导航栏诊断提示刷新间隔（RUNNING 期间每 10s 探测一次完成） */
const DIAG_HINT_POLL_MS = 10_000;

const menuItems = [
  { key: ROUTES.CANDIDATE_HOME, label: '首页', icon: '🏠' },
  { key: ROUTES.CANDIDATE_JOB, label: '岗位探索', icon: '💼' },
  { key: ROUTES.CANDIDATE_RESUME_UPLOAD, label: '简历管理', icon: '📋' },
  { key: ROUTES.CANDIDATE_APPLICATION, label: '投递进度', icon: '📊' },
  { key: ROUTES.CANDIDATE_AI_ASSISTANT, label: 'AI求职助手', icon: '🤖' },
  { key: ROUTES.CANDIDATE_MOCK_INTERVIEW, label: '模拟面试', icon: '🎯' },
  { key: ROUTES.CANDIDATE_MESSAGE, label: '消息沟通', icon: '💬' },
  { key: ROUTES.CANDIDATE_NOTIFICATION, label: '消息通知', icon: '🔔' },
];

const CandidateLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { userInfo, logout } = useAuth();
  const { isLogin } = useUserStore();
  const { unreadCount } = useUnreadCount();
  const conversations = useMessageStore((s) => s.conversations);
  const setConversations = useMessageStore((s) => s.setConversations);

  // 消息未读总数
  const messageUnreadCount = conversations.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  // ==================== 方案C：诊断任务导航栏提示 ====================

  const [diagHint, setDiagHint] = useState<PendingDiagnosisHint | null>(null);
  const [parseHint, setParseHint] = useState<PendingParseHint | null>(null);

  /** 扫描 localStorage 活跃诊断任务 → 调 status 接口确认 → 更新提示（FAILED/NONE 清理残留） */
  const checkDiagnosisPending = useCallback(async () => {
    const pending: { resumeId: string; career: string }[] = [];
    try {
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i);
        if (!key?.startsWith(PENDING_DIAGNOSIS_PREFIX)) continue;
        const parts = key.split(':');
        if (parts.length !== 3) continue;
        let careerText: string | null = null;
        try {
          careerText = decodeURIComponent(atob(parts[2]));
        } catch {
          continue;
        }
        if (careerText) {
          pending.push({ resumeId: parts[1], career: careerText });
        }
      }
    } catch {
      return; // localStorage 不可用：跳过提示
    }
    if (pending.length === 0) {
      setDiagHint(null);
      return;
    }
    for (const p of pending) {
      let status: DiagnosisTaskStatus | null = null;
      try {
        status = await getDiagnosisTaskStatus(p.resumeId, p.career);
      } catch {
        continue; // 单次失败不影响其余
      }
      if (status.state === 'RUNNING') {
        setDiagHint({ resumeId: p.resumeId, career: p.career, state: 'RUNNING' });
        return;
      }
      if (status.state === 'COMPLETED') {
        setDiagHint({ resumeId: p.resumeId, career: p.career, state: 'COMPLETED' });
        // QQ 式系统通知：后台完成弹窗提醒（已见/已弹过不重复；未授权静默）
        if (!isDiagnosisNotified(p.resumeId, p.career)) {
          showDiagnosisNotification(p);
          markDiagnosisNotified(p.resumeId, p.career);
        }
        return;
      }
      if (status.state === 'FAILED') {
        // 失败无提示意义，清除残留
        try {
          window.localStorage.removeItem(
            `${PENDING_DIAGNOSIS_PREFIX}${p.resumeId}:${btoa(encodeURIComponent(p.career))}`,
          );
        } catch {
          // 忽略
        }
      }
      // NONE：不删 key——诊断刚提交的瞬间扫描可能读到 NONE（后端状态未写入的
      // 竞态窗口），删除会导致提示永久丢失；残留 key 由诊断页进入时自行清理
    }
    setDiagHint(null);
  }, []);

  /** 扫描 localStorage 活跃解析任务 → 调 parse-status 确认 → 弹完成/失败通知 + 更新提示 */
  const checkParsePending = useCallback(async () => {
    const pendingIds: string[] = [];
    try {
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i);
        if (!key?.startsWith(PENDING_PARSE_PREFIX)) continue;
        const parts = key.split(':');
        if (parts.length === 2 && parts[1]) {
          pendingIds.push(parts[1]);
        }
      }
    } catch {
      return; // localStorage 不可用：跳过提示
    }
    if (pendingIds.length === 0) {
      setParseHint(null);
      return;
    }
    for (const rid of pendingIds) {
      let status: ParseTaskStatus | null = null;
      try {
        status = await getParseTaskStatus(rid);
      } catch {
        continue; // 单次失败不影响其余
      }
      if (status.state === 'RUNNING') {
        setParseHint({ resumeId: rid, state: 'RUNNING' });
        return;
      }
      if (status.state === 'COMPLETED') {
        setParseHint({ resumeId: rid, state: 'COMPLETED' });
        // QQ 式系统通知：后台完成弹窗提醒（已弹过不重复；未授权静默）
        if (!isParseNotified(rid)) {
          showParseNotification(rid, false);
          markParseNotified(rid);
        }
        return;
      }
      if (status.state === 'FAILED') {
        // 解析失败保留在列表需手动删除——弹通知引导回来处理，随后清除残留
        if (!isParseNotified(rid)) {
          showParseNotification(rid, true);
          markParseNotified(rid);
        }
        try {
          window.localStorage.removeItem(`${PENDING_PARSE_PREFIX}${rid}`);
        } catch {
          // 忽略
        }
        continue;
      }
      // NONE：解析未触发/状态已过期（不删 key——上传页刷新/回页时会自行清理或重写）
    }
    setParseHint(null);
  }, []);

  useEffect(() => {
    if (!isLogin) return;
    checkDiagnosisPending();
    checkParsePending();
    // 窗口聚焦时刷新（跳页回来立即感知）；RUNNING 期间定时探测完成
    const onFocus = () => {
      checkDiagnosisPending();
      checkParsePending();
    };
    window.addEventListener('focus', onFocus);
    const timer = window.setInterval(onFocus, DIAG_HINT_POLL_MS);
    return () => {
      window.removeEventListener('focus', onFocus);
      window.clearInterval(timer);
    };
  }, [isLogin, checkDiagnosisPending, checkParsePending]);

  /** 点击提示：诊断 → 诊断页看报告；解析 → 简历管理页看列表 */
  const handleResumeHintClick = () => {
    if (diagHint) {
      navigate(`${ROUTES.CANDIDATE_RESUME_DIAGNOSIS}/${diagHint.resumeId}/diagnosis`);
      setDiagHint(null);
    } else if (parseHint) {
      navigate(ROUTES.CANDIDATE_RESUME_UPLOAD);
      setParseHint(null);
    }
  };

  // 连接WebSocket获取实时通知
  useWebSocketConnect();

  // 加载会话列表（获取未读数）
  useEffect(() => {
    if (isLogin) {
      messageService.getConversations().then((data) => {
        setConversations(Array.isArray(data) ? data : []);
      }).catch(() => {});
    }
  }, [isLogin, setConversations]);

  const isActive = (path: string) => {
    if (path === ROUTES.CANDIDATE_HOME) {
      return location.pathname === path;
    }
    return location.pathname.startsWith(path);
  };

  // 「简历管理」提示：诊断优先（用户操作更近），无诊断时显示解析提示
  const resumeHint = diagHint ?? parseHint;
  const resumeHintTooltip = diagHint
    ? diagHint.state === 'RUNNING'
      ? `「${diagHint.career}」诊断进行中，完成前请勿重复提交`
      : `「${diagHint.career}」诊断报告已生成，点击查看`
    : parseHint
      ? parseHint.state === 'RUNNING'
        ? '简历解析中，完成前请勿重复上传'
        : '简历解析已完成，点击查看'
      : '';

  const userMenuItems: MenuProps['items'] = [
    { key: 'favorites', label: '我的收藏', icon: <StarOutlined />, onClick: () => navigate(ROUTES.CANDIDATE_FAVORITES) },
    { key: 'profile', label: '个人中心', icon: <UserOutlined />, onClick: () => navigate(ROUTES.CANDIDATE_PROFILE) },
    { key: 'security', label: '账号安全', icon: <SafetyOutlined />, onClick: () => navigate(ROUTES.CANDIDATE_SECURITY) },
    { type: 'divider' },
    { key: 'logout', label: '退出登录', onClick: logout },
  ];

  return (
    <div className={`c-mode c-page ${styles.layout}`}>
      {/* 顶部导航 */}
      <header className={styles.topbar}>
        <div className={styles.brand} onClick={() => navigate(ROUTES.CANDIDATE_HOME)}>
          <div className={styles.brandMark}>L</div>
          <span className={styles.brandText}>灵犀互聘</span>
        </div>

        <nav className={styles.nav}>
          {menuItems.map((item) => (
            <button
              key={item.key}
              className={`${styles.navItem} ${isActive(item.key) ? styles.active : ''}`}
              onClick={() =>
                item.key === ROUTES.CANDIDATE_RESUME_UPLOAD && resumeHint
                  ? handleResumeHintClick()
                  : navigate(item.key)
              }
            >
              {item.key === ROUTES.CANDIDATE_RESUME_UPLOAD && resumeHint ? (
                <Tooltip title={resumeHintTooltip} placement="bottom">
                  <Badge
                    dot
                    color={resumeHint.state === 'RUNNING' ? 'orange' : 'green'}
                    offset={[4, -2]}
                  >
                    <span className={styles.navIcon}>{item.icon}</span>
                  </Badge>
                </Tooltip>
              ) : item.key === ROUTES.CANDIDATE_MESSAGE && messageUnreadCount > 0 ? (
                <Badge count={messageUnreadCount} size="small" offset={[4, -2]}>
                  <span className={styles.navIcon}>{item.icon}</span>
                </Badge>
              ) : (
                <span className={styles.navIcon}>{item.icon}</span>
              )}
              {item.label}
            </button>
          ))}
        </nav>

        <div className={styles.actions}>
          {isLogin ? (
            <>
              <Badge count={unreadCount.totalCount} size="small">
                <button
                  className={styles.iconBtn}
                  onClick={() => navigate(ROUTES.CANDIDATE_NOTIFICATION)}
                >
                  <BellOutlined />
                </button>
              </Badge>
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                <button className={styles.avatarBtn}>
                  <Avatar
                    size={32}
                    src={getAvatarUrl(userInfo?.avatar)}
                    icon={<UserOutlined />}
                  />
                  <span className={styles.userName}>{userInfo?.name || '用户'}</span>
                </button>
              </Dropdown>
            </>
          ) : (
            <button className={styles.loginBtn} onClick={() => navigate(ROUTES.LOGIN)}>
              登录
            </button>
          )}
        </div>
      </header>

      {/* 内容区 */}
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
};

export default CandidateLayout;
