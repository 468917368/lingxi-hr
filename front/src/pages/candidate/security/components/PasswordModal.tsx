import React, { useState, useRef, useEffect } from 'react';
import { Form, Input, Modal, message } from 'antd';
import { changePassword } from '@/services/auth';
import { validatePassword } from '@/utils/validators';

interface PasswordModalProps {
  visible: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const PasswordModal = ({ visible, onClose, onSuccess }: PasswordModalProps) => {
  const [form] = Form.useForm();
  const isMountedRef = useRef(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /** 提交修改 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      await changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });

      message.success('密码修改成功');
      form.resetFields();
      onSuccess();
      onClose();
    } catch (error) {
      console.error('修改密码失败:', error);
    } finally {
      if (isMountedRef.current) {
        setSubmitting(false);
      }
    }
  };

  return (
    <Modal
      title="修改密码"
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
        <Form.Item
          name="oldPassword"
          label="当前密码"
          rules={[{ required: true, message: '请输入当前密码' }]}
        >
          <Input.Password placeholder="请输入当前密码" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[{ required: true, validator: validatePassword }]}
        >
          <Input.Password placeholder="请设置新密码（8-20位）" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请确认新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}
        >
          <Input.Password placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PasswordModal;
