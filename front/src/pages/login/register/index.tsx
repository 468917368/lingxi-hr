import React, { useState, useCallback, useEffect, useRef } from 'react';
import { Input, Select, Typography, message, Steps, Upload, Button } from 'antd';
import {
  UserOutlined, TeamOutlined, ArrowLeftOutlined,
  SearchOutlined, KeyOutlined, BankOutlined,
  PhoneOutlined, LockOutlined, UploadOutlined,
  RocketOutlined, SafetyOutlined, ThunderboltOutlined,
  RobotOutlined, BulbOutlined, RightOutlined,
  CheckCircleOutlined, StarOutlined, TrophyOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'umi';
import { useRegister, type RegisterFormState } from '@/hooks/useRegister';
import { sendCode } from '@/services/auth';
import { updateUserInfo, updateProfile } from '@/services/user';
import { joinByInvite, applyCertification } from '@/services/company';
import useUserStore from '@/stores/userStore';
import { isApiError } from '@/utils/apiError';
import styles from './index.less';

const { Text } = Typography;

/** 动态数字计数器 */
function useCounter(target: number, duration = 2000, startDelay = 800) {
  const [value, setValue] = useState(0);

  useEffect(() => {
    setValue(0);
    const timer = setTimeout(() => {
      const startTime = Date.now();
      const tick = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        setValue(Math.round(eased * target));
        if (progress < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    }, startDelay);
    return () => clearTimeout(timer);
  }, [target, duration, startDelay]);

  return value;
}

/** 打字机效果 */
function useTypewriter(texts: string[], typeSpeed = 80, deleteSpeed = 40, pauseTime = 2000) {
  const [displayText, setDisplayText] = useState('');
  const [textIndex, setTextIndex] = useState(0);
  const [charIndex, setCharIndex] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    const currentText = texts[textIndex];
    let timer: ReturnType<typeof setTimeout>;

    if (!isDeleting && charIndex < currentText.length) {
      timer = setTimeout(() => {
        setDisplayText(currentText.substring(0, charIndex + 1));
        setCharIndex(charIndex + 1);
      }, typeSpeed);
    } else if (!isDeleting && charIndex === currentText.length) {
      timer = setTimeout(() => setIsDeleting(true), pauseTime);
    } else if (isDeleting && charIndex > 0) {
      timer = setTimeout(() => {
        setDisplayText(currentText.substring(0, charIndex - 1));
        setCharIndex(charIndex - 1);
      }, deleteSpeed);
    } else if (isDeleting && charIndex === 0) {
      setIsDeleting(false);
      setTextIndex((textIndex + 1) % texts.length);
    }

    return () => clearTimeout(timer);
  }, [charIndex, isDeleting, textIndex, texts, typeSpeed, deleteSpeed, pauseTime]);

  return displayText;
}

type FlowStep =
  | 'role'
  | 'hrType'
  | 'candidateStep1' | 'candidateStep2' | 'candidateStep3'
  | 'hrJoinStep1' | 'hrJoinStep2'
  | 'hrNewStep1' | 'hrNewStep2';

const cityOptions = [
  { value: '北京', label: '北京' }, { value: '上海', label: '上海' },
  { value: '广州', label: '广州' }, { value: '深圳', label: '深圳' },
  { value: '杭州', label: '杭州' }, { value: '成都', label: '成都' },
  { value: '武汉', label: '武汉' }, { value: '南京', label: '南京' },
  { value: '西安', label: '西安' }, { value: '苏州', label: '苏州' },
];

const industryOptions = [
  { value: '互联网/电商', label: '互联网/电商' },
  { value: '企业服务/SaaS', label: '企业服务/SaaS' },
  { value: '大数据/AI', label: '大数据/AI' },
  { value: '金融科技', label: '金融科技' },
  { value: '游戏', label: '游戏' },
  { value: '教育培训', label: '教育培训' },
  { value: '医疗健康', label: '医疗健康' },
  { value: '制造业', label: '制造业' },
];

const scaleOptions = [
  { value: '1-49人', label: '1-49人' }, { value: '50-99人', label: '50-99人' },
  { value: '100-499人', label: '100-499人' }, { value: '500-999人', label: '500-999人' },
  { value: '1000人以上', label: '1000人以上' },
];

const workYearsOptions = [
  { value: 'FRESH', label: '应届' }, { value: '1-3', label: '1-3年' },
  { value: '3-5', label: '3-5年' }, { value: '5-10', label: '5-10年' },
  { value: '10+', label: '10年以上' },
];

const educationOptions = [
  { value: 'COLLEGE', label: '大专' }, { value: 'BACHELOR', label: '本科' },
  { value: 'MASTER', label: '硕士' }, { value: 'PHD', label: '博士' },
];

const jobStatusOptions = [
  { value: 'JOB_SEEKING', label: '求职中' },
  { value: 'EMPLOYED_LOOKING', label: '在职看机会' },
  { value: 'FRESH', label: '应届生' },
];

