import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'umi';
import styles from './index.less';

interface ToastMessage {
  id: string;
  senderName: string;
  senderAvatar?: string | null;
  content: string;
  conversationId: string;
  timestamp: number;
}

interface MessageToastProps {
  /** 消息点击跳转路径前缀 */
  messagePath: string;
}

/** 消息浮动通知组件（HTTP环境降级方案） */
const MessageToast: React.FC<MessageToastProps> = ({ messagePath }) => {
  const navigate = useNavigate();
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  /** 添加通知 */
  const addToast = useCallback((msg: Omit<ToastMessage, 'id' | 'timestamp'>) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const newToast: ToastMessage = {
      ...msg,
      id,
      timestamp: Date.now(),
    };

    setToasts((prev) => {
      // 最多显示3个通知
      const updated = [newToast, ...prev].slice(0, 3);
      return updated;
    });

    // 5秒后自动移除
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 5000);
  }, []);

  /** 点击通知跳转 */
  const handleClick = useCallback(
    (conversationId: string, toastId: string) => {
      navigate(`${messagePath}?conversationId=${conversationId}`);
      setToasts((prev) => prev.filter((t) => t.id !== toastId));
    },
    [navigate, messagePath],
  );

  /** 关闭通知 */
  const handleClose = useCallback((toastId: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== toastId));
  }, []);

  // 暴露addToast方法到全局
  useEffect(() => {
    (window as any).__messageToast = addToast;
    return () => {
      delete (window as any).__messageToast;
    };
  }, [addToast]);

  if (toasts.length === 0) return null;

  return (
    <div className={styles.container}>
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={styles.toast}
          onClick={() => handleClick(toast.conversationId, toast.id)}
        >
          <div className={styles.avatar}>
            {toast.senderAvatar ? (
              <img src={toast.senderAvatar} alt={toast.senderName} />
            ) : (
              <span>{toast.senderName?.charAt(0) || '?'}</span>
            )}
          </div>
          <div className={styles.content}>
            <div className={styles.senderName}>{toast.senderName}</div>
            <div className={styles.message}>{toast.content}</div>
          </div>
          <button
            className={styles.closeBtn}
            onClick={(e) => {
              e.stopPropagation();
              handleClose(toast.id);
            }}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
};

/**
 * 触发消息通知（供其他组件调用）
 */
export function showMessageToast(msg: {
  senderName: string;
  senderAvatar?: string | null;
  content: string;
  conversationId: string;
}) {
  const addToast = (window as any).__messageToast;
  if (addToast) {
    addToast(msg);
  }
}

export default MessageToast;
