import React, { useState, useEffect } from 'react';
import { Input, InputNumber, Select, Switch, Button, Spin, message } from 'antd';
import PageHeader from '@/components/PageHeader';
import { getSystemConfig, updateSystemConfig } from '@/services/admin';
import styles from './index.less';

interface ConfigItem {
  id: number;
  configKey: string;
  configValue: string;
  description: string;
}

/** 根据 configKey 判断控件类型 */
const getInputType = (key: string) => {
  if (key === 'cert_mode') return 'select';
  if (['ai_stream', 'register_enabled'].includes(key)) return 'switch';
  if (['file_max_size', 'ai_max_rounds', 'max_resume_count'].includes(key)) return 'number';
  return 'text';
};

/** configKey -> 中文标签 */
const KEY_LABELS: Record<string, string> = {
  platform_name: '平台名称',
  platform_logo: '平台Logo URL',
  contact_email: '客服邮箱',
  file_max_size: '文件大小限制（MB）',
  register_enabled: '开放注册',
  cert_mode: '企业认证方式',
  ai_model: 'AI模型',
  ai_max_rounds: 'AI最大轮次',
  ai_stream: 'AI流式输出',
  max_resume_count: '每用户最大简历数',
};

/** configKey -> 分组 */
const KEY_GROUPS: Record<string, string> = {
  platform_name: '基础配置',
  platform_logo: '基础配置',
  contact_email: '基础配置',
  file_max_size: '基础配置',
  register_enabled: '注册配置',
  cert_mode: '注册配置',
  ai_model: 'AI配置',
  ai_max_rounds: 'AI配置',
  ai_stream: 'AI配置',
  max_resume_count: 'AI配置',
};

const ConfigPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [configs, setConfigs] = useState<ConfigItem[]>([]);

  useEffect(() => {
    const fetchConfig = async () => {
      setLoading(true);
      try {
        const res = await getSystemConfig() as unknown;
        const list: ConfigItem[] = Array.isArray(res)
          ? res
          : (res as { data?: ConfigItem[] })?.data ?? [];
        setConfigs(list);
      } catch {
        message.error('获取系统配置失败');
      } finally {
        setLoading(false);
      }
    };
    fetchConfig();
  }, []);

  const getValue = (key: string) => {
    return configs.find((c) => c.configKey === key)?.configValue ?? '';
  };

  const setValue = (key: string, value: string) => {
    setConfigs((prev) =>
      prev.map((c) => (c.configKey === key ? { ...c, configValue: value } : c)),
    );
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = configs.map((c) => ({
        configKey: c.configKey,
        configValue: c.configValue,
      }));
      await updateSystemConfig(payload);
      message.success('系统配置已保存');
    } catch {
      message.error('保存系统配置失败');
    } finally {
      setSaving(false);
    }
  };

  const renderControl = (item: ConfigItem) => {
    const type = getInputType(item.configKey);
    const value = item.configValue;

    switch (type) {
      case 'switch':
        return (
          <Switch
            checked={value === 'true'}
            onChange={(v) => setValue(item.configKey, String(v))}
          />
        );
      case 'number':
        return (
          <InputNumber
            value={Number(value) || 0}
            onChange={(v) => setValue(item.configKey, String(v ?? 0))}
            style={{ width: 200 }}
          />
        );
      case 'select':
        return (
          <Select
            value={value}
            onChange={(v) => setValue(item.configKey, v)}
            style={{ width: 200 }}
            options={[
              { label: '人工审核', value: 'manual' },
              { label: '自动审核', value: 'auto' },
            ]}
          />
        );
      default:
        return (
          <Input
            value={value}
            onChange={(e) => setValue(item.configKey, e.target.value)}
            style={{ width: 320 }}
          />
        );
    }
  };

  // 分组
  const groups = ['基础配置', '注册配置', 'AI配置'];
  const groupedConfigs = groups
    .map((g) => ({
      group: g,
      items: configs.filter((c) => KEY_GROUPS[c.configKey] === g),
    }))
    .filter((g) => g.items.length > 0);

  return (
    <Spin spinning={loading}>
      <div className={styles.page}>
        <PageHeader title="系统配置" description="管理系统全局配置参数" />

        {groupedConfigs.map(({ group, items }) => (
          <div key={group} className={styles.configSection}>
            <div className={styles.sectionCard}>
              <div className={styles.sectionTitle}>{group}</div>
              <div className={styles.formGrid}>
                {items.map((item) => (
                  <div key={item.configKey} className={styles.formItem}>
                    <span className={styles.formLabel}>
                      {KEY_LABELS[item.configKey] || item.configKey}
                    </span>
                    {renderControl(item)}
                  </div>
                ))}
              </div>
            </div>
          </div>
        ))}

        <div className={styles.saveBar}>
          <Button type="primary" size="large" onClick={handleSave} loading={saving}>
            保存配置
          </Button>
        </div>
      </div>
    </Spin>
  );
};

export default ConfigPage;
