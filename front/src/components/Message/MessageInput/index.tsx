import React, { useState, useRef, useCallback } from 'react';
import { Button, Upload, message, Tooltip } from 'antd';
import {
  SendOutlined,
  PaperClipOutlined,
  PictureOutlined,
  FileOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { uploadMessageFile } from '@/services/message';
import styles from './index.less';

interface MessageInputProps {
  onSend: (content: string, contentType?: string, extra?: {
    mediaUrl?: string;
    fileName?: string;
    fileSize?: number;
  }) => void;
  onTyping?: () => void;
  disabled?: boolean;
  loading?: boolean;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

const MessageInput: React.FC<MessageInputProps> = ({ onSend, onTyping, disabled = false, loading = false }) => {
  const [inputValue, setInputValue] = useState('');
  const [uploading, setUploading] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  /** 发送文本消息 */
  const handleSend = useCallback(() => {
    const content = inputValue.trim();
    if (!content || disabled) return;
    onSend(content, 'TEXT');
    setInputValue('');
    inputRef.current?.focus();
  }, [inputValue, disabled, onSend]);

  /** 输入变化时发送正在输入状态 */
  const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputValue(e.target.value);

    // 发送正在输入状态（节流，每2秒最多发送一次）
    if (onTyping && !typingTimerRef.current) {
      onTyping();
      typingTimerRef.current = setTimeout(() => {
        typingTimerRef.current = null;
      }, 2000);
    }
  }, [onTyping]);

  /** 键盘事件 */
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  /** 上传文件 */
  const handleUpload = useCallback(
    async (file: File, contentType: 'IMAGE' | 'FILE') => {
      if (file.size > MAX_FILE_SIZE) {
        message.error('文件大小不能超过10MB');
        return;
      }

      console.log('[Upload] Starting upload:', { fileName: file.name, fileSize: file.size, contentType });
      setUploading(true);
      try {
        const result = await uploadMessageFile(file);
        console.log('[Upload] Success:', result);
        onSend(result.fileName || '文件', contentType, {
          mediaUrl: result.fileUrl,
          fileName: result.fileName,
          fileSize: result.fileSize,
        });
      } catch (error) {
        console.error('[Upload] Failed:', error);
        message.error('文件上传失败');
      } finally {
        setUploading(false);
      }
    },
    [onSend],
  );

  /** 图片上传配置 */
  const imageUploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    beforeUpload: (file) => {
      handleUpload(file, 'IMAGE');
      return false;
    },
  };

  /** 文件上传配置 */
  const fileUploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: (file) => {
      handleUpload(file, 'FILE');
      return false;
    },
  };

  return (
    <div className={styles.container}>
      <div className={styles.toolbar}>
        <Tooltip title="发送图片">
          <Upload {...imageUploadProps}>
            <Button
              type="text"
              icon={<PictureOutlined />}
              size="small"
              disabled={disabled || uploading}
            />
          </Upload>
        </Tooltip>
        <Tooltip title="发送文件">
          <Upload {...fileUploadProps}>
            <Button
              type="text"
              icon={<PaperClipOutlined />}
              size="small"
              disabled={disabled || uploading}
            />
          </Upload>
        </Tooltip>
      </div>

      <div className={styles.inputRow}>
        <textarea
          ref={inputRef}
          className={styles.input}
          placeholder={disabled ? '请选择会话' : '输入消息，Enter发送，Shift+Enter换行'}
          value={inputValue}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          rows={1}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          disabled={disabled || !inputValue.trim()}
          loading={loading || uploading}
          className={styles.sendBtn}
        />
      </div>
    </div>
  );
};

export default React.memo(MessageInput);
