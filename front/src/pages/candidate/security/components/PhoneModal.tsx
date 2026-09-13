import React, { useState, useRef, useEffect } from 'react';
import { Form, Input, Button, Modal, message } from 'antd';
import { sendPhoneCode, changePhone } from '@/services/user';
import { maskPhone, isValidPhone } from '@/utils/validators';
import { isApiError } from '@/utils/apiError';
import { useCountdown } from '@/hooks/useCountdown';

interface PhoneModalProps {
  visible: boolean;
  currentPhone: string;
  onClose: () => void;
  onSuccess: () => void;
}

const PhoneModal = ({ visible, currentPhone, onClose, onSuccess }: PhoneModalProps) => {
  const [form] = Form.useForm();
  const isMountedRef = useRef(true);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
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
      await form.validateFields(['newPhone']);
    } catch {
      return;
    }

    const newPhone = form.getFieldValue('newPhone');
    if (!isValidPhone(newPhone)) {
      message.warning('请输入正确的手机号');
      return;
    }

    setSending(true);
    try {
      await sendPhoneCode(newPhone);
      message.success('验证码已发送');
      startCountdown();
    } catch (error) {
      if (isApiError(error)) {
        switch (error.code) {
          case 1007:
            message.error('发送过于频繁，请60秒后重试');
            break;
          case 1008:
            message.error('今日发送次数已达上限');
            break;
          case 1009:
            message.error('请求过于频繁，请稍后重试');
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

  /** 提交修改 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      await changePhone(values.newPhone, values.code);
      message.success('手机号修改成功');
      form.resetFields();
      onSuccess();
      onClose();
    } catch (error) {
      console.error('修改手机号失败:', error);
    } finally {
      if (isMountedRef.current) {
        setSubmitting(false);
      }
    }
  };

  return (
    <Modal
      title="修改手机号"
      open={visible}
      onOk={handleSubmit}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      confirmLoading={submitting}
      okText="确认修改"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item label="当前手机号">
          <Input value={maskPhone(currentPhone)} disabled />
        </Form.Item>
        <Form.Item
          name="newPhone"
          label="新手机号"
          rules={[
            { required: true, message: '请输入新手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' },
          ]}
        >
          <Input placeholder="请输入新手机号" maxLength={11} />
        </Form.Item>
        <Form.Item
          name="code"
          label="验证码"
          rules={[{ required: true, message: '请输入验证码' }]}
        >
          <Input
            placeholder="请输入验证码"
            maxLength={6}
            suffix={
              <Button
                type="link"
                disabled={countdown > 0}
                loading={sending}
                onClick={handleSendCode}
              >
                {countdown > 0 ? `${countdown}秒后重试` : '获取验证码'}
              </Button>
            }
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PhoneModal;
