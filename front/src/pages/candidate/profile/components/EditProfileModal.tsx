/**
 * 编辑个人信息弹窗
 *
 * 功能：
 * 1. 编辑基本信息（姓名、头像、性别、城市、学历等）
 * 2. 编辑求职意向（期望岗位、城市、薪资、到岗时间）
 * 3. 头像上传（支持jpg/png，最大2MB）
 * 4. 姓名修改限制（每月只能修改一次）
 * 5. 保存后同步更新全局用户状态（头像实时更新）
 *
 * 注意：
 * - 手机号和邮箱是只读的，不能修改
 * - 姓名每月只能修改一次
 * - 薪资单位是千元（后端存储为元）
 */
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Modal, Form, Input, Select, Radio, Upload, message, Tooltip, Divider } from 'antd';
import { PlusOutlined, LoadingOutlined, InfoCircleOutlined } from '@ant-design/icons';
import type { RcFile } from 'antd/es/upload';
import { updateUserInfo, updateProfile, uploadAvatar, getUserInfo } from '@/services/user';
import { checkNameModifiable } from '@/utils/validators';
import type { UserInfo, UpdateUserInfoRequest, UpdateProfileRequest } from '@/constants/apiTypes';
import useUserStore from '@/stores/userStore';
import styles from './EditProfileModal.less';

/** 组件Props */
interface EditProfileModalProps {
  visible: boolean;    // 弹窗是否可见
  user: UserInfo;      // 当前用户信息
  onClose: () => void; // 关闭回调
  onSuccess: () => void; // 保存成功回调
}

// ==================== 下拉选项配置 ====================

/** 城市列表 */
const CITY_OPTIONS = [
  { value: '北京', label: '北京' },
  { value: '上海', label: '上海' },
  { value: '深圳', label: '深圳' },
  { value: '杭州', label: '杭州' },
  { value: '广州', label: '广州' },
  { value: '成都', label: '成都' },
  { value: '长沙', label: '长沙' },
  { value: '武汉', label: '武汉' },
  { value: '南京', label: '南京' },
  { value: '西安', label: '西安' },
];

/** 工作年限选项 */
const WORK_YEARS_OPTIONS = [
  { value: 'FRESH', label: '应届生' },
  { value: '1-3', label: '1-3年' },
  { value: '3-5', label: '3-5年' },
  { value: '5-10', label: '5-10年' },
  { value: '10+', label: '10年以上' },
];

/** 学历选项 */
const EDUCATION_OPTIONS = [
  { value: 'COLLEGE', label: '大专' },
  { value: 'BACHELOR', label: '本科' },
  { value: 'MASTER', label: '硕士' },
  { value: 'PHD', label: '博士' },
];

/** 薪资选项（单位：千元） */
const SALARY_OPTIONS = [
  { value: '5-10', label: '5K-10K' },
  { value: '10-15', label: '10K-15K' },
  { value: '15-20', label: '15K-20K' },
  { value: '20-30', label: '20K-30K' },
  { value: '30-50', label: '30K-50K' },
  { value: '50+', label: '50K以上' },
];

/** 到岗时间选项 */
const AVAILABLE_FROM_OPTIONS = [
  { value: 'ASAP', label: '随时到岗' },
  { value: '1W', label: '1周内' },
  { value: '1M', label: '1个月内' },
];

// ==================== 工具函数 ====================

/**
 * 头像上传前校验
 * - 格式：仅支持 jpg/png
 * - 大小：不超过 2MB
 */
const beforeUpload = (file: RcFile) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png';
  if (!isJpgOrPng) {
    message.error('头像仅支持jpg/png格式');
    return false;
  }
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isLt2M) {
    message.error('头像文件大小不可超过2MB');
    return false;
  }
  return true;
};

/**
 * 转换到岗时间为日期格式
 * ASAP → 今天
 * 1W → 7天后
 * 1M → 1个月后
 */
const convertAvailableFrom = (value: string): string => {
  const now = new Date();
  switch (value) {
    case 'ASAP':
      return now.toISOString().split('T')[0];
    case '1W':
      now.setDate(now.getDate() + 7);
      return now.toISOString().split('T')[0];
    case '1M':
      now.setMonth(now.getMonth() + 1);
      return now.toISOString().split('T')[0];
    default:
      return now.toISOString().split('T')[0];
  }
};

/**
 * 解析薪资范围（后端存元，前端显示千元）
 * 例：15000-25000 → "15-25"
 */
const parseSalary = (min?: number | null, max?: number | null): string | undefined => {
  if (!min || !max) return undefined;
  const minK = min / 1000;
  const maxK = max / 1000;
  return `${minK}-${maxK}`;
};

/**
 * 解析到岗时间（日期 → 选项值）
 * 7天内 → ASAP
 * 30天内 → 1W
 * 其他 → 1M
 */
const parseAvailableFrom = (date?: string | null): string | undefined => {
  if (!date) return undefined;
  const target = new Date(date);
  const now = new Date();
  const diffDays = Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  if (diffDays <= 0) return 'ASAP';
  if (diffDays <= 7) return '1W';
  return '1M';
};

