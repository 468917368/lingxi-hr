import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate, useParams } from 'umi';
import {
  Form,
  Input,
  Select,
  InputNumber,
  Button,
  Card,
  Row,
  Col,
  Space,
  message,
  Modal,
  Tag,
  Alert,
  Checkbox,
  Switch,
} from 'antd';
import {
  ArrowLeftOutlined,
  RobotOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import {
  getHrJobDetail,
  getJobOptions,
  getHrJobOptions,
  createJob,
  updateJob,
  changeJobStatus,
  parseJobJd,
} from '@/services/job';
import type {
  CityOption,
  HrJobDetail,
  HiddenRequirement,
  JdParseResult,
  JobOptionsResponse,
  ParsedSalary,
} from '@/services/job';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { checkJobContent, detectContentRisk } from '@/utils/contentSafety';
import { JobStatus, EducationRequirement } from '@/constants/enums';
import { ROUTES } from '@/constants/routes';
import {
  mapDetailToFormValues,
  buildSalaryPayload,
  buildCreatePayload,
  buildUpdatePayload,
} from './jobFormMapper';
import styles from './index.less';

const { TextArea } = Input;

// JD 解析状态机（本阶段按钮禁用，仅保留渲染，不触发假成功）
type ParseState = 'IDLE' | 'PARSING' | 'SUCCESS' | 'FAILED';

const educationOptions = [
  { value: EducationRequirement.NONE, label: '学历不限' },
  { value: EducationRequirement.COLLEGE, label: '大专' },
  { value: EducationRequirement.BACHELOR, label: '本科' },
  { value: EducationRequirement.MASTER, label: '硕士' },
  { value: EducationRequirement.DOCTOR, label: '博士' },
];

// 岗位类型（后端为自由字符串，仅发布校验非空；提供常见类型，可自定义）
const jobTypeOptions = [
  { value: 'FRONTEND', label: '前端开发' },
  { value: 'JAVA_BACKEND', label: 'Java后端开发' },
  { value: 'GO_BACKEND', label: 'Go后端开发' },
  { value: 'PRODUCT', label: '产品经理' },
  { value: 'UI_DESIGN', label: 'UI设计' },
  { value: 'DATA', label: '数据岗位' },
  { value: 'OPERATION', label: '运营' },
  { value: 'OTHER', label: '其他' },
];

/** 解析学历编码 → 展示（后端 PHD 对齐博士） */
const parseEduLabel = (code?: string): string | undefined => {
  if (!code) return undefined;
  const map: Record<string, string> = {
    NONE: '不限',
    COLLEGE: '大专',
    BACHELOR: '本科',
    MASTER: '硕士',
    PHD: '博士',
  };
  return map[code];
};

/** 解析薪资 → 展示（MONTH 且金额完整才结构化，否则原文/未知） */
const fmtParsedSalary = (salary?: ParsedSalary | null): string => {
  if (!salary) return '薪资未知';
  if (salary.period === 'MONTH' && typeof salary.minAmount === 'number' && typeof salary.maxAmount === 'number') {
    return `${(salary.minAmount / 100).toFixed(0)}-${(salary.maxAmount / 100).toFixed(0)}元/月`;
  }
  return salary.rawText || '薪资未知';
};

// 画像内容字段（命中变化时自动重置画像确认态）
const PROFILE_FIELDS = ['coreSkills', 'softSkills', 'industryExperience', 'interviewFocus', 'jobType', 'hiddenRequirements'];

// 解析结果类型直接使用 services 的 JdParseResult（对齐后端 JdParseResponse，字段可缺省）

const JobCreatePage: React.FC = () => {
  const navigate = useNavigate();
  const params = useParams<{ jobId?: string }>();
  const jobId = params.jobId;
  const isEdit = !!jobId;

  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [editDetail, setEditDetail] = useState<HrJobDetail | null>(null);
  // 画像确认态（P0#1：发布死锁修复，独立于表单）
  const [profileConfirmed, setProfileConfirmed] = useState(false);
  // 编辑回填时抑制画像字段变化触发重置（避免 setFieldsValue 误重置）
  const suppressingReset = useRef(false);
  // JD 解析请求序号：JD 文本变更时递增，使在途解析结果过期（防旧 JD 结果误应用）
  const parseRequestSeq = useRef(0);
  // 画像内容字段是否被用户修改过（决定编辑时三件套是否携带）
  const profileTouched = useRef(false);

  // ===== 表单选项（HR 城市 + 公共行业，后端主数据） =====
  const [cities, setCities] = useState<CityOption[]>([]);
  const [industries, setIndustries] = useState<JobOptionsResponse['industries']>([]);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionsError, setOptionsError] = useState(false);

  // 并行加载公共行业与 HR 城市（与详情请求互不依赖，各自独立发生）
  const loadFormOptions = useCallback(async () => {
    setOptionsLoading(true);
    setOptionsError(false);
    try {
      const [jobOptions, hrOptions] = await Promise.all([
        getJobOptions(),
        getHrJobOptions(),
      ]);
      setIndustries(jobOptions.industries ?? []);
      setCities(hrOptions.cities ?? []);
    } catch {
      setOptionsError(true);
      setIndustries([]);
      setCities([]);
    } finally {
      setOptionsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFormOptions();
  }, [loadFormOptions]);

  // 编辑历史岗位的停用城市回显：详情城市不在 HR options 时补充仅用于展示的选项（不写回全局状态）
  const displayCities = useMemo(() => {
    if (!isEdit || !editDetail?.cityCode || cities.some((item) => item.code === editDetail.cityCode)) {
      return cities;
    }
    return [{ code: editDetail.cityCode, name: editDetail.cityName }, ...cities];
  }, [cities, editDetail, isEdit]);

  // JD 解析状态
  const [parseState, setParseState] = useState<ParseState>('IDLE');
  const [parsedProfile, setParsedProfile] = useState<JdParseResult | null>(null);
  const [parseError, setParseError] = useState('');
  // 已应用的隐性要求（三态：null=未应用保留原画像 / [] =已应用但无有效项明确清空 / 非空=归一化结果）
  const [appliedHiddenRequirements, setAppliedHiddenRequirements] = useState<HiddenRequirement[] | null>(null);
  // 右侧 JD 输入框响应式数据源（getFieldValue 非响应式，setFieldsValue 不触发重渲染）
  const jdText = Form.useWatch('jdText', form);

  // 表单变化时：命中画像内容字段 → 标记画像已修改 + 自动重置确认态
  const handleValuesChange = (changedValues: Record<string, unknown>) => {
    if (suppressingReset.current) return;
    // JD 文本变更后废弃解析结果（Codex 五审 P1）：左侧主表单与右侧输入框共用 jdText 字段，
    // 任一侧修改都应让旧解析结果失效，避免「旧 JD 结果」应用到「新 JD」
    if (changedValues.jdText !== undefined && parseState !== 'IDLE') {
      parseRequestSeq.current++;
      setParsedProfile(null);
      setParseError('');
      setParseState('IDLE');
    }
    if (PROFILE_FIELDS.some((f) => changedValues[f] !== undefined)) {
      profileTouched.current = true;
      setProfileConfirmed(false);
    }
  };

  // 编辑模式加载岗位数据（真实接口）
  useEffect(() => {
    if (isEdit && jobId) {
      suppressingReset.current = true;
      getHrJobDetail(jobId)
        .then((detail) => {
          // 仅 DRAFT 可编辑（后端 updateJob 仅 DRAFT，否则 2104）
          if (detail.status !== JobStatus.DRAFT) {
            message.warning('当前状态的岗位不可编辑');
            navigate(`${ROUTES.HR_JOB_DETAIL}/${jobId}`);
            return;
          }
          setEditDetail(detail);
          setProfileConfirmed(detail.profileConfirmed);
          profileTouched.current = false; // 回填不视为画像修改
          form.setFieldsValue(mapDetailToFormValues(detail));
        })
        .catch(() => {
          message.error('岗位加载失败');
          navigate(ROUTES.HR_JOB);
        })
        .finally(() => {
          suppressingReset.current = false;
        });
    }
  }, [isEdit, jobId, form, navigate]);

  // ===== JD 解析（接入真实接口 parseJobJd） =====
  const handleParseJD = useCallback(async () => {
    if (parseState === 'PARSING') return; // 防重复点击
    // 前置校验（与表单规则对齐：20-20000）
    const text = (form.getFieldValue('jdText') || '').trim();
    if (!text) {
      message.warning('请输入JD内容');
      return;
    }
    if (text.length < 20) {
      message.warning('JD内容至少20个字符');
      return;
    }
    if (text.length > 20000) {
      message.warning('JD内容不能超过20000字符');
      return;
    }
    setParseState('PARSING');
    setParseError('');
    const seq = ++parseRequestSeq.current;
    try {
      const data = await parseJobJd({ jdText: text });
      // JD 文本已变更时丢弃在途结果（竞态防护）
      if (seq !== parseRequestSeq.current) return;
      setParsedProfile(data);
      setParseState('SUCCESS');
    } catch (error) {
      if (seq !== parseRequestSeq.current) return;
      // 任何 reject（含网络/超时/未知）都进 FAILED，避免 PARSING 卡死；具体提示由拦截器弹出
      setParseError(getErrorMessage(error, '解析失败，请稍后重试'));
      setParseState('FAILED');
    }
  }, [form, parseState]);

  const handleReParse = () => {
    setParsedProfile(null);
    setParseState('IDLE');
    handleParseJD();
  };

  /** 应用解析结果到表单（HR 点「应用至表单」= 明确确认） */
  const acceptParsedProfile = () => {
    if (!parsedProfile) return;

    // 组装更新值：画像字段解析有值才覆盖；基础信息仅未手动修改才填
    const updates: Record<string, unknown> = {};

    // 画像字段（覆盖写入，过滤空 name 防空 Tag/空 key）
    const coreSkills = (parsedProfile.coreSkills ?? [])
      .filter((s) => s.name?.trim())
      .map((s) => s.name!.trim());
    const softSkills = (parsedProfile.softSkills ?? [])
      .filter((s) => s.name?.trim())
      .map((s) => s.name!.trim());
    const interviewFocusNames = (parsedProfile.interviewFocus ?? [])
      .filter((f) => f.name?.trim())
      .map((f) => f.name!.trim());
    if (coreSkills.length > 0) updates.coreSkills = coreSkills;
    if (softSkills.length > 0) updates.softSkills = softSkills;
    if (parsedProfile.industryExperience) updates.industryExperience = parsedProfile.industryExperience;
    if (interviewFocusNames.length > 0) updates.interviewFocus = interviewFocusNames.join('、');
    if (parsedProfile.jobType) updates.jobType = parsedProfile.jobType;

    // 基础信息（仅未手动修改才填，避免覆盖已填内容）
    if (!form.isFieldTouched('minExperienceYears') && typeof parsedProfile.minExperienceYears === 'number') {
      updates.minExperienceYears = parsedProfile.minExperienceYears;
    }
    // 学历：PHD→DOCTOR 映射（前端枚举），未知值跳过保留原文
    const edu = parsedProfile.educationRequirement === 'PHD'
      ? EducationRequirement.DOCTOR
      : parsedProfile.educationRequirement;
    if (
      !form.isFieldTouched('educationRequirement') &&
      edu &&
      (['NONE', 'COLLEGE', 'BACHELOR', 'MASTER', 'DOCTOR'] as string[]).includes(edu)
    ) {
      updates.educationRequirement = edu;
    }
    // 薪资：仅 MONTH 且 min/max 完整才填（分→元），薪数未手动改时补默认 13
    const sal = parsedProfile.salary;
    if (
      !form.isFieldTouched('salaryMin') &&
      !form.isFieldTouched('salaryMax') &&
      sal?.period === 'MONTH' &&
      typeof sal.minAmount === 'number' &&
      typeof sal.maxAmount === 'number'
    ) {
      updates.salaryMin = Math.round((sal.minAmount / 100) * 100) / 100;
      updates.salaryMax = Math.round((sal.maxAmount / 100) * 100) / 100;
      if (!form.isFieldTouched('salaryMonths')) updates.salaryMonths = 13;
    }

    // 归一化隐性要求（仅应用时更新；requirement 为空丢弃，basis 补空串，confidence 补 0，inferred/hrConfirmed 固定）
    const normalized: HiddenRequirement[] = (parsedProfile.hiddenRequirements ?? [])
      .filter((h) => h.requirement?.trim())
      .map((h) => ({
        requirement: h.requirement ?? '',
        basis: h.basis ?? '',
        inferred: true,
        confidence: h.confidence ?? 0,
        hrConfirmed: false,
      }));

    const doApply = () => {
      if (Object.keys(updates).length > 0) {
        form.setFieldsValue(updates);
      }
      // 显式重置画像确认态（setFieldsValue 不触发 onValuesChange，Codex 二审 P0-1）
      profileTouched.current = true;
      setProfileConfirmed(false);
      setAppliedHiddenRequirements(normalized);
      message.success('已应用解析结果到表单');
    };

    // 覆盖清单提示：被覆盖的已填画像字段
    const fieldLabels: Record<string, string> = {
      jobType: '岗位类型',
      coreSkills: '核心技能',
      softSkills: '软能力',
      industryExperience: '行业经验',
      interviewFocus: '面试重点',
    };
    const overridden = Object.keys(fieldLabels).filter((field) => {
      const cur = form.getFieldValue(field);
      const hasCur = Array.isArray(cur) ? cur.length > 0 : !!cur;
      return hasCur && updates[field] !== undefined;
    });
    if (overridden.length > 0) {
      Modal.confirm({
        title: '确认应用解析结果？',
        content: `以下已填写的画像字段将被解析结果覆盖：${overridden.map((f) => fieldLabels[f]).join('、')}。`,
        okText: '确认应用',
        cancelText: '取消',
        onOk: doApply,
      });
    } else {
      doApply();
    }
  };

  // 薪资校验：面议开关开启时直接通过；否则走三态逻辑（都空=面议 / 都有值=max>=min / 单侧=拦截）
  const salaryNegotiable = Form.useWatch('salaryNegotiable', form) as boolean | undefined;
  const validateSalary = (): string | null => {
    const values = form.getFieldsValue();
    const result = buildSalaryPayload(
      values.salaryMin,
      values.salaryMax,
      values.salaryMonths,
      values.salaryNegotiable,
    );
    if (!result.ok) {
      return result.message;
    }
    return null;
  };

  // 内容风险确认（Promise 化 Modal.confirm：取消 return 中止保存，确认后才继续）
  const confirmContentRisk = (items: string[]): Promise<boolean> =>
    new Promise((resolve) => {
      Modal.confirm({
        title: '检测到岗位内容包含外部联系方式',
        content: `以下内容可能存在外部引流，发布后将对求职者公开：${items.join('；')}`,
        okText: '确认发布',
        cancelText: '返回修改',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

  // 画像字段失焦轻提示（输入层不阻断，发布层有强确认）
  const handleProfileBlur = (label: string, value: string | undefined) => {
    if (!value) return;
    const result = detectContentRisk(value);
    if (result.highItems.length > 0) {
      message.warning(`${label}包含外部联系方式（${result.highItems.join('、')}），请勿在岗位内容中留个人联系方式`);
    } else if (result.lowItems.length > 0) {
      message.warning(`${label}包含疑似广告词（${result.lowItems.join('、')}），发布后将对求职者公开`);
    }
  };

  // ===== 保存 =====
  const doSave = async (status: JobStatus) => {
    // 防御：options 未就绪时拦截（避免键盘提交绕过禁用按钮）
    if (optionsLoading || optionsError) {
      message.warning('岗位选项尚未加载完成，请稍后重试');
      return;
    }
    try {
      await form.validateFields();
    } catch {
      return; // 表单校验失败，antd 已展示错误
    }

    // 发布前校验
    if (status === JobStatus.PUBLISHED) {
      // 过滤空串后判空（P0#1：Select tags 可能含空串，避免后端反序列化判空拒绝）
      const coreSkills = (form.getFieldValue('coreSkills') || []).filter(
        (s: string) => s && s.trim(),
      );
      if (coreSkills.length === 0) {
        message.warning('发布前请至少添加一项有效核心技能');
        return;
      }
      if (!profileConfirmed) {
        message.warning('请先确认岗位画像后再发布');
        return;
      }
    }

    const salaryErr = validateSalary();
    if (salaryErr) {
      message.warning(salaryErr);
      return;
    }

    const values = form.getFieldsValue();

    // ===== 内容安全检测（仅发布；DRAFT 不检测——草稿不公开） =====
    if (status === JobStatus.PUBLISHED) {
      // 隐性要求必须用"最终提交值"（提交时取 appliedHiddenRequirements ?? editDetail，非表单字段）
      const finalHiddenRequirements =
        appliedHiddenRequirements ?? editDetail?.hiddenRequirements ?? [];
      const { highItems, lowItems } = checkJobContent([
        { label: '岗位名称', value: values.title },
        { label: '岗位描述', value: values.jdText },
        // tags 输入框可自由输入任意文本（软能力/核心技能），同样纳入检测
        { label: '核心技能', value: (values.coreSkills || []).join('、') },
        { label: '软能力', value: (values.softSkills || []).join('、') },
        { label: '行业经验', value: values.industryExperience },
        { label: '面试考察重点', value: values.interviewFocus },
        ...finalHiddenRequirements.map((req, i) => ({
          label: `隐性要求${i + 1}`,
          value: req.requirement,
        })),
      ]);
      // LOW：广告词提示，不阻断
      if (lowItems.length > 0) {
        message.warning(`提示：岗位内容包含疑似广告词（${lowItems.join('、')}），发布后将对求职者公开`);
      }
      // HIGH：外部联系方式需强确认；取消则中止，不进入 setSaving
      if (highItems.length > 0) {
        const ok = await confirmContentRisk(highItems);
        if (!ok) return;
      }
    }

    setSaving(true);
    try {
      const saved = isEdit
        ? await updateJob(
            jobId!,
            buildUpdatePayload(
              {
                ...values,
                profileConfirmed,
                jobType: values.jobType || editDetail?.jobType || '',
                hiddenRequirements: appliedHiddenRequirements ?? editDetail?.hiddenRequirements ?? [],
                profileSource: editDetail?.profileSource || 'MANUAL',
              },
              {
                version: editDetail!.version,
                profileVersion: editDetail!.profileVersion,
                profileChanged: profileTouched.current,
                original: editDetail || undefined,
              },
            ),
          )
        : await createJob(
            buildCreatePayload(
              {
                ...values,
                profileConfirmed,
                jobType: values.jobType || '',
                hiddenRequirements: appliedHiddenRequirements ?? [],
                profileSource: 'MANUAL',
              },
              {},
            ),
          );

      if (status === JobStatus.PUBLISHED) {
        // 两步发布：先保存为 DRAFT → 再用保存响应的新版本发布（P0：避免编辑后旧版本冲突）
        try {
          await changeJobStatus(saved.jobId, {
            action: 'PUBLISH',
            version: saved.version,
            profileVersion: saved.profileVersion,
          });
          message.success('岗位发布成功');
          navigate(`${ROUTES.HR_JOB_DETAIL}/${saved.jobId}`);
        } catch {
          // 保存成功但发布失败：岗位保留为草稿，不重复创建
          message.warning('草稿已保存，发布失败可稍后重试');
          navigate(`${ROUTES.HR_JOB_DETAIL}/${saved.jobId}`);
        }
      } else {
        message.success(isEdit ? '岗位更新成功' : '岗位创建成功');
        navigate(`${ROUTES.HR_JOB_DETAIL}/${saved.jobId}`);
      }
    } catch (error) {
      // 编辑乐观锁冲突（2102）：保留表单让用户复制内容，不刷新覆盖用户输入
      if (getErrorCode(error) === 2102 && isEdit) {
        message.warning('数据已被其他人修改，请复制当前填写内容后刷新重试');
      }
      // 其余错误拦截器已提示，页面停留保留表单
    } finally {
      // P1#1：统一恢复按钮态，避免 2102 分支提前 return 导致永久 loading
      setSaving(false);
    }
  };

  const handleSaveDraft = () => {
    doSave(JobStatus.DRAFT);
  };

  const handlePublish = () => {
    Modal.confirm({
      title: '确认发布',
      content: '发布后岗位将对所有求职者可见。请确认岗位信息和HC数据准确。',
      okText: '确认发布',
      cancelText: '再检查一下',
      onOk: () => doSave(JobStatus.PUBLISHED),
    });
  };

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title={isEdit ? '编辑岗位' : '创建岗位'}
        desc={isEdit ? '修改岗位信息并保存' : '填写岗位信息并发布招聘'}
        extra={
          <Button
            className={styles.backBtn}
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(ROUTES.HR_JOB)}
          >
            返回列表
          </Button>
        }
      />

      <Row gutter={24}>
        <Col xs={24} lg={16}>
          {/* 基础信息 */}
          <Card className={styles.card} bordered={false}>
            <div className={styles.sectionTitle}>基本信息</div>
            {optionsError && (
              <Alert
                type="error"
                message="岗位选项加载失败，暂不能保存，请重试。"
                action={
                  <Button size="small" onClick={loadFormOptions}>重试</Button>
                }
                className={styles.alertBottom}
              />
            )}
            <Form
              form={form}
              layout="vertical"
              initialValues={{
                salaryMonths: 13,
                totalHc: 1,
                educationRequirement: EducationRequirement.BACHELOR,
              }}
              onValuesChange={handleValuesChange}
            >
              <Form.Item
                name="title"
                label="岗位名称"
                rules={[
                  { required: true, message: '请输入岗位名称' },
                  { min: 2, max: 100, message: '岗位名称2-100字符' },
                ]}
              >
                <Input placeholder="例如: 高级前端工程师" className={styles.formInput} />
              </Form.Item>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="cityCode"
                    label="工作城市"
                    rules={[{ required: true, message: '请选择城市' }]}
                  >
                    <Select
                      placeholder="选择城市"
                      options={displayCities.map((item) => ({ value: item.code, label: item.name }))}
                      loading={optionsLoading}
                      disabled={optionsLoading || optionsError}
                      className={styles.formSelect}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="industryGroupCode"
                    label="所属行业"
                    rules={[{ required: true, message: '请选择行业' }]}
                  >
                    <Select
                      placeholder="选择行业"
                      options={industries.map((item) => ({ value: item.groupCode, label: item.name }))}
                      loading={optionsLoading}
                      disabled={optionsLoading || optionsError}
                      className={styles.formSelect}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="minExperienceYears"
                    label="最低工作年限"
                    rules={[{ required: true, message: '请输入' }]}
                  >
                    <InputNumber min={0} max={50} placeholder="年" className={styles.formInputFull} addonAfter="年" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="educationRequirement" label="学历要求">
                    <Select placeholder="选择学历" options={educationOptions} className={styles.formSelect} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="totalHc"
                label="招聘人数 (HC)"
                rules={[
                  { required: true, message: '请输入HC' },
                  { type: 'number', min: 1, max: 999, message: 'HC范围1-999' },
                ]}
              >
                <InputNumber min={1} max={999} placeholder="HC" className={styles.formInputFull} addonAfter="人" />
              </Form.Item>

              <div className={styles.salaryTitleRow}>
                <div className={styles.sectionTitle}>薪资范围</div>
                <Form.Item name="salaryNegotiable" valuePropName="checked" noStyle>
                  <Switch
                    checkedChildren="面议"
                    unCheckedChildren="面议"
                    onChange={(checked) => {
                      if (checked) {
                        // 开启面议：清空薪资字段，避免残留脏数据随表单提交
                        form.setFieldsValue({ salaryMin: null, salaryMax: null, salaryMonths: null });
                      }
                    }}
                  />
                </Form.Item>
              </div>
              {!salaryNegotiable && (
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item
                      name="salaryMin"
                      label="最低薪资"
                      rules={[{ required: true, message: '请填写最低薪资' }]}
                    >
                      <InputNumber
                        min={0} placeholder="最低"
                        className={styles.formInputFull}
                        formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                        addonAfter="元/月"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item
                      name="salaryMax"
                      label="最高薪资"
                      rules={[{ required: true, message: '请填写最高薪资' }]}
                    >
                      <InputNumber
                        min={0} placeholder="最高"
                        className={styles.formInputFull}
                        formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                        addonAfter="元/月"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item
                      name="salaryMonths"
                      label="薪数"
                      rules={[{ required: true, message: '请填写薪数' }]}
                    >
                      <InputNumber min={12} max={24} className={styles.formInputFull} addonAfter="薪" />
                    </Form.Item>
                  </Col>
                </Row>
              )}

              <Form.Item
                name="jdText"
                label="岗位描述 (JD)"
                rules={[
                  { required: true, message: '请输入JD内容' },
                  { min: 20, max: 20000, message: 'JD内容20-20000字符' },
                ]}
              >
                <TextArea rows={8} placeholder="请详细描述岗位职责和任职要求..." className={styles.formTextarea} />
              </Form.Item>
            </Form>
          </Card>

          {/* 人才画像 */}
          <Card className={`${styles.card} ${styles.cardGap}`} bordered={false}>
            <div className={styles.sectionTitle}>岗位画像</div>
            <Form form={form} layout="vertical" onValuesChange={handleValuesChange}>
              {/* 岗位类型（P0#1：独立选择器，不再用行业编码兜底） */}
              <Form.Item
                name="jobType"
                label="岗位类型"
                rules={[{ required: true, message: '请选择岗位类型' }]}
              >
                <Select
                  placeholder="选择岗位类型"
                  options={jobTypeOptions}
                  showSearch
                  allowClear={false}
                  className={styles.formSelect}
                />
              </Form.Item>

              {/* 核心技能（P0#1：可输入可编辑，Select tags 模式，保存前过滤空串） */}
              <Form.Item
                name="coreSkills"
                label="核心技能"
                rules={[{ required: true, message: '请至少填写一项核心技能' }]}
              >
                <Select
                  mode="tags"
                  placeholder="输入技能名称后回车添加，如 React / Java"
                  className={styles.formSelect}
                  tokenSeparators={[',', '、']}
                  onBlur={() =>
                    handleProfileBlur('核心技能', (form.getFieldValue('coreSkills') || []).join('、'))
                  }
                />
              </Form.Item>

              <Form.Item name="softSkills" label="软能力">
                <Select
                  mode="tags"
                  placeholder="输入软能力后回车添加，如 沟通协作"
                  className={styles.formSelect}
                  tokenSeparators={[',', '、']}
                  onBlur={() =>
                    handleProfileBlur('软能力', (form.getFieldValue('softSkills') || []).join('、'))
                  }
                />
              </Form.Item>

              <Form.Item name="industryExperience" label="行业经验要求">
                <TextArea
                  rows={2}
                  placeholder="例如: 互联网行业，有电商/企业服务方向经验优先"
                  className={styles.formTextarea}
                  onBlur={(e) => handleProfileBlur('行业经验', e.target.value)}
                />
              </Form.Item>

              <Form.Item name="interviewFocus" label="面试考察重点">
                <Input
                  placeholder="例如: React原理深度、性能优化经验、工程化能力（多个用顿号分隔）"
                  className={styles.formInput}
                  onBlur={(e) => handleProfileBlur('面试考察重点', e.target.value)}
                />
              </Form.Item>

              {/* 我已确认岗位画像 checkbox（P0#1：保存并发布必须 true；修改画像内容自动重置） */}
              <Form.Item>
                <Checkbox
                  checked={profileConfirmed}
                  onChange={(e) => {
                    setProfileConfirmed(e.target.checked);
                    // P0#2：确认状态修改也是画像三件套变更，需携带提交（否则编辑仅勾选确认不落库）
                    profileTouched.current = true;
                  }}
                >
                  我已确认岗位画像（保存并发布时必须勾选；修改画像内容后将自动取消，需重新确认）
                </Checkbox>
              </Form.Item>
            </Form>
          </Card>

          {/* 操作按钮 */}
          <div className={styles.actionBar}>
            <Button className={styles.ghostBtn} onClick={() => navigate(ROUTES.HR_JOB)}>取消</Button>
            <Space>
              <Button
                className={styles.outlineBtn}
                onClick={handleSaveDraft}
                loading={saving}
                disabled={optionsLoading || optionsError}
              >
                保存草稿
              </Button>
              <Button
                type="primary" className={styles.accentBtn}
                onClick={handlePublish}
                loading={saving}
                disabled={optionsLoading || optionsError}
              >
                保存并发布
              </Button>
            </Space>
          </div>
        </Col>

        {/* 右侧: JD 解析面板（本阶段禁用，不保留 mock 假成功） */}
        <Col xs={24} lg={8}>
          <Card className={styles.card} bordered={false}>
            <div className={styles.sectionTitle}>
              <RobotOutlined className={styles.aiIcon} /> JD 智能解析
            </div>
            <TextArea
              rows={6}
              placeholder="粘贴JD内容，AI自动解析出技能要求和人才画像..."
              value={jdText || ''}
              maxLength={20000}
              onChange={(e) => {
                form.setFieldsValue({ jdText: e.target.value });
                // JD 变更后废弃解析结果（含 PARSING 在途），与左侧主表单行为一致（Codex 五审 P1）
                if (parseState !== 'IDLE') {
                  parseRequestSeq.current++;
                  setParsedProfile(null);
                  setParseError('');
                  setParseState('IDLE');
                }
              }}
              className={styles.formTextarea}
            />

            <Button
              type="primary" block
              className={`${styles.aiBtn} ${styles.aiBtnGap}`}
              icon={<RobotOutlined />}
              onClick={handleParseJD}
              loading={parseState === 'PARSING'}
              disabled={parseState === 'PARSING'}
            >
              AI 解析 JD
            </Button>

            {parseState === 'SUCCESS' && parsedProfile && (
              <div className={styles.parsedResult}>
                <div className={styles.parsedHeader}>
                  <CheckCircleOutlined className={styles.successIcon} />
                  <span>解析完成</span>
                  <Space size={4}>
                    <Button type="link" size="small" className={styles.applyBtn} onClick={acceptParsedProfile}>
                      应用至表单
                    </Button>
                    <Button type="link" size="small" onClick={handleReParse}>重新解析</Button>
                  </Space>
                </div>

                {(parsedProfile.warnings ?? []).length > 0 && (
                  <div className={styles.warningsBox}>
                    {parsedProfile.warnings?.map((w, i) => (
                      <div key={i} className={styles.warningItem}>
                        <ExclamationCircleOutlined /> {w}
                      </div>
                    ))}
                  </div>
                )}

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>岗位类型</div>
                  <div className={styles.parsedText}>{parsedProfile.jobType || '-'}</div>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>核心技能</div>
                  <Space wrap size={[0, 4]}>
                    {(parsedProfile.coreSkills ?? [])
                      .filter((s) => s.name?.trim())
                      .map((s) => (
                        <span key={s.name} className={styles.parsedTag}>
                          {s.name}
                        </span>
                      ))}
                  </Space>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>软能力</div>
                  <Space wrap size={[0, 4]}>
                    {(parsedProfile.softSkills ?? [])
                      .filter((s) => s.name?.trim())
                      .map((s) => (
                        <span key={s.name} className={styles.parsedTagSoft}>
                          {s.name}
                          {s.inferred && <span className={styles.aiHint}> (AI推断)</span>}
                        </span>
                      ))}
                  </Space>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>行业经验</div>
                  <div className={styles.parsedText}>{parsedProfile.industryExperience || '-'}</div>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>面试重点</div>
                  <Space wrap size={[0, 4]}>
                    {(parsedProfile.interviewFocus ?? [])
                      .filter((f) => f.name?.trim())
                      .map((f) => (
                        <Tag key={f.name}>{f.name}</Tag>
                      ))}
                  </Space>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>经验/学历/薪资</div>
                  <div className={styles.parsedText}>
                    {typeof parsedProfile.minExperienceYears === 'number'
                      ? `${parsedProfile.minExperienceYears}年`
                      : '经验未知'}
                    {' · '}
                    {parseEduLabel(parsedProfile.educationRequirement) ?? '学历未知'}
                    {' · '}
                    {fmtParsedSalary(parsedProfile.salary)}
                  </div>
                </div>

                <div className={styles.parsedSection}>
                  <div className={styles.parsedLabel}>隐性要求</div>
                  <div className={styles.parsedText}>{parsedProfile.hiddenRequirements?.length ?? 0} 条</div>
                </div>
              </div>
            )}

            {parseState === 'FAILED' && (
              <div className={styles.failedBox}>
                <Alert
                  type="error"
                  message="解析失败"
                  description={parseError || 'AI解析暂不可用，请手工填写表单。'}
                  showIcon
                  action={
                    <Button size="small" onClick={handleParseJD}>重试</Button>
                  }
                />
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default JobCreatePage;
