/** 路由路径常量 */
export const ROUTES = {
  // 登录
  LOGIN: '/login',
  LOGIN_ADMIN: '/login/admin',
  REGISTER: '/register',

  // C端
  CANDIDATE_HOME: '/candidate/home',
  CANDIDATE_JOB: '/candidate/job',
  CANDIDATE_JOB_DETAIL: '/candidate/job',
  CANDIDATE_RESUME_UPLOAD: '/candidate/resume/upload',
  CANDIDATE_RESUME_PREVIEW: '/candidate/resume',
  CANDIDATE_RESUME_DIAGNOSIS: '/candidate/resume',
  CANDIDATE_APPLICATION: '/candidate/application',
  CANDIDATE_FAVORITES: '/candidate/favorites',
  CANDIDATE_AI_ASSISTANT: '/candidate/ai-assistant',
  CANDIDATE_MOCK_INTERVIEW: '/candidate/mock-interview',
  CANDIDATE_MESSAGE: '/candidate/message',
  CANDIDATE_NOTIFICATION: '/candidate/notification',
  CANDIDATE_PROFILE: '/candidate/profile',
  CANDIDATE_SECURITY: '/candidate/security',

  // B端 HR
  HR_DASHBOARD: '/hr/dashboard',
  HR_JOB: '/hr/job',
  HR_JOB_CREATE: '/hr/job/create',
  HR_JOB_DETAIL: '/hr/job',
  HR_JOB_EDIT: '/hr/job',
  HR_QUESTION_BANK: '/hr/question-bank',
  HR_CANDIDATE: '/hr/candidate',
  HR_INTERVIEW: '/hr/interview',
  HR_OFFER: '/hr/offer',
  HR_MESSAGE: '/hr/message',
  HR_NOTIFICATION: '/hr/notification',
  HR_COMPANY: '/hr/company',
  HR_PROFILE: '/hr/profile',

  // B端 面试官
  INTERVIEWER_INTERVIEW: '/interviewer/interview',
  INTERVIEWER_CANDIDATE: '/interviewer/candidate',
  INTERVIEWER_PROFILE: '/interviewer/profile',

  // A端
  ADMIN_DASHBOARD: '/admin/dashboard',
  ADMIN_ENTERPRISE_AUDIT: '/admin/enterprise-audit',
  ADMIN_ENTERPRISE: '/admin/enterprise',
  ADMIN_HR: '/admin/hr',
  ADMIN_INTERVIEWER: '/admin/interviewer',
  ADMIN_JOB: '/admin/job',
  ADMIN_CANDIDATE: '/admin/candidate',
  ADMIN_ANNOUNCEMENT: '/admin/announcement',
  ADMIN_AUDIT_LOG: '/admin/audit-log',
  ADMIN_CONFIG: '/admin/config',
} as const;
