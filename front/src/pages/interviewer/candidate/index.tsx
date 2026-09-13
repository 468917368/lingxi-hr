/**
 * 面试官「候选人」（/interviewer/candidate）
 * 仅展示分配给当前面试官的面试候选人；查看简历（Drawer，联系方式等敏感信息对面试官隐藏），
 * 非终态行支持「面试出题」（复用 QuestionGenerateModal，SSE 流式 AI 出题）。
 */
import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Drawer, Spin, Empty, Alert, Space } from 'antd';
import { EyeOutlined, UserOutlined, CalendarOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHero from '@/components/PageHero';
import { useAuth } from '@/hooks/useAuth';
import { getInterviewList, getCandidateResume } from '@/services/hr';
import type { InterviewVO } from '@/constants/apiTypes';
import type { ResumeDetail, CardSection } from '@/services/resume';
import { InterviewStatus } from '@/constants/enums';
import { getAvatarUrl } from '@/utils/fileUrl';
import QuestionGenerateModal, { QuestionTarget } from '@/pages/hr/candidate/components/QuestionGenerateModal';
import styles from './index.less';

/** 面试状态徽章配色（与面试官面试页一致） */
const statusColorMap: Record<InterviewStatus, string> = {
  [InterviewStatus.PENDING]: '#D97706',
  [InterviewStatus.SCHEDULED]: '#45B7D1',
  [InterviewStatus.IN_PROGRESS]: '#45B7D1',
  [InterviewStatus.COMPLETED]: '#059669',
  [InterviewStatus.CANCELLED]: '#DC2626',
};

/** 拆 ISO 时间为双行：{ date: '08-07', time: '14:00' } */
const splitDateTime = (t?: string): { date: string; time: string } => {
  if (!t) return { date: '-', time: '' };
  const [date, time] = t.replace('T', ' ').split(' ');
  const [, month, day] = date.split('-');
  return { date: `${month}-${day}`, time: time?.slice(0, 5) ?? '' };
};

const CandidatePage: React.FC = () => {
  const { userInfo } = useAuth();
  const [list, setList] = useState<InterviewVO[]>([]);
  const [loading, setLoading] = useState(false);

  // 简历 Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<InterviewVO | null>(null);
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [resumeLoading, setResumeLoading] = useState(false);
  const [resumeError, setResumeError] = useState(false);
  // 面试出题弹窗
  const [questionTarget, setQuestionTarget] = useState<QuestionTarget | null>(null);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getInterviewList({ interviewerId: userInfo?.id, page: 1, size: 50 });
      setList(data.list);
    } catch {
      // 拦截器已提示 body.message
    } finally {
      setLoading(false);
    }
  }, [userInfo?.id]);

  useEffect(() => {
    if (userInfo?.id) fetchList();
  }, [fetchList, userInfo?.id]);

  const handleViewResume = async (record: InterviewVO) => {
    setCurrent(record);
    setResume(null);
    setResumeError(false);
    setResumeLoading(true);
    setDrawerOpen(true);
    try {
      setResume(await getCandidateResume(record.applicationId));
    } catch {
      // 4011/4300/4301/4303 已由拦截器提示
      setResumeError(true);
    } finally {
      setResumeLoading(false);
    }
  };

  const columns: ColumnsType<InterviewVO> = [
    {
      title: '候选人',
      dataIndex: 'candidateName',
      key: 'candidateName',
      width: 160,
      render: (name: string, record) => (
        <div className={styles.candidateCell}>
          <div className={styles.candidateAvatar}>
            {record.candidateAvatar ? (
              <img src={getAvatarUrl(record.candidateAvatar)} alt="" />
            ) : (
              <UserOutlined />
            )}
          </div>
          <span className={styles.candidateName}>{name}</span>
        </div>
      ),
    },
    { title: '投递岗位', dataIndex: 'jobTitle', key: 'jobTitle', width: 180 },
    {
      title: '面试时间',
      dataIndex: 'scheduledAt',
      key: 'scheduledAt',
      width: 170,
      render: (t: string) => {
        const { date, time } = splitDateTime(t);
        return (
          <span className={styles.timeCell}>
            <CalendarOutlined />
            {date} <span className={styles.timeClock}>{time}</span>
          </span>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: InterviewStatus, record) => {
        const color = statusColorMap[status];
        return (
          <span className={styles.badge} style={{ color, background: `${color}14` }}>
            <span className={styles.badgeDot} style={{ background: color }} />
            {record.statusDesc || status}
          </span>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_: unknown, record) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewResume(record)}>
            查看简历
          </Button>
          {![InterviewStatus.CANCELLED, InterviewStatus.COMPLETED].includes(record.status) && (
            <Button
              type="link"
              size="small"
              icon={<ThunderboltOutlined />}
              onClick={() =>
                setQuestionTarget({
                  jobId: record.jobId,
                  applicationId: record.applicationId,
                  candidateName: record.candidateName,
                  jobTitle: record.jobTitle,
                })
              }
            >
              面试出题
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const chapters: CardSection[] = resume?.cardStructure?.cards ?? resume?.cardStructure?.sections ?? [];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="候选人"
        desc="仅展示已分配给我面试的候选人"
      />

      <Alert
        type="info"
        showIcon
        message="权限提示"
        description="作为面试官，您仅能查看已分配给您面试的候选人信息。为保护候选人隐私，联系方式等敏感信息不在此展示。"
        className={styles.infoTip}
      />

      <div className={styles.table}>
        <Table
          columns={columns}
          dataSource={list}
          rowKey="interviewId"
          loading={loading}
          pagination={false}
          locale={{ emptyText: '暂无分配给我的面试候选人' }}
        />
      </div>

      <Drawer
        title="候选人简历"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
      >
        {resumeLoading ? (
          <div className={styles.drawerLoading}><Spin size="large" /></div>
        ) : resumeError || !resume ? (
          <Empty description={resumeError ? '简历加载失败，请重试' : '暂无简历数据'} />
        ) : (
          <div className={styles.drawerContent}>
            {/* 头部 */}
            <div className={styles.resumeHero}>
              <div className={styles.resumeHeroAvatar}>
                {resume.facePhotoUrl ? (
                  <img src={getAvatarUrl(resume.facePhotoUrl)} alt="" />
                ) : (
                  <UserOutlined />
                )}
              </div>
              <div className={styles.resumeHeroInfo}>
                <div className={styles.resumeHeroName}>{resume.candidateName || current?.candidateName}</div>
                <div className={styles.resumeHeroMeta}>
                  {current?.jobTitle} · {splitDateTime(current?.scheduledAt).date} {splitDateTime(current?.scheduledAt).time}
                </div>
              </div>
            </div>

            <Alert
              type="info"
              showIcon
              message="隐私保护"
              description="为保护候选人隐私，联系方式等敏感信息仅 HR 可见。"
              className={styles.privacyTip}
            />

            {/* 简历结构化内容 */}
            {chapters.length > 0 ? (
              chapters.map((section, idx) => (
                <div key={section.title || idx} className={styles.drawerSection}>
                  <div className={styles.drawerTitle}>{section.title}</div>
                  {section.points.length > 0 ? (
                    <ul className={styles.pointList}>
                      {section.points.map((p, i) => (
                        <li key={p.id || i} className={styles.point}>
                          <span className={styles.pointDot} />
                          {p.text}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <div className={styles.pointEmpty}>{section.raw_text || '—'}</div>
                  )}
                </div>
              ))
            ) : (
              <Empty description="该简历暂无结构化内容" />
            )}
          </div>
        )}
      </Drawer>

      {/* 面试出题弹窗 */}
      <QuestionGenerateModal
        open={!!questionTarget}
        record={questionTarget}
        onClose={() => setQuestionTarget(null)}
      />
    </div>
  );
};

export default CandidatePage;
