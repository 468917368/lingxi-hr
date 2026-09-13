import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'umi';
import { Timeline, Button, Alert, Spin } from 'antd';
import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { getCertificationStatus } from '@/services/company';
import type { CertStatus } from '@/constants/apiTypes';
import styles from './index.less';

const statusIconMap: Record<CertStatus, React.ReactNode> = {
  PENDING: <ClockCircleOutlined className={styles.iconPending} />,
  APPROVED: <CheckCircleOutlined className={styles.iconApproved} />,
  REJECTED: <CloseCircleOutlined className={styles.iconRejected} />,
};

const statusTitleMap: Record<CertStatus, string> = {
  PENDING: '审核中',
  APPROVED: '审核通过',
  REJECTED: '审核未通过',
};

const statusDescMap: Record<CertStatus, string> = {
  PENDING: '您的企业认证申请正在审核中，预计1-3个工作日完成',
  APPROVED: '恭喜！您的企业认证已通过，可以开始使用招聘功能',
  REJECTED: '很抱歉，您的企业认证未通过，请根据原因修改后重新提交',
};

const CertificationPendingPage: React.FC = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState<CertStatus>('PENDING');
  const [rejectReason, setRejectReason] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const isMountedRef = useRef(true);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await getCertificationStatus();
      if (!isMountedRef.current) return;
      setStatus(res.status);
      if (res.rejectReason) {
        setRejectReason(res.rejectReason);
      }
    } catch (error) {
      if (!isMountedRef.current) return;
      console.error('获取认证状态失败:', error);
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    fetchStatus();
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchStatus]);

  if (loading) {
    return (
      <div className={styles.loading}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.icon}>
          {statusIconMap[status]}
        </div>

        <h2 className={styles.title}>
          {statusTitleMap[status]}
        </h2>

        <p className={styles.desc}>
          {statusDescMap[status]}
        </p>

        <Timeline
          className={styles.timeline}
          items={[
            { color: 'green', children: '手机号验证' },
            { color: 'green', children: '个人信息填写' },
            { color: 'green', children: '企业认证提交' },
            {
              color: status === 'PENDING' ? 'blue' : status === 'APPROVED' ? 'green' : 'red',
              children: `平台审核${status === 'PENDING' ? '中' : status === 'APPROVED' ? '通过' : '拒绝'}`,
            },
          ]}
        />

        {status === 'REJECTED' && rejectReason && (
          <Alert
            type="error"
            message="拒绝原因"
            description={rejectReason}
            className={styles.alert}
          />
        )}

        <div className={styles.actions}>
          {status === 'APPROVED' && (
            <Button type="primary" size="large" onClick={() => navigate('/hr/dashboard')}>
              进入工作台
            </Button>
          )}
          {status === 'REJECTED' && (
            <Button type="primary" size="large" onClick={() => navigate('/register')}>
              重新提交
            </Button>
          )}
          {status === 'PENDING' && (
            <Button size="large" onClick={fetchStatus}>
              刷新状态
            </Button>
          )}
          <Button size="large" onClick={() => navigate('/login')}>
            返回登录
          </Button>
        </div>
      </div>
    </div>
  );
};

export default CertificationPendingPage;
