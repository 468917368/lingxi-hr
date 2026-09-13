/**
 * 候选人简历详情页（/hr/candidate/detail/:userId）
 * 按 userId 查看候选人简历结构化内容，支持发起沟通（创建/复用会话并发送消息）。
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'umi';
import {
  Card,
  Button,
  Space,
  Tag,
  Descriptions,
  Divider,
  message,
  Spin,
  Modal,
  Input,
} from 'antd';
import {
  ArrowLeftOutlined,
  MessageOutlined,
  FileTextOutlined,
  PhoneOutlined,
  MailOutlined,
  WechatOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { getCandidateResumeByUserId } from '@/services/hr';
import { createConversation, sendMessage } from '@/services/message';
import useUserStore from '@/stores/userStore';
import type { ResumeDetail } from '@/services/resume';
import styles from './index.less';

const CandidateDetailPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { userInfo } = useUserStore();
  const [loading, setLoading] = useState(true);
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [contactModalVisible, setContactModalVisible] = useState(false);
  const [contactMessage, setContactMessage] = useState('您好，我看到您的简历非常符合我们的岗位需求，希望有机会和您聊聊！');
  const [sending, setSending] = useState(false);

  /** 获取候选人简历 */
  useEffect(() => {
    const fetchResume = async () => {
      if (!userId) return;
      setLoading(true);
      try {
        const data = await getCandidateResumeByUserId(userId);
        setResume(data);
      } catch (error: any) {
        message.error(error.message || '获取简历失败');
      } finally {
        setLoading(false);
      }
    };
    fetchResume();
  }, [userId]);

  /** 发起沟通 */
  const handleContact = useCallback(async () => {
    if (!userId || !contactMessage.trim()) {
      message.warning('请输入沟通内容');
      return;
    }
    setSending(true);
    try {
      // 创建会话
      const conversationId = await createConversation({
        companyId: userInfo?.companyId || 0,
        candidateId: Number(userId),
        hrId: userInfo?.id || 0,
      });

      // 发送消息
      await sendMessage(conversationId, {
        content: contactMessage,
        msgType: 'TEXT',
        contentType: 'TEXT',
      });

      message.success('消息已发送');
      setContactModalVisible(false);

      // 跳转到消息页面
      navigate(`/hr/message?conversationId=${conversationId}`);
    } catch (error: any) {
      message.error(error.message || '发送失败');
    } finally {
      setSending(false);
    }
  }, [userId, contactMessage, navigate, userInfo]);

  /** 渲染卡片章节 - 展示所有 sections */
  const renderCardSections = () => {
    if (!resume?.cardStructure?.sections) return null;

    return resume.cardStructure.sections.map((section, index) => (
      <Card key={index} className={styles.sectionCard}>
        <h3 className={styles.sectionTitle}>{section.title}</h3>
        <ul className={styles.sectionList}>
          {section.points.map((point) => (
            <li key={point.id} className={styles.sectionItem}>
              {point.text}
            </li>
          ))}
        </ul>
      </Card>
    ));
  };

  if (loading) {
    return (
      <div className={styles.loading}>
        <Spin size="large" />
      </div>
    );
  }

  if (!resume) {
    return (
      <div className={styles.error}>
        <p>简历不存在或已删除</p>
        <Button onClick={() => navigate(-1)}>返回</Button>
      </div>
    );
  }

  const isBlindMode = resume.blindMode === true;

  return (
    <div className={styles.page}>
      {/* 顶部导航 */}
      <div className={styles.header}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(-1)}
        >
          返回
        </Button>
        <h2>{isBlindMode ? '候选人简历' : (resume.candidateName || '候选人简历')}</h2>
        <Button
          type="primary"
          icon={<MessageOutlined />}
          onClick={() => setContactModalVisible(true)}
        >
          发起沟通
        </Button>
      </div>

      {/* 基本信息 - 盲选模式下隐藏 */}
      {!isBlindMode && (
        <Card className={styles.infoCard}>
          <Descriptions column={2}>
            <Descriptions.Item label="姓名">
              {resume.candidateName || '未填写'}
            </Descriptions.Item>
            <Descriptions.Item label="手机">
              <PhoneOutlined /> {resume.phone || '未填写'}
            </Descriptions.Item>
            <Descriptions.Item label="邮箱">
              <MailOutlined /> {resume.email || '未填写'}
            </Descriptions.Item>
            <Descriptions.Item label="微信">
              <WechatOutlined /> {resume.wechat || '未填写'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {isBlindMode && (
        <Card className={styles.infoCard}>
          <div style={{ padding: '8px 0', color: '#666' }}>
            📋 该候选人已开启盲选模式，个人信息已隐藏
          </div>
        </Card>
      )}

      {/* 简历内容 */}
      <div className={styles.content}>
        <h3 className={styles.title}>
          <FileTextOutlined /> 简历内容
        </h3>
        {renderCardSections()}
      </div>

      {/* 发起沟通弹窗 */}
      <Modal
        title="发起沟通"
        open={contactModalVisible}
        onCancel={() => setContactModalVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setContactModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="send"
            type="primary"
            icon={<SendOutlined />}
            loading={sending}
            onClick={handleContact}
          >
            发送
          </Button>,
        ]}
      >
        <div className={styles.contactContent}>
          <p>向 {resume.candidateName || '候选人'} 发送消息：</p>
          <Input.TextArea
            value={contactMessage}
            onChange={(e) => setContactMessage(e.target.value)}
            placeholder="请输入沟通内容，例如：您好，我看到您的简历非常符合我们的岗位需求..."
            rows={4}
          />
        </div>
      </Modal>
    </div>
  );
};

export default CandidateDetailPage;
