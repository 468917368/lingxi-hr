import type { UserRole } from '@/constants/enums';
import { InterviewStatus, OfferStatus } from '@/constants/enums';

// ===== 用户 Mock =====
export interface MockUser {
  id: number;
  name: string;
  phone: string;
  email: string;
  avatar?: string;
  role: UserRole;
  companyId?: number;
  companyName?: string;
  position?: string;
  city?: string;
  jobStatus?: string;
}

export const mockUsers: Record<string, MockUser> = {
  candidate1: {
    id: 1, name: '张三', phone: '13800000001', email: 'zhangsan@example.com',
    role: 'CANDIDATE' as UserRole, city: '杭州', jobStatus: 'LOOKING',
  },
  candidate2: {
    id: 2, name: '李四', phone: '13800000002', email: 'lisi@example.com',
    role: 'CANDIDATE' as UserRole, city: '北京', jobStatus: 'OPEN',
  },
  hr1: {
    id: 10, name: '王HR', phone: '13800000010', email: 'wanghr@example.com',
    role: 'HR' as UserRole, companyId: 1, companyName: '某互联网公司', position: 'HR负责人',
  },
  interviewer1: {
    id: 20, name: '赵面试官', phone: '13800000020', email: 'zhao@example.com',
    role: 'INTERVIEWER' as UserRole, companyId: 1, companyName: '某互联网公司', position: '技术总监',
  },
  admin: {
    id: 99, name: '管理员', phone: '13800000099', email: 'admin@example.com',
    role: 'ADMIN' as UserRole,
  },
};

// ===== 简历 Mock =====
export interface MockResume {
  id: number;
  userId: number;
  name: string;
  phone: string;
  email: string;
  education: string;
  workExperience: string;
  projectExperience: string;
  skills: string[];
  selfEvaluation: string;
  isDefault: boolean;
  uploadTime: string;
}

export const mockResumes: MockResume[] = [
  {
    id: 1, userId: 1, name: '张三', phone: '13800000001', email: 'zhangsan@example.com',
    education: '北京大学 · 计算机科学 · 本科',
    workExperience: '3年 · 某科技公司 · 前端开发工程师',
    projectExperience: '企业级中后台管理系统 · React + TypeScript',
    skills: ['React', 'TypeScript', 'Node.js', 'CSS', 'Webpack'],
    selfEvaluation: '热爱前端技术，擅长React生态，有良好的工程化思维',
    isDefault: true, uploadTime: '2026-07-28 14:30',
  },
];

// ===== 面试 Mock =====
export interface MockInterview {
  id: number;
  applicationId: number;
  candidateName: string;
  jobTitle: string;
  interviewTime: string;
  interviewType: string;
  interviewer: string;
  status: InterviewStatus;
  evaluation?: string;
}

export const mockInterviews: MockInterview[] = [
  {
    id: 1, applicationId: 2, candidateName: '张三', jobTitle: 'Java后端工程师',
    interviewTime: '2026-07-30 14:00', interviewType: '视频面试', interviewer: '赵面试官',
    status: InterviewStatus.SCHEDULED,
  },
  {
    id: 2, applicationId: 1, candidateName: '李四', jobTitle: '高级前端工程师',
    interviewTime: '2026-07-31 10:00', interviewType: '现场面试', interviewer: '赵面试官',
    status: InterviewStatus.PENDING,
  },
];

// ===== Offer Mock =====
export interface MockOffer {
  id: number;
  candidateName: string;
  jobTitle: string;
  salary: string;
  sentTime: string;
  expireTime: string;
  status: OfferStatus;
}

export const mockOffers: MockOffer[] = [
  {
    id: 1, candidateName: '王五', jobTitle: '前端开发工程师',
    salary: '22K×14薪', sentTime: '2026-07-28', expireTime: '2026-08-04',
    status: OfferStatus.SENT,
  },
];

// ===== 通知 Mock =====
export interface MockNotification {
  id: number;
  type: string;
  title: string;
  content: string;
  icon: string;
  color: string;
  time: string;
  read: boolean;
  link: string;
}

export const mockNotifications = [
  { id: 1, type: 'APPLICATION_STATUS', title: '简历被查看', content: '某互联网公司的HR已查看您的简历', icon: '📄', color: '#1890ff', time: '2分钟前', read: false, link: '/candidate/application' },
  { id: 2, type: 'APPLICATION_STATUS', title: '筛选通过', content: '恭喜！您已通过某科技公司的简历筛选', icon: '🎉', color: '#52c41a', time: '1小时前', read: false, link: '/candidate/application' },
  { id: 3, type: 'INTERVIEW_INVITE', title: '面试邀请', content: '某科技公司邀请您参加面试，时间明天14:00', icon: '📅', color: '#52c41a', time: '昨天', read: true, link: '/candidate/application' },
  { id: 4, type: 'JOB_RECOMMEND', title: '岗位推荐', content: '为您推荐5个匹配度>85%的岗位', icon: '💼', color: '#13c2c2', time: '3天前', read: true, link: '/candidate/job' },
];

// ===== 企业 Mock =====
export interface MockCompany {
  id: number;
  name: string;
  industry: string;
  scale: string;
  address: string;
  certStatus: string;
  logo?: string;
}

export const mockCompanies: MockCompany[] = [
  { id: 1, name: '某互联网公司', industry: '互联网/电商', scale: '500-999人', address: '杭州', certStatus: '已认证' },
  { id: 2, name: '某科技公司', industry: '企业服务/SaaS', scale: '100-499人', address: '杭州', certStatus: '待审核' },
  { id: 3, name: '某数据公司', industry: '大数据/AI', scale: '200-499人', address: '上海', certStatus: '已认证' },
];

// ===== Dashboard 统计数据 Mock =====
export const mockHRStats = {
  todayApplications: 56,
  pendingReview: 23,
  weeklyInterviews: 8,
  monthlyOffers: 5,
};

export const mockAdminStats = {
  totalUsers: 1580,
  certifiedCompanies: 126,
  activeJobs: 342,
  totalApplications: 4520,
};