const EditProfileModal = ({
  visible,
  user,
  onClose,
  onSuccess,
}: EditProfileModalProps) => {
  // ==================== 状态定义 ====================

  /** 表单实例 */
  const [form] = Form.useForm();

  /** 组件是否挂载（防止卸载后更新状态） */
  const isMountedRef = useRef(true);

  /** 提交加载状态 */
  const [loading, setLoading] = useState(false);

  /** 头像URL */
  const [avatarUrl, setAvatarUrl] = useState<string>(user.avatar || '');

  /** 头像上传中状态 */
  const [uploading, setUploading] = useState(false);

  // ==================== 计算属性 ====================

  /**
   * 检查姓名是否可修改
   * 每月只能修改一次，返回 { canModify, nextModifyDate }
   */
  const nameStatus = useMemo(
    () => checkNameModifiable(user.nameUpdatedAt),
    [user.nameUpdatedAt],
  );

  // ==================== 副作用 ====================

  /** 组件卸载清理 */
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /**
   * 初始化表单值
   * 弹窗打开时，用用户数据填充表单
   */
  useEffect(() => {
    if (visible && user) {
      form.setFieldsValue({
        // 基本信息
        name: user.name,
        phone: user.phone,    // 只读
        email: user.email,    // 只读
        gender: user.profile?.gender,
        city: user.profile?.city,
        workYears: user.profile?.workYears,
        education: user.profile?.education,
        jobStatus: user.profile?.jobStatus,
        // 求职意向
        desiredJob: user.profile?.desiredJob,
        desiredCity: user.profile?.desiredCity,
        desiredSalary: parseSalary(user.profile?.desiredSalaryMin, user.profile?.desiredSalaryMax),
        availableFrom: parseAvailableFrom(user.profile?.availableFrom),
      });
      setAvatarUrl(user.avatar || '');
    }
  }, [visible, user, form]);

  // ==================== 事件处理 ====================

  /**
   * 头像上传
   * 1. 校验文件格式和大小
   * 2. 调用API上传
   * 3. 更新头像URL
   */
  const handleUpload = async (file: RcFile) => {
    if (!beforeUpload(file)) return;

    setUploading(true);
    try {
      const url = await uploadAvatar(file);
      if (isMountedRef.current) {
        setAvatarUrl(url);
        message.success('头像上传成功');
      }
    } catch (error) {
      console.error('头像上传失败:', error);
      // 错误已在拦截器中提示
    } finally {
      if (isMountedRef.current) {
        setUploading(false);
      }
    }
  };

  /**
   * 提交表单
   *
   * 流程：
   * 1. 表单校验
   * 2. 更新基本信息（姓名、头像、性别、城市等）
   * 3. 更新求职意向（期望岗位、薪资等）
   * 4. 重新获取用户信息，同步到全局状态（头像实时更新）
   */
  const handleOk = async () => {
    try {
      // 表单校验
      const values = await form.validateFields();
      setLoading(true);

      // 第一步：更新基本信息
      const userInfoData: UpdateUserInfoRequest = {
        name: values.name,
        avatar: avatarUrl || undefined,
        gender: values.gender,
        city: values.city,
        workYears: values.workYears,
        education: values.education,
        jobStatus: values.jobStatus,
      };
      await updateUserInfo(userInfoData);

      // 第二步：更新求职意向
      // 薪资格式："15-25" → { min: 15000, max: 25000 }
      const [min, max] = values.desiredSalary ? values.desiredSalary.split('-').map(Number) : [undefined, undefined];
      const profileInfo: UpdateProfileRequest = {
        desiredJob: values.desiredJob,
        desiredCity: values.desiredCity,
        desiredSalaryMin: min ? min * 1000 : undefined,  // 千元 → 元
        desiredSalaryMax: max ? max * 1000 : undefined,
        availableFrom: values.availableFrom ? convertAvailableFrom(values.availableFrom) : undefined,
      };
      await updateProfile(profileInfo);

      // 第三步：重新获取用户信息，同步到全局状态
      // 这样布局组件的头像会实时更新
      const { setUserInfo } = useUserStore.getState();
      const latestUserInfo = await getUserInfo();
      setUserInfo({
        id: latestUserInfo.id,
        name: latestUserInfo.name,
        phone: latestUserInfo.phone,
        email: latestUserInfo.email,
        avatar: latestUserInfo.avatar || undefined,
        role: latestUserInfo.role,
        companyId: latestUserInfo.companyId || undefined,
        companyName: latestUserInfo.company?.name || undefined,
        profile: latestUserInfo.profile ? {
          gender: latestUserInfo.profile.gender || undefined,
          city: latestUserInfo.profile.city || undefined,
          workYears: latestUserInfo.profile.workYears || undefined,
          education: latestUserInfo.profile.education || undefined,
          jobStatus: latestUserInfo.profile.jobStatus || undefined,
          desiredJob: latestUserInfo.profile.desiredJob || undefined,
          desiredCity: latestUserInfo.profile.desiredCity || undefined,
          desiredSalaryMin: latestUserInfo.profile.desiredSalaryMin || undefined,
          desiredSalaryMax: latestUserInfo.profile.desiredSalaryMax || undefined,
          availableFrom: latestUserInfo.profile.availableFrom || undefined,
          resumePublic: latestUserInfo.profile.resumePublic,
          blindMode: latestUserInfo.profile.blindMode,
        } : undefined,
      });

      if (isMountedRef.current) {
        message.success('保存成功');
        onSuccess();
        onClose();
      }
    } catch (error) {
      console.error('保存失败:', error);
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  };

  /**
   * 上传按钮（使用useMemo缓存，避免每次渲染都创建新对象）
   */
  const uploadButton = useMemo(
    () => (
      <div>
        {uploading ? <LoadingOutlined /> : <PlusOutlined />}
        <div className={styles.uploadText}>上传头像</div>
      </div>
    ),
    [uploading],
  );

  // ==================== 渲染 ====================

  return (
    <Modal
      title="编辑个人信息"
      open={visible}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={loading}
      okText="保存"
      cancelText="取消"
      width={600}
      destroyOnClose  // 关闭时销毁内部组件，重新打开时重新初始化
    >
      <Form form={form} layout="vertical">
        {/* ========== 基本信息 ========== */}
        <div className={styles.sectionTitle}>基本信息</div>

        {/* 头像上传 */}
        <Form.Item label="头像">
          <Upload
            name="avatar"
            listType="picture-card"
            className="avatar-uploader"
            showUploadList={false}
            customRequest={({ file }) => handleUpload(file as RcFile)}
            beforeUpload={beforeUpload}
          >
            {avatarUrl ? (
              // 已有头像，显示图片
              <img src={avatarUrl} alt="avatar" className={styles.avatarImage} />
            ) : (
              // 没有头像，显示上传按钮
              uploadButton
            )}
          </Upload>
        </Form.Item>

        {/* 姓名（每月只能修改一次） */}
        <Form.Item
          name="name"
          label={
            <span>
              姓名
              {/* 不可修改时显示提示图标 */}
              {!nameStatus.canModify && (
                <Tooltip title={`姓名每月只能修改一次，下次可修改时间：${nameStatus.nextModifyDate}`}>
                  <InfoCircleOutlined style={{ marginLeft: 4, color: '#faad14' }} />
                </Tooltip>
              )}
            </span>
          }
          rules={[{ required: true, message: '请输入姓名' }]}
        >
          <Input
            disabled={!nameStatus.canModify}
            placeholder={nameStatus.canModify ? '请输入姓名' : `下次可修改：${nameStatus.nextModifyDate}`}
          />
        </Form.Item>

        {/* 手机号（只读，不能修改） */}
        <Form.Item name="phone" label="手机号">
          <Input disabled />
        </Form.Item>

        {/* 邮箱（只读，不能修改） */}
        <Form.Item name="email" label="邮箱">
          <Input disabled placeholder="未绑定" />
        </Form.Item>

        {/* 性别 */}
        <Form.Item name="gender" label="性别">
          <Radio.Group>
            <Radio value="MALE">男</Radio>
            <Radio value="FEMALE">女</Radio>
          </Radio.Group>
        </Form.Item>

        {/* 所在城市 */}
        <Form.Item name="city" label="所在城市">
          <Select placeholder="请选择城市" options={CITY_OPTIONS} allowClear />
        </Form.Item>

        {/* 工作年限 */}
        <Form.Item name="workYears" label="工作年限">
          <Select placeholder="请选择工作年限" options={WORK_YEARS_OPTIONS} allowClear />
        </Form.Item>

        {/* 最高学历 */}
        <Form.Item name="education" label="最高学历">
          <Select placeholder="请选择学历" options={EDUCATION_OPTIONS} allowClear />
        </Form.Item>

        {/* 求职状态 */}
        <Form.Item name="jobStatus" label="求职状态">
          <Radio.Group>
            <Radio value="JOB_SEEKING">求职中</Radio>
            <Radio value="EMPLOYED_LOOKING">在职看机会</Radio>
            <Radio value="NOT_LOOKING">暂不考虑</Radio>
          </Radio.Group>
        </Form.Item>

        <Divider />

        {/* ========== 求职意向 ========== */}
        <div className={styles.sectionTitle}>求职意向</div>

        {/* 期望岗位 */}
        <Form.Item name="desiredJob" label="期望岗位">
          <Input placeholder="如：前端工程师" />
        </Form.Item>

        {/* 期望城市 */}
        <Form.Item name="desiredCity" label="期望城市">
          <Select placeholder="请选择期望城市" options={CITY_OPTIONS} allowClear />
        </Form.Item>

        {/* 期望薪资（单位：千元） */}
        <Form.Item name="desiredSalary" label="期望薪资">
          <Select placeholder="请选择期望薪资" options={SALARY_OPTIONS} allowClear />
        </Form.Item>

        {/* 到岗时间 */}
        <Form.Item name="availableFrom" label="到岗时间">
          <Select placeholder="请选择到岗时间" options={AVAILABLE_FROM_OPTIONS} allowClear />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default EditProfileModal;
