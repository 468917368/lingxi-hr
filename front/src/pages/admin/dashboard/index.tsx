import React, { useState, useEffect, useCallback } from 'react';
import { Table, Spin, message } from 'antd';
import {
  UserOutlined,
  SafetyOutlined,
  FileTextOutlined,
  SendOutlined,
} from '@ant-design/icons';
import PageHeader from '@/components/PageHeader';
import StatCard from '@/components/StatCard';
import {
  getAdminStats,
  getDashboardTrend,
  getDashboardDistribution,
  getDashboardCompanyRank,
} from '@/services/admin';
import styles from './index.less';

// 类型定义
interface DashboardStats {
  userCount: number;
  companyCount: number;
  jobCount: number;
  applicationCount: number;
  // API 实际字段名（兼容两种命名）
  enterpriseCount?: number;
  positionCount?: number;
  interviewCount?: number;
  offerCount?: number;
  userCountToday?: number;
  enterpriseCountToday?: number;
  positionCountToday?: number;
  applicationCountToday?: number;
}

interface TrendItem {
  date: string;
  count: number;
}

interface DistributionItem {
  type: string;
  count: number;
}

const JOB_TYPE_LABELS: Record<string, string> = {
  JAVA_BACKEND: 'Java后端',
  FRONTEND: '前端',
  TEST_ENGINEER: '测试',
  PRODUCT: '产品',
  ALGORITHM_ENGINEER: '算法',
};

interface CompanyRankItem {
  id: number;
  name: string;
  conversationCount: number;
}

const DashboardPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [trendData, setTrendData] = useState<TrendItem[]>([]);
  const [distributionData, setDistributionData] = useState<DistributionItem[]>([]);
  const [companyRankData, setCompanyRankData] = useState<CompanyRankItem[]>([]);

  // 获取统计数据
  const fetchStats = useCallback(async () => {
    try {
      const data = await getAdminStats();
      setStats(data as unknown as DashboardStats);
    } catch (error) {
      message.error('获取统计数据失败');
    }
  }, []);

  // 获取趋势数据（近7日）
  const fetchTrend = useCallback(async () => {
    try {
      const data = await getDashboardTrend({ days: 7 }) as unknown as { dates: string[]; counts: number[] };
      setTrendData(data.dates?.map((date: string, index: number) => ({
        date,
        count: data.counts?.[index] ?? 0,
      })) || []);
    } catch (error) {
      message.error('获取趋势数据失败');
    }
  }, []);

  // 获取分布数据
  const fetchDistribution = useCallback(async () => {
    try {
      const data = await getDashboardDistribution();
      setDistributionData(data as unknown as DistributionItem[]);
    } catch (error) {
      message.error('获取分布数据失败');
    }
  }, []);

  // 获取排行数据
  const fetchCompanyRank = useCallback(async () => {
    try {
      const data = await getDashboardCompanyRank();
      setCompanyRankData(data as unknown as CompanyRankItem[]);
    } catch (error) {
      message.error('获取排行数据失败');
    }
  }, []);

  // 初始加载
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      await Promise.all([fetchStats(), fetchTrend(), fetchDistribution(), fetchCompanyRank()]);
      setLoading(false);
    };
    fetchData();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 计算最大值用于柱状图
  const maxTrendValue = trendData.length > 0 ? Math.max(...trendData.map((d) => d.count), 1) : 1;
  const maxDistributionValue = Math.max(...distributionData.map((d) => d.count), 1);

  // 排名列定义
  const rankColumns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 70,
      render: (_: unknown, __: unknown, index: number) => {
        const rank = index + 1;
        const getRankClass = (r: number) => {
          if (r === 1) return styles.rankGold;
          if (r === 2) return styles.rankSilver;
          if (r === 3) return styles.rankBronze;
          return styles.rankDefault;
        };
        return <span className={`${styles.rankBadge} ${getRankClass(rank)}`}>{rank}</span>;
      },
    },
    { title: '企业名称', dataIndex: 'name', key: 'name' },
    { title: '沟通次数', dataIndex: 'conversationCount', key: 'conversationCount' },
  ];

  return (
    <div className={styles.dashboard}>
      <PageHeader
        title="数据看板"
        description="实时监控平台运营数据，掌握招聘动态"
      />

      <Spin spinning={loading}>
        {/* 统计卡片 */}
        <div className={styles.statGrid}>
          <StatCard
            icon={<UserOutlined />}
            label="注册用户"
            value={(stats?.userCount ?? 0).toLocaleString()}
          />
          <StatCard
            icon={<SafetyOutlined />}
            label="认证企业"
            value={String(stats?.enterpriseCount ?? stats?.companyCount ?? 0)}
          />
          <StatCard
            icon={<FileTextOutlined />}
            label="在招岗位"
            value={String(stats?.positionCount ?? stats?.jobCount ?? 0)}
          />
          <StatCard
            icon={<SendOutlined />}
            label="累计投递"
            value={(stats?.applicationCount ?? 0).toLocaleString()}
          />
        </div>

        {/* 图表区域 */}
        <div className={styles.chartGrid}>
          {/* 投递趋势 */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>近7日投递趋势</div>
            <div className={styles.barChart}>
              {trendData.map((item) => (
                <div key={item.date} className={styles.barItem}>
                  <span className={styles.barValue}>{item.count}</span>
                  <div
                    className={styles.bar}
                    style={{ height: `${(item.count / maxTrendValue) * 150}px` }}
                  />
                  <span className={styles.barLabel}>{item.date}</span>
                </div>
              ))}
            </div>
          </div>

          {/* 岗位类型分布 */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>岗位类型分布</div>
            <div className={styles.hBarChart}>
              {distributionData.map((item) => (
                <div key={item.type} className={styles.hBarItem}>
                  <span className={styles.hBarLabel}>{JOB_TYPE_LABELS[item.type] || item.type}</span>
                  <div className={styles.hBarTrack}>
                    <div
                      className={styles.hBarFill}
                      style={{ width: `${(item.count / maxDistributionValue) * 100}%` }}
                    >
                      <span className={styles.hBarVal}>{item.count}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 企业活跃度排行 */}
        <div className={styles.card}>
          <div className={styles.cardTitle}>企业活跃度排行</div>
          <Table
            className={styles.rankTable}
            columns={rankColumns}
            dataSource={companyRankData}
            rowKey="id"
            pagination={false}
          />
        </div>
      </Spin>
    </div>
  );
};

export default DashboardPage;
