/**
 * 修改邮箱弹窗：新邮箱 + 验证码（60s 倒计时），成功后刷新显示。
 */
import React, { useState, useRef, useEffect } from 'react';
import { Form, Input, Button, Modal, message } from 'antd';
import { sendHrEmailCode, changeHrEmail } from '@/services/hr';
import { isApiError } from '@/utils/apiError';
import { useCountdown } from '@/hooks/useCountdown';

interface EmailModalProps {
  visible: boolean;
  currentEmail?: string;
  onClose: () => void;
  onSuccess: () => void;
}

const EmailModal = ({ visible, currentEmail, onClose, onSuccess }: EmailModalProps) => {
  const [form] = Form.useForm();
  const isMountedRef = useRef(true);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [emailSent, setEmailSent] = useState(false);
  const { countdown, startCountdown } = useCountdown(60);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /** 发送验证码 */
  const handleSendCode = async () => {
    try {
      await form.validateFields(['email']);
    } catch {
      return;
    }

    const email = form.getFieldValue('email');
    setSending(true);
    try {
      await sendHrEmailCode(email);
      message.success('验证码已发送到您的邮箱');
      setEmailSent(true);
      startCountdown();
    } catch (error) {
      if (isApiError(error)) {
        switch (error.code) {
          case 1110:
            message.error('邮箱验证码发送过于频繁，请稍后重试');
            break;
          case 1111:
            message.error('邮箱验证码发送失败，请稍后重试');
            break;
          default:
            break;
        }
      }
    } finally {
      if (isMountedRef.current) {
        setSending(false);
      }
    }
  };

  /** 提交验证 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      await changeHrEmail(values.email, values.code);
      message.success('邮箱修改成功');
      form.resetFields();
      setEmailSent(false);
      onSuccess();
      onClose();
    } catch (error) {
      console.error('邮箱修改失败:', error);
    } finally {
      if (isMountedRef.current) {
        setSubmitting(false);
      }
    }
  };

  /** 关闭Modal */
  const handleClose = () => {
    form.resetFields();
    setEmailSent(false);
    onClose();
  };

  return (
    <Modal
      title="修改邮箱"
      open={visible}
      onOk={handleSubmit}
      onCancel={handleClose}
      confirmLoading={submitting}
      okText="确认修改"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        {currentEmail && (
          <Form.Item label="当前邮箱">
            <Input value={currentEmail} disabled />
          </Form.Item>
        )}
        <Form.Item
          name="email"
          label="新邮箱"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入正确的邮箱' },
          ]}
        >
          <Input
            placeholder="请输入邮箱"
            disabled={emailSent}
          />
        </Form.Item>

        {!emailSent ? (
          <Form.Item>
            <Button
              type="primary"
              onClick={handleSendCode}
              loading={sending}
              block
            >
              发送验证码
            </Button>
          </Form.Item>
        ) : (
          <Form.Item
            name="code"
            label="验证码"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Input
              placeholder="请输入6位验证码"
              maxLength={6}
              suffix={
                <Button
                  type="link"
                  disabled={countdown > 0}
                  loading={sending}
                  onClick={handleSendCode}
                >
                  {countdown > 0 ? `${countdown}秒后重试` : '重新发送'}
                </Button>
              }
            />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
};

export default EmailModal;
