import React, { useState, useEffect, useRef } from 'react';
import { Drawer, Spin, Empty, Descriptions, Tag, Space, Button, message } from 'antd';
import { EditOutlined } from '@ant-design/icons';
import StatusTag from '@/components/StatusTag';
import { getQuestionDetail } from '@/services/questionBank';
import type { HrQuestionListVO, HrQuestionDetailVO } from '@/services/questionBank';
import {
  QuestionStatus,
  QuestionType,
  QuestionDifficulty,
  QuestionSource,
} from '@/constants/enums';
import { renderJobTypeTag } from '@/constants/questionBank';
import { getErrorCode } from '@/utils/apiError';
import styles from './QuestionDetailDrawer.less';

interface Props {
  /** 打开抽屉的目标（列表行）；null 关闭 */
  target: HrQuestionListVO | null;
  onClose: () => void;
  /** 编辑按钮（传完整详情，避免二次请求） */
  onEdit: (detail: HrQuestionDetailVO) => void;
}

/** 题型 → 中文 */
const TYPE_LABEL: Record<QuestionType, string> = {
  [QuestionType.BASIC]: '基础验证',
  [QuestionType.PROJECT]: '项目深挖',
  [QuestionType.BOUNDARY]: '能力边界',
  [QuestionType.COMPREHENSIVE]: '综合素养',
};

/** 题型 → Tag 颜色 */
const TYPE_COLOR: Record<QuestionType, string> = {
  [QuestionType.BASIC]: 'blue',
  [QuestionType.PROJECT]: 'geekblue',
  [QuestionType.BOUNDARY]: 'purple',
  [QuestionType.COMPREHENSIVE]: 'cyan',
};

/** 难度 → 中文 */
const DIFF_LABEL: Record<QuestionDifficulty, string> = {
  [QuestionDifficulty.EASY]: '简单',
  [QuestionDifficulty.MEDIUM]: '中等',
  [QuestionDifficulty.HARD]: '困难',
};

/** 难度 → Tag 颜色 */
const DIFF_COLOR: Record<QuestionDifficulty, string> = {
  [QuestionDifficulty.EASY]: 'green',
  [QuestionDifficulty.MEDIUM]: 'orange',
  [QuestionDifficulty.HARD]: 'red',
};

/** 状态 → 中文 */
const STATUS_LABEL: Record<QuestionStatus, string> = {
  [QuestionStatus.PENDING_REVIEW]: '待审核',
  [QuestionStatus.ACTIVE]: '已启用',
  [QuestionStatus.INACTIVE]: '已停用',
  [QuestionStatus.REJECTED]: '已拒绝',
};

/** 状态 → StatusTag type */
const STATUS_TAG_TYPE: Record<QuestionStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  [QuestionStatus.PENDING_REVIEW]: 'warning',
  [QuestionStatus.ACTIVE]: 'success',
  [QuestionStatus.INACTIVE]: 'neutral',
  [QuestionStatus.REJECTED]: 'danger',
};

/** 来源 → 中文 */
const SOURCE_LABEL: Record<QuestionSource, string> = {
  [QuestionSource.HR_CREATED]: 'HR创建',
  [QuestionSource.AI_GENERATED]: 'AI生成',
};

/** 格式化 ISO 时间 → 展示 */
const fmtTime = (iso?: string) => {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 16);
};

