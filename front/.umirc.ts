import { defineConfig } from 'umi';

export default defineConfig({
  title: '灵犀互聘',
  links: [
    { rel: 'icon', href: '/favicon.ico' },
    // Google Fonts 加载
    { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
    { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: 'anonymous' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&display=swap' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700;800&display=swap' },
  ],

  // 路由配置
  routes: [
    // ===== 通用登录 =====
    { path: '/login', component: 'login/index', layout: false },
    { path: '/login/admin', component: 'login/admin', layout: false },
    { path: '/register', component: 'login/register', layout: false },

    // ===== C端 - 求职者 =====
    {
      path: '/candidate',
      component: '@/components/Layout/CandidateLayout',
      routes: [
        { path: '/candidate/home', component: 'candidate/home/index' },
        { path: '/candidate/job', component: 'candidate/job/index' },
        { path: '/candidate/job/:jobId', component: 'candidate/job/detail' },
        { path: '/candidate/resume/upload', component: 'candidate/resume/upload' },
        { path: '/candidate/resume/:id/preview', component: 'candidate/resume/preview' },
        { path: '/candidate/resume/:id/diagnosis', component: 'candidate/resume/diagnosis' },
        { path: '/candidate/application', component: 'candidate/application/index' },
        { path: '/candidate/favorites', component: 'candidate/favorites/index' },
        { path: '/candidate/ai-assistant', component: 'candidate/ai-assistant/index' },
        { path: '/candidate/mock-interview', component: 'candidate/mock-interview/index' },
        { path: '/candidate/message', component: 'candidate/message/index' },
        { path: '/candidate/notification', component: 'candidate/notification/index' },
        { path: '/candidate/profile', component: 'candidate/profile/index' },
        { path: '/candidate/security', component: 'candidate/security/index' },
        { path: '/candidate', redirect: '/candidate/home' },
      ],
    },

    // ===== B端 - HR =====
    {
      path: '/hr',
      component: '@/components/Layout/HRLayout',
      routes: [
        { path: '/hr/dashboard', component: 'hr/dashboard/index' },
        { path: '/hr/job', component: 'hr/job/index' },
        { path: '/hr/job/create', component: 'hr/job/create' },
        { path: '/hr/job/:jobId', component: 'hr/job/detail' },
        { path: '/hr/job/:jobId/edit', component: 'hr/job/create' },
        { path: '/hr/question-bank', component: 'hr/question-bank/index' },
        { path: '/hr/candidate', component: 'hr/candidate/index' },
        { path: '/hr/candidate/detail/:userId', component: 'hr/candidate/detail/index' },
        { path: '/hr/interview', component: 'hr/interview/index' },
        { path: '/hr/offer', component: 'hr/offer/index' },
        { path: '/hr/message', component: 'hr/message/index' },
        { path: '/hr/notification', component: 'hr/notification/index' },
        { path: '/hr/company', component: 'hr/company/index' },
        { path: '/hr/profile', component: 'hr/profile/index' },
        { path: '/hr', redirect: '/hr/dashboard' },
      ],
    },

    // ===== B端 - 面试官 =====
    {
      path: '/interviewer',
      component: '@/components/Layout/HRLayout',
      routes: [
        { path: '/interviewer/interview', component: 'interviewer/interview/index' },
        { path: '/interviewer/candidate', component: 'interviewer/candidate/index' },
        { path: '/interviewer/notification', component: 'interviewer/notification/index' },
        { path: '/interviewer/profile', component: 'interviewer/profile/index' },
        { path: '/interviewer', redirect: '/interviewer/interview' },
      ],
    },

    // ===== A端 - 管理后台 =====
    {
      path: '/admin',
      component: '@/components/Layout/AdminLayout',
      routes: [
        { path: '/admin/dashboard', component: 'admin/dashboard/index' },
        { path: '/admin/enterprise-audit', component: 'admin/enterprise-audit/index' },
        { path: '/admin/enterprise', component: 'admin/enterprise/index' },
        { path: '/admin/hr', component: 'admin/hr/index' },
        { path: '/admin/interviewer', component: 'admin/interviewer/index' },
        { path: '/admin/job', component: 'admin/job/index' },
        { path: '/admin/candidate', component: 'admin/candidate/index' },
        { path: '/admin/announcement', component: 'admin/announcement/index' },
        { path: '/admin/audit-log', component: 'admin/audit-log/index' },
        { path: '/admin/config', component: 'admin/config/index' },
        { path: '/admin', redirect: '/admin/dashboard' },
      ],
    },

    // ===== 认证等待页 =====
    { path: '/certification/pending', component: '@/pages/certification/pending', layout: false },

    // ===== 根路径重定向 =====
    { path: '/', redirect: '/login' },
  ],

  // 代理配置
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // API通过网关
      changeOrigin: true,
      // SSE流式响应不缓冲
      configure: (proxy: any) => {
        proxy.on('proxyRes', (proxyRes: any) => {
          const contentType = proxyRes.headers['content-type'] || '';
          if (contentType.includes('text/event-stream')) {
            proxyRes.headers['cache-control'] = 'no-cache';
            proxyRes.headers['x-accel-buffering'] = 'no';
          }
        });
      },
    },
    '/files': {
      target: 'http://localhost:8080',  // 文件也通过网关
      changeOrigin: true,
    },
    '/ws': {
      target: 'ws://localhost:8080',  // WebSocket通过网关
      ws: true,
      changeOrigin: true,
    },
  },

  // npm 客户端
  npmClient: 'npm',
});