const salaryOptions = [
  { value: '5000-10000', label: '5K-10K' }, { value: '10000-15000', label: '10K-15K' },
  { value: '15000-25000', label: '15K-25K' }, { value: '25000-35000', label: '25K-35K' },
  { value: '35000-50000', label: '35K-50K' }, { value: '50000', label: '50K+' },
];

const isValidPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone);

/** 每一步的营销内容配置 - 每步不同视觉风格 */
const stepMarketingContent: Record<string, {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  features: { icon: React.ReactNode; text: string }[];
  bgImage: string;
  overlayStyle: React.CSSProperties;
  stats: { value: number; label: string; suffix?: string }[];
  typewriterTexts: string[];
}> = {
  // 角色选择 - 温暖欢迎风格
  role: {
    icon: <RocketOutlined />,
    title: '开启你的职业新篇章',
    subtitle: '灵犀互聘，AI驱动的智能招聘平台',
    features: [
      { icon: <RobotOutlined />, text: 'AI智能匹配，精准推荐岗位' },
      { icon: <SafetyOutlined />, text: '企业直招，信息真实可靠' },
      { icon: <ThunderboltOutlined />, text: '一键投递，实时追踪进度' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(160deg, rgba(255, 107, 107, 0.75) 0%, rgba(255, 142, 114, 0.6) 40%, rgba(255, 160, 122, 0.4) 100%)',
    },
    stats: [
      { value: 5000, label: '优质岗位', suffix: '+' },
      { value: 1200, label: '合作企业', suffix: '+' },
      { value: 98, label: '满意度', suffix: '%' },
    ],
    typewriterTexts: ['找到理想工作', '获得AI智能推荐', '开启职业新篇章'],
  },
  // 验证手机 - 安全信任风格
  candidateStep1: {
    icon: <SafetyOutlined />,
    title: '安全注册，30秒搞定',
    subtitle: '手机号验证，保障账户安全',
    features: [
      { icon: <LockOutlined />, text: '数据加密传输，隐私安全有保障' },
      { icon: <PhoneOutlined />, text: '手机号一键验证，快速注册' },
      { icon: <BulbOutlined />, text: '注册即享AI求职助手服务' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.7) 0%, rgba(52, 211, 153, 0.5) 50%, rgba(110, 231, 183, 0.3) 100%)',
    },
    stats: [
      { value: 30, label: '秒完成注册', suffix: '' },
      { value: 100, label: '数据加密', suffix: '%' },
      { value: 24, label: '小时服务', suffix: 'h' },
    ],
    typewriterTexts: ['安全可靠', '极速注册', '隐私保护'],
  },
  // 个人信息 - 专业商务风格
  candidateStep2: {
    icon: <UserOutlined />,
    title: '完善信息，提升匹配度',
    subtitle: '填写个人信息，让AI更懂你',
    features: [
      { icon: <RobotOutlined />, text: 'AI分析你的优势，智能推荐' },
      { icon: <ThunderboltOutlined />, text: '匹配度提升30%，offer更近' },
      { icon: <BulbOutlined />, text: '信息越完整，推荐越精准' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1552664730-d307ca884678?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(160deg, rgba(99, 102, 241, 0.7) 0%, rgba(129, 140, 248, 0.5) 50%, rgba(165, 180, 252, 0.3) 100%)',
    },
    stats: [
      { value: 30, label: '匹配度提升', suffix: '%' },
      { value: 500, label: '企业HR在线', suffix: '+' },
      { value: 95, label: '推荐准确率', suffix: '%' },
    ],
    typewriterTexts: ['让AI更懂你', '精准匹配', '智能推荐'],
  },
  // 求职意向 - 活力进取风格
  candidateStep3: {
    icon: <RocketOutlined />,
    title: '设置求职意向，精准匹配',
    subtitle: '告诉AI你想要什么，剩下的交给它',
    features: [
      { icon: <SearchOutlined />, text: '5000+优质岗位，等你来挑' },
      { icon: <RobotOutlined />, text: 'AI模拟面试，提升通过率' },
      { icon: <ThunderboltOutlined />, text: '实时推送匹配岗位，不错过机会' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.7) 0%, rgba(251, 191, 36, 0.5) 50%, rgba(253, 224, 71, 0.3) 100%)',
    },
    stats: [
      { value: 72, label: '小时收到offer', suffix: 'h' },
      { value: 85, label: '面试通过率', suffix: '%' },
      { value: 1000, label: '成功入职', suffix: '+' },
    ],
    typewriterTexts: ['精准匹配岗位', '快速拿offer', '开启新旅程'],
  },
  // HR类型选择 - 专业商务风格
  hrType: {
    icon: <TeamOutlined />,
    title: '选择入驻方式',
    subtitle: '加入灵犀互聘，开启智能招聘之旅',
    features: [
      { icon: <RobotOutlined />, text: 'AI智能筛选，精准匹配人才' },
      { icon: <ThunderboltOutlined />, text: '一键发布岗位，触达优质候选人' },
      { icon: <SafetyOutlined />, text: '企业认证，提升招聘可信度' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1552664730-d307ca884678?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(160deg, rgba(14, 165, 233, 0.75) 0%, rgba(56, 189, 248, 0.55) 40%, rgba(125, 211, 252, 0.35) 100%)',
    },
    stats: [
      { value: 1200, label: '合作企业', suffix: '+' },
      { value: 50000, label: '优质人才', suffix: '+' },
      { value: 95, label: '企业满意度', suffix: '%' },
    ],
    typewriterTexts: ['高效招聘', '精准匹配', '智能筛选'],
  },
  // HR加入企业 - 邀请码验证
  hrJoinStep1: {
    icon: <SafetyOutlined />,
    title: '安全验证，加入团队',
    subtitle: '手机号验证，保障企业信息安全',
    features: [
      { icon: <LockOutlined />, text: '企业级安全，数据加密传输' },
      { icon: <PhoneOutlined />, text: '手机号验证，快速加入团队' },
      { icon: <BulbOutlined />, text: '加入后即可使用全部招聘功能' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(135deg, rgba(14, 165, 233, 0.7) 0%, rgba(56, 189, 248, 0.5) 50%, rgba(125, 211, 252, 0.3) 100%)',
    },
    stats: [
      { value: 30, label: '秒完成验证', suffix: '' },
      { value: 100, label: '数据加密', suffix: '%' },
      { value: 24, label: '小时服务', suffix: 'h' },
    ],
    typewriterTexts: ['安全可靠', '快速加入', '团队协作'],
  },
  // HR加入企业 - 输入邀请码
  hrJoinStep2: {
    icon: <KeyOutlined />,
    title: '输入邀请码，加入企业',
    subtitle: '邀请码由企业管理员提供',
    features: [
      { icon: <TeamOutlined />, text: '加入企业团队，协同招聘' },
      { icon: <SearchOutlined />, text: '共享人才库，提升招聘效率' },
      { icon: <ThunderboltOutlined />, text: '即时通讯，候选人沟通更便捷' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(160deg, rgba(14, 165, 233, 0.75) 0%, rgba(56, 189, 248, 0.55) 40%, rgba(125, 211, 252, 0.35) 100%)',
    },
    stats: [
      { value: 1200, label: '合作企业', suffix: '+' },
      { value: 50, label: '平均招聘周期', suffix: '天' },
      { value: 90, label: '招聘成功率', suffix: '%' },
    ],
    typewriterTexts: ['团队协作', '高效招聘', '智能管理'],
  },
  // HR新企业 - 手机验证
  hrNewStep1: {
    icon: <SafetyOutlined />,
    title: '安全注册，企业入驻',
    subtitle: '手机号验证，开启企业招聘之旅',
    features: [
      { icon: <LockOutlined />, text: '企业级安全保障' },
      { icon: <PhoneOutlined />, text: '手机号快速验证' },
      { icon: <BulbOutlined />, text: '注册即享AI招聘助手' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(135deg, rgba(14, 165, 233, 0.7) 0%, rgba(56, 189, 248, 0.5) 50%, rgba(125, 211, 252, 0.3) 100%)',
    },
    stats: [
      { value: 30, label: '秒完成注册', suffix: '' },
      { value: 100, label: '数据加密', suffix: '%' },
      { value: 24, label: '小时服务', suffix: 'h' },
    ],
    typewriterTexts: ['安全注册', '快速入驻', 'AI赋能'],
  },
  // HR新企业 - 填写企业信息
  hrNewStep2: {
    icon: <BankOutlined />,
    title: '完善企业信息，提升招聘效率',
    subtitle: '填写企业资料，AI将为您精准匹配人才',
    features: [
      { icon: <RobotOutlined />, text: 'AI分析岗位需求，智能推荐人才' },
      { icon: <ThunderboltOutlined />, text: '企业认证后，招聘效率提升50%' },
      { icon: <BulbOutlined />, text: '信息越完整，推荐越精准' },
    ],
    bgImage: 'https://images.unsplash.com/photo-1497366216548-37526070297c?w=1200&h=900&fit=crop',
    overlayStyle: {
      background: 'linear-gradient(160deg, rgba(14, 165, 233, 0.75) 0%, rgba(56, 189, 248, 0.55) 40%, rgba(125, 211, 252, 0.35) 100%)',
    },
    stats: [
      { value: 50, label: '招聘效率提升', suffix: '%' },
      { value: 1000, label: '优质人才', suffix: '+' },
      { value: 98, label: '企业满意度', suffix: '%' },
    ],
    typewriterTexts: ['企业入驻', '人才匹配', '高效招聘'],
  },
};

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const {
    loading, setLoading, countdown, setCountdown, sending, setSending,
    validateStep1, doRegister, completeRegistration,
  } = useRegister();

  const [step, setStep] = useState<FlowStep>('role');

  // ===== Common Step 1 fields =====
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [name, setName] = useState('');

  // ===== Candidate Step 2 =====
  const [gender, setGender] = useState<'MALE' | 'FEMALE' | undefined>(undefined);
  const [city, setCity] = useState<string | undefined>(undefined);
  const [workYears, setWorkYears] = useState<string | undefined>(undefined);
  const [education, setEducation] = useState<string | undefined>(undefined);
  const [jobStatus, setJobStatus] = useState('JOB_SEEKING');

  // ===== Candidate Step 3 =====
  const [desiredJob, setDesiredJob] = useState('');
  const [desiredCity, setDesiredCity] = useState<string | undefined>(undefined);
  const [desiredSalary, setDesiredSalary] = useState<string | undefined>(undefined);
  const [availableFrom, setAvailableFrom] = useState('ASAP');

  // ===== HR - Join existing company =====
  const [inviteCode, setInviteCode] = useState('');

  // ===== HR - New company =====
  const [companyName, setCompanyName] = useState('');
  const [companyIndustry, setCompanyIndustry] = useState<string | undefined>(undefined);
  const [companyScale, setCompanyScale] = useState<string | undefined>(undefined);
  const [companyAddress, setCompanyAddress] = useState<string | undefined>(undefined);
  const [businessLicense, setBusinessLicense] = useState<File | null>(null);

  // 倒计时
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown, setCountdown]);

  // 判断是否使用分屏布局（所有流程都使用分屏）
  const isSplitLayout = step === 'role' || step === 'hrType' ||
    step === 'candidateStep1' || step === 'candidateStep2' || step === 'candidateStep3' ||
    step === 'hrJoinStep1' || step === 'hrJoinStep2' ||
    step === 'hrNewStep1' || step === 'hrNewStep2';

  // 获取当前步骤的营销内容
  const marketing = stepMarketingContent[step] || stepMarketingContent.role;

  // 获取表单数据
  const getFormState = useCallback((): RegisterFormState => ({
    phone, code, password, confirmPassword, name,
  }), [phone, code, password, confirmPassword, name]);

  // 发送验证码
  const handleSendCode = useCallback(async () => {
    if (!isValidPhone(phone)) {
      message.warning('请输入正确的手机号');
      return;
    }
    setSending(true);
    try {
      await sendCode({ phone });
      message.success('验证码已发送');
      setCountdown(60);
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
          case 1017:
            message.error('该手机号已注册，请直接登录');
            break;
          default:
            break;
        }
      }
    } finally {
      setSending(false);
    }
  }, [phone, setSending]);

  // ===== Step 1 处理（共享逻辑）=====
  const handleStep1 = useCallback(async (role: 'CANDIDATE' | 'HR', nextStep: FlowStep) => {
    if (!validateStep1(getFormState())) return;
    const success = await doRegister(getFormState(), role);
    if (success) {
      setStep(nextStep);
    }
  }, [validateStep1, doRegister, getFormState]);

  /** 转换到岗时间为日期格式 */
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

  // ===== 求职者 Step 3 → 完成 =====
  const handleCandidateStep3 = useCallback(async () => {
    if (!completeRegistration()) return;
    setLoading(true);
    try {
      await updateUserInfo({ gender, city, workYears, education, jobStatus });
      const [min, max] = desiredSalary ? desiredSalary.split('-').map(Number) : [undefined, undefined];
      await updateProfile({
        desiredJob, desiredCity, desiredSalaryMin: min, desiredSalaryMax: max,
        availableFrom: convertAvailableFrom(availableFrom),
      });
      message.success('注册完成，欢迎加入灵犀互聘！');
      navigate('/candidate/home');
    } catch {
      // 错误已在拦截器中处理
    } finally {
      setLoading(false);
    }
  }, [completeRegistration, gender, city, workYears, education, jobStatus, desiredJob, desiredCity, desiredSalary, availableFrom, navigate, setLoading]);

  // ===== HR 邀请码 Step 2 → 完成 =====
  const handleHRJoinStep2 = useCallback(async () => {
    if (!completeRegistration()) return;
    if (!inviteCode.trim()) { message.warning('请输入邀请码'); return; }
    setLoading(true);
    try {
      const res = await joinByInvite(inviteCode);
      const { userInfo } = useUserStore.getState();
      if (userInfo) {
        useUserStore.setState({
          userInfo: { ...userInfo, companyId: res.companyId, companyName: res.companyName },
        });
      }
      message.success('已成功加入企业');
      navigate('/hr/dashboard');
    } catch {
      // 错误已在拦截器中处理
    } finally {
      setLoading(false);
    }
  }, [completeRegistration, inviteCode, navigate, setLoading]);

  // ===== HR 新企业 Step 2 → 提交 =====
  const handleHRNewStep2 = useCallback(async () => {
    if (!completeRegistration()) return;
    if (!companyName.trim()) { message.warning('请输入企业名称'); return; }
    if (!companyIndustry) { message.warning('请选择行业'); return; }
    if (!companyScale) { message.warning('请选择企业规模'); return; }
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('name', companyName);
      formData.append('industry', companyIndustry);
      formData.append('scale', companyScale);
      if (companyAddress) formData.append('address', companyAddress);
      if (businessLicense) formData.append('businessLicense', businessLicense);
      await applyCertification(formData);
      message.success('企业认证申请已提交');
      navigate('/certification/pending');
    } catch {
      // 错误已在拦截器中处理
    } finally {
      setLoading(false);
    }
  }, [completeRegistration, companyName, companyIndustry, companyScale, companyAddress, businessLicense, navigate, setLoading]);

  const goBack = useCallback(() => {
    switch (step) {
      case 'hrType': setStep('role'); break;
      case 'candidateStep1': case 'hrJoinStep1': case 'hrNewStep1': setStep('role'); break;
      case 'candidateStep2': setStep('candidateStep1'); break;
      case 'candidateStep3': setStep('candidateStep2'); break;
      case 'hrJoinStep2': setStep('hrType'); break;
      case 'hrNewStep2': setStep('hrType'); break;
      default: setStep('role');
    }
  }, [step]);

  // ===== 动态统计数字 =====
  const counter1 = useCounter(marketing.stats[0]?.value || 0, 2000, 1000);
  const counter2 = useCounter(marketing.stats[1]?.value || 0, 2000, 1200);
  const counter3 = useCounter(marketing.stats[2]?.value || 0, 1800, 1400);

  // ===== 打字机效果 =====
  const typewriterText = useTypewriter(marketing.typewriterTexts);

  // ===== 左侧营销面板（每步不同风格）=====
  const renderLeftPanel = () => (
    <div className={styles.leftPanel}>
      <div className={styles.leftBg} style={{ backgroundImage: `url(${marketing.bgImage})` }} />
      <div className={styles.leftOverlay} style={marketing.overlayStyle} />

      {/* 浮动粒子 */}
      <div className={styles.leftParticles}>
        {[...Array(5)].map((_, i) => (
          <div key={i} className={styles.leftParticle} />
        ))}
      </div>

      <div className={styles.leftContent}>
        <div className={styles.leftTop}>
          <div className={styles.leftBrand}>
            <div className={styles.leftBrandMark}>L</div>
            <span className={styles.leftBrandText}>灵犀互聘</span>
          </div>
        </div>

        <div className={styles.leftMain} key={step}>
          <div className={styles.leftIconWrap}>
            {marketing.icon}
          </div>
          <h2 className={styles.leftTitle}>{marketing.title}</h2>
          <p className={styles.leftSubtitle}>{marketing.subtitle}</p>

          {/* 打字机效果 */}
          <div className={styles.typewriterWrap}>
            <span className={styles.typewriterText}>{typewriterText}</span>
            <span className={styles.typewriterCursor}>|</span>
          </div>

          <div className={styles.leftFeatures}>
            {marketing.features.map((feature, index) => (
              <div key={index} className={styles.leftFeature}>
                <div className={styles.leftFeatureIcon}>
                  {feature.icon}
                </div>
                <span>{feature.text}</span>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.leftBottom}>
          <div className={styles.leftStats}>
            {marketing.stats.map((stat, index) => {
              const counters = [counter1, counter2, counter3];
              return (
                <div key={index} className={styles.leftStat}>
                  <span className={styles.leftStatNum}>
                    {counters[index]}{stat.suffix}
                  </span>
                  <span className={styles.leftStatLabel}>{stat.label}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );

  // ===== Render: Role Selection =====
  const renderRoleSelection = () => (
    <>
      <div className={styles.sectionLabel}>选择您的身份</div>
      <div className={styles.roleCards}>
        <button className={styles.roleCard} onClick={() => setStep('candidateStep1')}>
          <div className={styles.roleIcon}><UserOutlined /></div>
          <div className={styles.roleName}>我是求职者</div>
          <div className={styles.roleDesc}>浏览岗位、投递简历、AI 面试辅导</div>
        </button>
        <button className={styles.roleCard} onClick={() => setStep('hrType')}>
          <div className={styles.roleIcon}><TeamOutlined /></div>
          <div className={styles.roleName}>我是企业 HR</div>
          <div className={styles.roleDesc}>发布岗位、管理候选人、安排面试</div>
        </button>
      </div>
    </>
  );

  // ===== Render: HR Type Selection =====
  const renderHRTypeSelection = () => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 返回选择身份
      </button>
      <div className={styles.sectionLabel}>选择入驻方式</div>
      <div className={styles.roleCards}>
        <button className={styles.roleCard} onClick={() => setStep('hrJoinStep1')}>
          <div className={styles.roleIcon}><SearchOutlined /></div>
          <div className={styles.roleName}>加入已有企业</div>
          <div className={styles.roleDesc}>我的企业已入驻平台，通过邀请码加入团队</div>
        </button>
        <button className={styles.roleCard} onClick={() => setStep('hrNewStep1')}>
          <div className={styles.roleIcon}><BankOutlined /></div>
          <div className={styles.roleName}>企业首次入驻</div>
          <div className={styles.roleDesc}>企业尚未入驻，提交申请资料等待审核</div>
        </button>
      </div>
    </>
  );

  // ===== Render: Step 1 (Phone + Code + Password + Name) =====
  const renderStep1 = (onNext: () => void, steps: { title: string }[], currentStep: number) => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 返回
      </button>
      <div className={styles.steps}>
        <Steps size="small" current={currentStep} items={steps} />
      </div>
      <div className={styles.formFields}>
        <div className={styles.field}>
          <Input size="large" placeholder="请输入手机号"
            prefix={<PhoneOutlined style={{ color: 'var(--text-tertiary)' }} />}
            value={phone} onChange={(e) => setPhone(e.target.value)}
            maxLength={11} className={styles.fieldInput}
          />
        </div>
        <div className={styles.field}>
          <Input size="large" placeholder="请输入验证码"
            value={code} onChange={(e) => setCode(e.target.value)}
            maxLength={6} className={styles.fieldInput}
            suffix={
              <button className={styles.codeBtn} disabled={countdown > 0 || sending} onClick={handleSendCode}>
                {sending ? '发送中...' : countdown > 0 ? `${countdown}s` : '获取验证码'}
              </button>
            }
          />
        </div>
        <div className={styles.field}>
          <Input.Password size="large" placeholder="请设置密码（8-20位，大小写+数字+特殊字符）"
            prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
            value={password} onChange={(e) => setPassword(e.target.value)}
            className={styles.fieldInput}
          />
        </div>
        <div className={styles.field}>
          <Input.Password size="large" placeholder="请确认密码"
            prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
            value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
            className={styles.fieldInput}
          />
        </div>
        <div className={styles.field}>
          <Input size="large" placeholder="请输入真实姓名"
            value={name} onChange={(e) => setName(e.target.value)}
            className={styles.fieldInput}
          />
        </div>
      </div>
      <button className={styles.submitBtn} disabled={loading} onClick={onNext}>
        {loading ? '注册中...' : '下一步'}
      </button>
    </>
  );

  // ===== Render: Candidate Step 2 =====
  const renderCandidateStep2 = () => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 上一步
      </button>
      <div className={styles.steps}>
        <Steps size="small" current={1}
          items={[{ title: '验证手机' }, { title: '个人信息' }, { title: '求职意向' }]}
        />
      </div>
      <div className={styles.formFields}>
        <div className={styles.field}>
          <Select size="large" placeholder="请选择性别" allowClear
            value={gender} onChange={(v) => setGender(v)}
            options={[{ value: 'MALE', label: '男' }, { value: 'FEMALE', label: '女' }]}
            className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="请选择所在城市" allowClear
            value={city} onChange={(v) => setCity(v)}
            options={cityOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="请选择工作年限" allowClear
            value={workYears} onChange={(v) => setWorkYears(v)}
            options={workYearsOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="请选择最高学历" allowClear
            value={education} onChange={(v) => setEducation(v)}
            options={educationOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <div className={styles.radioLabel}>求职状态</div>
          {jobStatusOptions.map((item) => (
            <div key={item.value}
              className={`${styles.radioCard} ${jobStatus === item.value ? styles.radioCardActive : ''}`}
              onClick={() => setJobStatus(item.value)}
              style={{ marginBottom: 8 }}
            >
              <div className={`${styles.radioDot} ${jobStatus === item.value ? styles.radioDotActive : ''}`} />
              <div className={styles.radioContent}>
                <span className={styles.radioTitle}>{item.label}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className={styles.actionBtns}>
        <button className={styles.submitBtn} disabled={loading} onClick={() => setStep('candidateStep3')}>
          下一步
        </button>
        <button className={styles.skipBtn} onClick={() => setStep('candidateStep3')}>
          跳过，稍后填写
        </button>
      </div>
    </>
  );

  // ===== Render: Candidate Step 3 =====
  const renderCandidateStep3 = () => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 上一步
      </button>
      <div className={styles.steps}>
        <Steps size="small" current={2}
          items={[{ title: '验证手机' }, { title: '个人信息' }, { title: '求职意向' }]}
        />
      </div>
      <div className={styles.formFields}>
        <div className={styles.field}>
          <Input size="large" placeholder="期望岗位（如：前端工程师）"
            value={desiredJob} onChange={(e) => setDesiredJob(e.target.value)}
            className={styles.fieldInput}
          />
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="期望城市" allowClear
            value={desiredCity} onChange={(v) => setDesiredCity(v)}
            options={cityOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="期望薪资" allowClear
            value={desiredSalary} onChange={(v) => setDesiredSalary(v)}
            options={salaryOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <div className={styles.radioLabel}>到岗时间</div>
          {[
            { value: 'ASAP', label: '随时到岗' },
            { value: '1W', label: '1周内' },
            { value: '1M', label: '1个月内' },
          ].map((item) => (
            <div key={item.value}
              className={`${styles.radioCard} ${availableFrom === item.value ? styles.radioCardActive : ''}`}
              onClick={() => setAvailableFrom(item.value)}
              style={{ marginBottom: 8 }}
            >
              <div className={`${styles.radioDot} ${availableFrom === item.value ? styles.radioDotActive : ''}`} />
              <div className={styles.radioContent}>
                <span className={styles.radioTitle}>{item.label}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className={styles.actionBtns}>
        <button className={styles.submitBtn} disabled={loading} onClick={handleCandidateStep3}>
          {loading ? '保存中...' : '完成注册'}
        </button>
        <button className={styles.skipBtn} onClick={() => {
          if (completeRegistration()) {
            navigate('/candidate/home');
          }
        }}>
          跳过，稍后填写
        </button>
      </div>
    </>
  );

  // ===== Render: HR Join Step 2 =====
  const renderHRJoinStep2 = () => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 返回
      </button>
      <div className={styles.steps}>
        <Steps size="small" current={1}
          items={[{ title: '验证手机' }, { title: '加入企业' }, { title: '完成' }]}
        />
      </div>
      <div className={styles.formFields}>
        <div className={styles.sectionSubLabel}>企业信息</div>
        <div className={styles.field}>
          <Input size="large" placeholder="请输入企业邀请码"
            prefix={<KeyOutlined style={{ color: 'var(--text-tertiary)' }} />}
            value={inviteCode} onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
            className={styles.fieldInput}
          />
          <div className={styles.fieldHint}>请联系企业管理员获取邀请码</div>
        </div>
      </div>
      <button className={styles.submitBtn} disabled={loading} onClick={handleHRJoinStep2}>
        {loading ? '加入中...' : '加入企业'}
      </button>
    </>
  );

  // ===== Render: HR New Step 2 =====
  const renderHRNewStep2 = () => (
    <>
      <button className={styles.backBtn} onClick={goBack}>
        <ArrowLeftOutlined /> 返回
      </button>
      <div className={styles.steps}>
        <Steps size="small" current={1}
          items={[{ title: '验证手机' }, { title: '企业信息' }, { title: '提交审核' }]}
        />
      </div>
      <div className={styles.formFields}>
        <div className={styles.sectionSubLabel}>企业资料</div>
        <div className={styles.field}>
          <Input size="large" placeholder="请输入企业全称"
            value={companyName} onChange={(e) => setCompanyName(e.target.value)}
            className={styles.fieldInput}
          />
        </div>
        <div className={styles.fieldRow}>
          <div className={styles.fieldHalf}>
            <Select size="large" placeholder="所属行业" allowClear
              value={companyIndustry} onChange={(v) => setCompanyIndustry(v)}
              options={industryOptions} className={styles.fieldSelect}
            />
          </div>
          <div className={styles.fieldHalf}>
            <Select size="large" placeholder="企业规模" allowClear
              value={companyScale} onChange={(v) => setCompanyScale(v)}
              options={scaleOptions} className={styles.fieldSelect}
            />
          </div>
        </div>
        <div className={styles.field}>
          <Select size="large" placeholder="企业所在地" allowClear
            value={companyAddress} onChange={(v) => setCompanyAddress(v)}
            options={cityOptions} className={styles.fieldSelect}
          />
        </div>
        <div className={styles.field}>
          <div className={styles.sectionSubLabel}>营业执照</div>
          <Upload
            accept=".jpg,.jpeg,.png,.pdf"
            maxCount={1}
            beforeUpload={(file) => {
              if (!/\.(jpe?g|png|pdf)$/i.test(file.name)) {
                message.error('仅支持 jpg/png/pdf 格式');
                return Upload.LIST_IGNORE;
              }
              if (file.size > 5 * 1024 * 1024) {
                message.error('营业执照不能超过5MB');
                return Upload.LIST_IGNORE;
              }
              setBusinessLicense(file);
              return false;
            }}
            onRemove={() => setBusinessLicense(null)}
          >
            <Button icon={<UploadOutlined />}>
              {businessLicense ? businessLicense.name : '上传营业执照'}
            </Button>
          </Upload>
          <div className={styles.fieldHint}>选填，仅支持 jpg/png/pdf，不超过5MB</div>
        </div>
      </div>
      <div className={styles.notice}>
        提交后平台将在 1-3 个工作日内完成审核，审核结果将以短信通知。
      </div>
      <button className={styles.submitBtn} disabled={loading} onClick={handleHRNewStep2}>
        {loading ? '提交中...' : '提交审核'}
      </button>
    </>
  );

  // ===== Main Render =====
  const renderContent = () => {
    switch (step) {
      case 'role': return renderRoleSelection();
      case 'hrType': return renderHRTypeSelection();
      case 'candidateStep1': return renderStep1(() => handleStep1('CANDIDATE', 'candidateStep2'), [{ title: '验证手机' }, { title: '个人信息' }, { title: '求职意向' }], 0);
      case 'candidateStep2': return renderCandidateStep2();
      case 'candidateStep3': return renderCandidateStep3();
      case 'hrJoinStep1': return renderStep1(() => handleStep1('HR', 'hrJoinStep2'), [{ title: '验证手机' }, { title: '加入企业' }, { title: '完成' }], 0);
      case 'hrJoinStep2': return renderHRJoinStep2();
      case 'hrNewStep1': return renderStep1(() => handleStep1('HR', 'hrNewStep2'), [{ title: '验证手机' }, { title: '企业信息' }, { title: '提交审核' }], 0);
      case 'hrNewStep2': return renderHRNewStep2();
      default: return null;
    }
  };

  const isRoleStep = step === 'role';

  // ===== 分屏布局（角色选择 + 求职者流程）=====
  if (isSplitLayout) {
    return (
      <div className={`c-mode ${styles.splitWrapper}`}>
        {renderLeftPanel()}
        <div className={styles.rightPanel}>
          {/* 步骤进度条 */}
          {step !== 'role' && step !== 'hrType' && (
            <div className={styles.progressBar}>
              <div className={styles.progressFill} style={{
                width: (() => {
                  if (step === 'candidateStep1' || step === 'hrJoinStep1' || step === 'hrNewStep1') return '33%';
                  if (step === 'candidateStep2' || step === 'hrJoinStep2' || step === 'hrNewStep2') return '66%';
                  return '100%';
                })(),
                background: `linear-gradient(90deg, var(--accent), var(--accent-2))`,
              }} />
            </div>
          )}

          <div className={styles.rightContent}>
            {step === 'role' ? (
              // 角色选择页：居中大标题
              <div className={styles.rightHeaderCenter}>
                <div className={styles.rightBrandMark}>注</div>
                <h1 className={styles.rightTitle}>创建账号</h1>
                <p className={styles.rightSubtitle}>加入灵犀互聘，开启智能招聘之旅</p>
              </div>
            ) : (
              // 步骤页：紧凑标题
              <div className={styles.rightHeader}>
                <div className={styles.rightBrandMark}>注</div>
                <div>
                  <h1 className={styles.rightTitle}>创建账号</h1>
                  <p className={styles.rightSubtitle}>
                    {step === 'candidateStep1' && '第一步: 验证手机号'}
                    {step === 'candidateStep2' && '第二步: 完善个人信息'}
                    {step === 'candidateStep3' && '第三步: 设置求职意向'}
                    {step === 'hrType' && '选择入驻方式'}
                    {step === 'hrJoinStep1' && '第一步: 验证手机号'}
                    {step === 'hrJoinStep2' && '第二步: 加入企业'}
                    {step === 'hrNewStep1' && '第一步: 验证手机号'}
                    {step === 'hrNewStep2' && '第二步: 填写企业信息'}
                  </p>
                </div>
              </div>
            )}
            {renderContent()}
            {step === 'role' && (
              <div className={styles.rightFooter}>
                <button className={styles.footerLink} onClick={() => navigate('/login')}>
                  已有账号？立即登录
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ===== HR流程：居中卡片布局（保持不变）=====
  return (
    <div className={`c-mode ${styles.wrapper}`}>
      <div className={styles.bgPattern} />
      <div className={styles.container}>
        <div className={styles.card}>
          <div className={styles.cardAccent} />
          {isRoleStep ? (
            <div className={styles.header}>
              <div className={styles.brandMark}>注</div>
              <h1 className={styles.title}>创建账号</h1>
              <p className={styles.subtitle}>加入灵犀互聘，开启智能招聘之旅</p>
            </div>
          ) : (
            <div className={styles.headerCompact}>
              <div className={styles.brandMarkSm}>注</div>
              <div>
                <h1 className={styles.titleSm}>创建账号</h1>
              </div>
            </div>
          )}
          {renderContent()}
          {isRoleStep && (
            <div className={styles.footer}>
              <button className={styles.footerLink} onClick={() => navigate('/login')}>
                已有账号？立即登录
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
