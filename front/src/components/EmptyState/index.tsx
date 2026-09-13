import React from 'react';
import { InboxOutlined } from '@ant-design/icons';
import styles from './index.less';

interface EmptyStateProps {
  icon?: React.ReactNode;
  title?: string;
  description?: string;
  action?: React.ReactNode;
}

const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title = '暂无数据',
  description,
  action,
}) => {
  return (
    <div className={styles.container}>
      <div className={styles.iconWrap}>
        <div className={styles.icon}>
          {icon || <InboxOutlined />}
        </div>
      </div>
      <div className={styles.title}>{title}</div>
      {description && <div className={styles.desc}>{description}</div>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
};

export default EmptyState;
