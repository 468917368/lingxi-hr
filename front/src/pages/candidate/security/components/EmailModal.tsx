/**
 * 邮箱绑定/修改弹窗
 *
 * 功能：
 * 1. 输入新邮箱
 * 2. 发送邮箱验证码
 * 3. 输入验证码完成绑定/修改
 *
 * 流程：
 * 1. 输入邮箱 → 点击"发送验证码"
 * 2. 邮箱输入框禁用，显示验证码输入框
 * 3. 输入验证码 → 点击"确认修改"
 * 4. 成功后关闭弹窗
 *
 * 注意：
 * - 验证码60秒内只能发送一次（倒计时）
 * - 邮箱修改后需要重新登录（token失效）
 */
import React, { useState, useRef, useEffect } from 'react';
import { Form, Input, Button, Modal, message } from 'antd';
import { sendEmailCode, verifyEmail } from '@/services/user';
import { isApiError } from '@/utils/apiError';
import { useCountdown } from '@/hooks/useCountdown';

/** 组件Props */
interface EmailModalProps {
  visible: boolean;       // 弹窗是否可见
  currentEmail?: string;  // 当前邮箱（有值=修改，无值=绑定）
  onClose: () => void;    // 关闭回调
  onSuccess: () => void;  // 绑定/修改成功回调
}

const EmailModal = ({ visible, currentEmail, onClose, onSuccess }: EmailModalProps) => {
  // ==================== 状态定义 ====================

  /** 表单实例 */
  const [form] = Form.useForm();

  /** 组件是否挂载（防止卸载后更新状态） */
  const isMountedRef = useRef(true);

  /** 验证码发送中状态 */
  const [sending, setSending] = useState(false);

  /** 表单提交中状态 */
  const [submitting, setSubmitting] = useState(false);

  /** 验证码是否已发送（控制显示验证码输入框） */
  const [emailSent, setEmailSent] = useState(false);

  /** 60秒倒计时Hook */
  const { countdown, startCountdown } = useCountdown(60);

  // ==================== 副作用 ====================

  /** 组件卸载清理 */
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  // ==================== 事件处理 ====================

  /**
   * 发送验证码
   *
   * 流程：
   * 1. 校验邮箱格式
   * 2. 调用API发送验证码
   * 3. 显示验证码输入框
   * 4. 启动60秒倒计时
   */
  const handleSendCode = async () => {
    try {
      await form.validateFields(['email']);
    } catch {
      return;
    }

    const email = form.getFieldValue('email');
    setSending(true);
    try {
      await sendEmailCode(email);
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

  /**
   * 提交验证
   *
   * 流程：
   * 1. 表单校验
   * 2. 调用API验证邮箱
   * 3. 成功后重置表单，关闭弹窗
   */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      await verifyEmail(values.email, values.code);
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

  /**
   * 关闭Modal
   * 重置表单和状态
   */
  const handleClose = () => {
    form.resetFields();
    setEmailSent(false);
    onClose();
  };

  // ==================== 渲染 ====================

  return (
    <Modal
      title={currentEmail ? '修改邮箱' : '绑定邮箱'}
      open={visible}
      onOk={handleSubmit}
      onCancel={handleClose}
      confirmLoading={submitting}
      okText="确认修改"
      cancelText="取消"
      destroyOnClose  // 关闭时销毁内部组件
    >
      <Form form={form} layout="vertical">
        {/* 邮箱输入框 */}
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
            disabled={emailSent}  // 发送验证码后禁用
          />
        </Form.Item>

        {!emailSent ? (
          /* 未发送验证码：显示"发送验证码"按钮 */
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
          /* 已发送验证码：显示验证码输入框 */
          <Form.Item
            name="code"
            label="验证码"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Input
              placeholder="请输入6位验证码"
              maxLength={6}
              suffix={
                /* 重新发送按钮（带倒计时） */
                <Button
                  type="link"
                  disabled={countdown > 0}  // 倒计时中禁用
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
