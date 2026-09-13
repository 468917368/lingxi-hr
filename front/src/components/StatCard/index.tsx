import React from 'react';
import styles from './index.less';

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  change?: string;
  changeUp?: boolean;
  onClick?: () => void;
}

const StatCard: React.FC<StatCardProps> = ({ icon, label, value, change, changeUp, onClick }) => {
  return (
    <div className={styles.card} onClick={onClick}>
      <div className={styles.icon}>{icon}</div>
      <div className={styles.value}>{value}</div>
      <div className={styles.label}>{label}</div>
      {change && (
        <div className={`${styles.change} ${changeUp === true ? styles.up : changeUp === false ? styles.down : ''}`}>
          {change}
        </div>
      )}
    </div>
  );
};

export default StatCard;