const QuestionDetailDrawer: React.FC<Props> = ({ target, onClose, onEdit }) => {
  const [detail, setDetail] = useState<HrQuestionDetailVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  // 详情请求序号：快速切换题目 A→B 时 A 的请求可能晚返回，据此丢弃过期响应，避免错误题目覆盖当前 detail
  const fetchSeqRef = useRef(0);

  // 打开时按 id 拉取完整详情（列表不缓存敏感字段，始终走详情接口）
  useEffect(() => {
    if (target) {
      const seq = ++fetchSeqRef.current;
      setDetail(null);
      setError(false);
      setLoading(true);
      getQuestionDetail(target.id)
        .then((data) => {
          // 过期响应丢弃（当前已切换/发起更新请求）
          if (seq !== fetchSeqRef.current) return;
          setDetail(data);
        })
        .catch((e) => {
          if (seq !== fetchSeqRef.current) return;
          // 2401 题目不存在/无权访问：抽屉内展示错误态（拦截器已提示），不自动关闭
          if (getErrorCode(e) === 2401) {
            setError(true);
          } else {
            setError(true);
            message.error('题目加载失败，请重试');
          }
        })
        .finally(() => {
          // 仅最新请求复位 loading
          if (seq === fetchSeqRef.current) setLoading(false);
        });
    }
    // 依赖仅为 target?.id：effect 内只读取 target.id，onClose/onEdit 不在此 effect 使用
  }, [target?.id]);

  const canEdit = !!detail && detail.status !== QuestionStatus.PENDING_REVIEW;

  return (
    <Drawer title="题目详情" open={!!target} onClose={onClose} width={640}>
      {loading ? (
        <div className={styles.drawerLoading}><Spin size="large" /></div>
      ) : !detail ? (
        <Empty description={error ? '题目不存在或加载失败' : '暂无数据'} />
      ) : (
        <div className={styles.body}>
          <div className={styles.header}>
            <span className={styles.headerTitle}>题干</span>
            {canEdit && (
              <Button type="primary" size="small" icon={<EditOutlined />} onClick={() => onEdit(detail)}>
                编辑
              </Button>
            )}
          </div>
          <div className={styles.content}>{detail.content}</div>

          <Descriptions column={2} size="small">
            <Descriptions.Item label="岗位类型">{detail.jobType ? renderJobTypeTag(detail.jobType) : '-'}</Descriptions.Item>
            <Descriptions.Item label="题型">
              <Tag color={TYPE_COLOR[detail.questionType]}>{TYPE_LABEL[detail.questionType] || detail.questionType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="难度">
              <Tag color={DIFF_COLOR[detail.difficulty]}>{DIFF_LABEL[detail.difficulty] || detail.difficulty}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="来源">
              <Tag>{SOURCE_LABEL[detail.source] || detail.source}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag type={STATUS_TAG_TYPE[detail.status]}>{STATUS_LABEL[detail.status] || detail.status}</StatusTag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{fmtTime(detail.createdAt)}</Descriptions.Item>
          </Descriptions>

          {detail.skillTags?.length > 0 && (
            <div className={styles.section}>
              <div className={styles.sectionTitle}>技能标签</div>
              <Space wrap size={[0, 4]}>
                {detail.skillTags.map((t) => (
                  <Tag key={t}>{t}</Tag>
                ))}
              </Space>
            </div>
          )}

          <div className={styles.section}>
            <div className={styles.sectionTitle}>考察要点</div>
            <div className={styles.sectionText}>{detail.keyPoints || '—'}</div>
          </div>

          <div className={styles.section}>
            <div className={styles.sectionTitle}>参考答案</div>
            <div className={styles.sectionText}>{detail.referenceAnswer || '—'}</div>
          </div>

          {detail.evaluationPoints?.length > 0 && (
            <div className={styles.section}>
              <div className={styles.sectionTitle}>评分要点</div>
              {detail.evaluationPoints.map((p, i) => (
                <div key={i} className={styles.evalRow}>
                  <span className={styles.evalName}>{p.name}</span>
                  <span className={styles.evalWeight}>{Math.round((p.weight || 0) * 100)}%</span>
                </div>
              ))}
            </div>
          )}

          {detail.reviewedBy || detail.reviewedAt || detail.reviewReason ? (
            <div className={styles.section}>
              <div className={styles.sectionTitle}>审核信息</div>
              <div className={styles.meta}>
                <div>审核时间：{fmtTime(detail.reviewedAt)}</div>
                {detail.reviewedBy !== undefined && <div>审核人ID：{detail.reviewedBy}</div>}
                {detail.reviewReason && <div>审核意见：{detail.reviewReason}</div>}
              </div>
            </div>
          ) : null}

          <div className={styles.section}>
            <div className={styles.sectionTitle}>创建信息</div>
            <div className={styles.meta}>
              <div>创建人ID：{detail.createdBy ?? '-'}</div>
              <div>更新时间：{fmtTime(detail.updatedAt)}</div>
            </div>
          </div>
        </div>
      )}
    </Drawer>
  );
};

export default QuestionDetailDrawer;
