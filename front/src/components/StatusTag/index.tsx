import React from 'react';
import styles from './index.less';

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary';

interface StatusTagProps {
  type: TagType;
  children: React.ReactNode;
}

const StatusTag: React.FC<StatusTagProps> = ({ type, children }) => {
  return <span className={`${styles.tag} ${styles[type]}`}>{children}</span>;
};

export default StatusTag;
