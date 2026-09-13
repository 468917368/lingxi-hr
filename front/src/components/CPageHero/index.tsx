import React from 'react';
import { Button } from 'antd';
import styles from './index.less';

export interface CPageHeroChip {
  key: string;
  label: string;
}

interface CPageHeroProps {
  title: string;
  desc?: string;
  /** 右侧内容（统计 / 操作按钮 / 徽标） */
  extra?: React.ReactNode;
  /** 激活筛选条件 chips（可选） */
  chips?: CPageHeroChip[];
  /** 「清除筛选」回调（可选，传了才显示清除按钮） */
  onClearChips?: () => void;
}

/**
 * C端页面 Hero 头部（Coral Red 主题）
 *
 * 渐变珊瑚色背景 + 装饰光球 + 网格纹理 + 大标题 + 右侧 extra。
 * 用于 C端各页面顶部，提升视觉层次。
 */
const CPageHero: React.FC<CPageHeroProps> = ({ title, desc, extra, chips = [], onClearChips }) => {
  return (
    <div className={styles.hero}>
      <div className={styles.heroDecor} />
      <div className={styles.heroGrid} />
      <div className={styles.heroInner}>
        <div className={styles.heroLeft}>
          <div className={styles.heroTitle}>{title}</div>
          {desc && <div className={styles.heroDesc}>{desc}</div>}
          {chips.length > 0 && (
            <div className={styles.heroChips}>
              {chips.map((chip) => (
                <span key={chip.key} className={styles.heroChip}>{chip.label}</span>
              ))}
              {onClearChips && (
                <Button type="link" size="small" className={styles.heroClear} onClick={onClearChips}>
                  清除筛选
                </Button>
              )}
            </div>
          )}
        </div>
        {extra && <div className={styles.heroRight}>{extra}</div>}
      </div>
    </div>
  );
};

export default CPageHero;
