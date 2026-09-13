import React from 'react';
import { useNavigate } from 'umi';
import { Input } from 'antd';
import {
  FileTextOutlined,
  AimOutlined,
  SearchOutlined,
  RobotOutlined,
  RightOutlined,
  ExperimentOutlined,
  ClockCircleOutlined,
  BulbOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import useUserStore from '@/stores/userStore';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const userInfo = useUserStore((s) => s.userInfo);
  const userName = userInfo?.name || '求职者';

  const bentoItems = [
    {
      icon: <FileTextOutlined />,
      label: '简历管理',
      desc: '管理简历，AI诊断匹配度',
      route: ROUTES.CANDIDATE_RESUME_UPLOAD,
      gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      bgTint: 'rgba(102, 126, 234, 0.06)',
    },
    {
      icon: <ExperimentOutlined />,
      label: '模拟面试',
      desc: 'AI模拟真实面试场景',
      route: ROUTES.CANDIDATE_MOCK_INTERVIEW,
      gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
      bgTint: 'rgba(240, 147, 251, 0.06)',
    },
    {
      icon: <ClockCircleOutlined />,
      label: '投递进度',
      desc: '实时追踪投递状态',
      route: ROUTES.CANDIDATE_APPLICATION,
      gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
      bgTint: 'rgba(79, 172, 254, 0.06)',
    },
    {
      icon: <SearchOutlined />,
      label: '岗位探索',
      desc: '发现高匹配度岗位',
      route: ROUTES.CANDIDATE_JOB,
      gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
      bgTint: 'rgba(67, 233, 123, 0.06)',
    },
  ];

  const getTimeGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  };

  return (
    <div className={styles.page}>
      <div className={styles.welcome}>
        <div>
          <h1 className={styles.greeting}>
            {getTimeGreeting()}，<span className={styles.greetingAccent}>{userName}</span>
          </h1>
          <p className={styles.date}>
            <BulbOutlined style={{ marginRight: 6 }} />
            灵犀互聘助你发现理想岗位，开启职业新篇章
          </p>
        </div>
      </div>

      <div className={styles.bannerCard}>
        <div className={styles.bannerDecor1} />
        <div className={styles.bannerDecor2} />
        <div className={styles.bannerLeft}>
          <div className={styles.bannerTitle}>
            <ThunderboltOutlined style={{ marginRight: 8 }} />
            完善个人信息，提高匹配精准度
          </div>
          <div className={styles.bannerDesc}>
            完整填写简历信息后，AI将为你精准推荐合适的岗位，匹配度提升30%
          </div>
        </div>
        <button
          className={styles.bannerBtn}
          onClick={() => navigate(ROUTES.CANDIDATE_RESUME_UPLOAD)}
        >
          完善信息 <RightOutlined />
        </button>
      </div>

      <div className={styles.aiPromo}>
        <div className={styles.aiIconWrap}>
          <RobotOutlined style={{ color: '#fff', fontSize: 32 }} />
        </div>
        <div className={styles.aiInfo}>
          <div className={styles.aiTitle}>AI 求职助手</div>
          <div className={styles.aiDesc}>
            智能推荐岗位、优化简历、模拟面试，7x24小时为你服务
          </div>
        </div>
        <div className={styles.aiInput}>
          <Input.Search
            placeholder="试试对AI说：帮我推荐合适的岗位"
            enterButton="开始对话"
            size="large"
            onSearch={() => navigate(ROUTES.CANDIDATE_AI_ASSISTANT)}
          />
        </div>
      </div>

      <div className={styles.bentoGrid}>
        {bentoItems.map((item) => (
          <div
            key={item.label}
            className={styles.bentoCard}
            onClick={() => navigate(item.route)}
            style={{ '--card-bg-tint': item.bgTint } as React.CSSProperties}
          >
            <div className={styles.bentoIcon} style={{ background: item.gradient }}>
              {React.cloneElement(item.icon, {
                style: { color: '#fff', fontSize: 24 },
              })}
            </div>
            <div className={styles.bentoLabel}>{item.label}</div>
            <div className={styles.bentoHint}>{item.desc}</div>
            <div className={styles.bentoArrow}>
              <RightOutlined />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default HomePage;
