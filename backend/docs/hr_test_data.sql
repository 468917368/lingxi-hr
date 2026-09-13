-- ============================================================
-- HR端候选人列表测试数据（成员D）
-- 企业：hr_company.id=1 (test1, 企业服务/SaaS)
-- 库：lingxi（本地开发库）
-- 生成日期：2026-08-05
--
-- 内容概览：
--   1. job_post          9 个新岗位（id=3~11，公司1）+ job_profile 画像
--   2. sys_user          10 个新求职者（id=20~29，CANDIDATE，密码统一）
--   3. sys_user_profile  10 份求职者画像
--   4. resume            10 份默认简历（解析完成）+ resume_ability_model 能力模型
--   5. resume_application  30 条投递（覆盖 SUBMITTED~OFFERED/REJECTED 全状态机）
--   6. resume_status_log 投递状态流转日志（时间线）
--   7. hr_interview      4 条面试（INTERVIEWING 候选人）
--   8. hr_offer          3 条 Offer + job_hc_reservation HC 流水
--   9. msg_*             2 个会话（HR 8 ↔ 候选人 20/28）
--  10. sys_notification  候选人/HR 通知若干
--
-- 密码：统一 $2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai
--       （与现有账号 xx / 测试候选人 相同明文密码）
--
-- 注意：
--   1. 使用显式ID，避免自增不确定性；重复执行前先跑文末「清理脚本」。
--   2. resume_application 唯一键 uk_job_candidate(job_id,candidate_id)，已避重。
--   3. hr_offer.id 非自增（Snowflake），需显式提供；已用 60100+ 段。
--   4. 执行方式：
--      mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uroot -p123456 lingxi < hr_test_data.sql
-- ============================================================

-- ============================================================
-- 1. 岗位（job_post，公司1），9 个新岗位
-- ============================================================
INSERT INTO job_post
    (id, company_id, title, city_code, city_name, min_experience_years, education_requirement,
     salary_min_amount, salary_max_amount, salary_currency, salary_period, salary_months,
     is_salary_negotiable, salary_raw_text, total_hc, reserved_hc, confirmed_hc,
     jd_text, jd_summary, status, pause_reason, close_reason, published_at, closed_at, expires_at,
     version, created_by, updated_by, industry_group_code, industry_code, created_at, updated_at, deleted_at)
VALUES
    (3, 1, 'Java开发工程师', '110100', '北京', 1, 'BACHELOR',
     1200000, 2000000, 'CNY', 'MONTH', 13, 0, '12-20K·13薪', 3, 0, 0,
     '负责企业级SaaS平台后端服务的设计、开发与维护，参与订单、用户、计费等核心模块建设，技术栈 Spring Boot + MySQL + Redis + RocketMQ。',
     'Java后端开发，Spring Boot/MySQL/Redis/RocketMQ，SaaS核心模块，1年以上经验',
     'PUBLISHED', NULL, NULL, '2026-08-01 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-08-01 10:00:00', '2026-08-01 10:00:00', NULL),

    (4, 1, '前端开发工程师', '110100', '北京', 1, 'BACHELOR',
     1300000, 2200000, 'CNY', 'MONTH', 13, 0, '13-22K·13薪', 3, 0, 0,
     '负责Web端与H5产品的前端开发，基于 React + TypeScript + Vite 技术栈，参与组件库建设与性能优化。',
     '前端开发，React/TypeScript/Vite，Web+H5，1年以上经验',
     'PUBLISHED', NULL, NULL, '2026-07-30 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-07-30 10:00:00', '2026-07-30 10:00:00', NULL),

    (5, 1, '高级前端工程师', '110100', '北京', 3, 'BACHELOR',
     2200000, 3500000, 'CNY', 'MONTH', 14, 0, '22-35K·14薪', 2, 0, 0,
     '负责前端架构设计与核心业务模块研发，主导前端工程化、微前端改造与性能优化，带教初级工程师。',
     '高级前端，前端架构/工程化/微前端，React技术栈，3年以上经验',
     'PUBLISHED', NULL, NULL, '2026-07-28 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-07-28 10:00:00', '2026-07-28 10:00:00', NULL),

    (6, 1, '产品经理', '110100', '北京', 3, 'BACHELOR',
     2000000, 3200000, 'CNY', 'MONTH', 13, 0, '20-32K·13薪', 2, 0, 0,
     '负责SaaS产品的需求分析、产品规划与迭代落地，撰写PRD并跟进研发进度，关注企业客户交付价值。',
     'SaaS产品经理，需求分析/PRD/版本规划，3年以上经验',
     'PUBLISHED', NULL, NULL, '2026-07-29 10:00:00', NULL, NULL,
     0, 12, 12, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-07-29 10:00:00', '2026-07-29 10:00:00', NULL),

    (7, 1, '测试开发工程师', '110100', '北京', 1, 'BACHELOR',
     1400000, 2400000, 'CNY', 'MONTH', 13, 0, '14-24K·13薪', 2, 0, 0,
     '负责SaaS平台质量保障体系建设，搭建自动化测试框架，编写接口与UI自动化用例，参与性能测试与持续集成。',
     '测试开发，自动化测试/接口测试/CI，1年以上经验',
     'PUBLISHED', NULL, NULL, '2026-08-02 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-08-02 10:00:00', '2026-08-02 10:00:00', NULL),

    (8, 1, '运维工程师', '110100', '北京', 3, 'BACHELOR',
     1800000, 2800000, 'CNY', 'MONTH', 13, 0, '18-28K·13薪', 1, 0, 0,
     '负责线上环境稳定性保障，建设监控告警体系，处理容量规划与故障应急，推进容器化与CI/CD落地。',
     '运维/SRE，监控告警/故障应急/容器化，3年以上经验',
     'PUBLISHED', NULL, NULL, '2026-07-31 10:00:00', NULL, NULL,
     0, 12, 12, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-07-31 10:00:00', '2026-07-31 10:00:00', NULL),

    (9, 1, '数据仓库工程师', '110100', '北京', 3, 'MASTER',
     2800000, 4500000, 'CNY', 'MONTH', 14, 0, '28-45K·14薪', 1, 0, 0,
     '负责数据仓库模型设计与ETL开发，建设数据中台，支持经营分析报表与BI看板，使用 Hive/Spark/Flink 技术栈。',
     '数据仓库，ETL/Hive/Spark/Flink，数据中台，3年以上经验',
     'PUBLISHED', NULL, NULL, '2026-07-28 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-07-28 10:00:00', '2026-07-28 10:00:00', NULL),

    (10, 1, '客户成功经理', '110100', '北京', 1, 'ASSOCIATE',
     1000000, 1800000, 'CNY', 'MONTH', 13, 0, '10-18K·13薪', 2, 0, 0,
     '负责存量客户的关系维护与续费增购，主导客户上线培训与使用指导，推动客户成功案例沉淀。',
     '客户成功，客户运营/续费增购/企业服务，1年以上经验',
     'PUBLISHED', NULL, NULL, '2026-08-03 10:00:00', NULL, NULL,
     0, 12, 12, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-08-03 10:00:00', '2026-08-03 10:00:00', NULL),

    (11, 1, 'UI设计师', '110100', '北京', 1, 'BACHELOR',
     1200000, 2000000, 'CNY', 'MONTH', 13, 0, '12-20K·13薪', 1, 0, 0,
     '负责SaaS产品界面与交互设计，搭建设计规范与组件库，与产品研发协作完成高保真设计交付。',
     'UI设计，SaaS/Web界面设计/设计规范，1年以上经验',
     'PUBLISHED', NULL, NULL, '2026-08-01 10:00:00', NULL, NULL,
     0, 8, 8, 'ENTERPRISE_SERVICE', 'ENTERPRISE_SERVICE', '2026-08-01 10:00:00', '2026-08-01 10:00:00', NULL);

-- ============================================================
-- 2. 岗位画像（job_profile，公司1，与上面岗位一一对应）
-- ============================================================
INSERT INTO job_profile
    (id, company_id, job_id, job_type, core_skills, soft_skills, industry_experience,
     hidden_requirements, interview_focus, profile_source, version, confirmed_by, confirmed_at, created_at, updated_at)
VALUES
    (2, 1, 3, 'JAVA_BACKEND',
     '[{"name":"Spring Boot","level":"熟练","required":true},{"name":"MySQL","level":"熟练","required":true},{"name":"Redis","level":"熟悉","required":false},{"name":"RocketMQ","level":"了解","required":false}]',
     '[{"name":"沟通协作","importance":"重要"},{"name":"责任心","importance":"重要"}]',
     '有SaaS/企业级平台后端开发经验者优先',
     '[{"requirement":"稳定性意识强，线上问题响应及时","basis":"SaaS客户对可用性要求高","inferred":true,"confidence":0.8}]',
     '["Spring Boot核心原理","MySQL索引与事务","并发与缓存一致性"]',
     'MANUAL', 1, 8, '2026-08-01 11:00:00', '2026-08-01 11:00:00', '2026-08-01 11:00:00'),

    (3, 1, 4, 'FRONTEND',
     '[{"name":"React","level":"熟练","required":true},{"name":"TypeScript","level":"熟练","required":true},{"name":"Vite","level":"熟悉","required":false}]',
     '[{"name":"主动性","importance":"重要"},{"name":"审美","importance":"一般"}]',
     '有中后台系统开发经验者优先',
     '[{"requirement":"对组件化与复用有良好实践","basis":"团队正在建设组件库","inferred":true,"confidence":0.7}]',
     '["React渲染机制","组件拆分与状态管理","性能优化"]',
     'MANUAL', 1, 8, '2026-07-30 11:00:00', '2026-07-30 11:00:00', '2026-07-30 11:00:00'),

    (4, 1, 5, 'FRONTEND',
     '[{"name":"React","level":"精通","required":true},{"name":"微前端","level":"熟练","required":true},{"name":"工程化","level":"熟练","required":true}]',
     '[{"name":"技术视野","importance":"重要"},{"name":"团队带教","importance":"重要"}]',
     '有大型Web应用架构经验优先',
     '[{"requirement":"能独立主导前端架构方案","basis":"负责整体前端架构","inferred":true,"confidence":0.85}]',
     '["微前端落地实践","前端工程化","性能优化与监控"]',
     'MANUAL', 1, 8, '2026-07-28 11:00:00', '2026-07-28 11:00:00', '2026-07-28 11:00:00'),

    (5, 1, 6, 'PRODUCT',
     '[{"name":"需求分析","level":"精通","required":true},{"name":"PRD撰写","level":"精通","required":true},{"name":"数据分析","level":"熟练","required":true}]',
     '[{"name":"沟通协调","importance":"重要"},{"name":"用户思维","importance":"重要"}]',
     '有B端/SaaS产品经验优先',
     '[{"requirement":"有从0到1产品落地经验","basis":"SaaS产品需要完整交付能力","inferred":true,"confidence":0.75}]',
     '["需求优先级判断","跨团队协作","产品数据指标体系"]',
     'MANUAL', 1, 12, '2026-07-29 11:00:00', '2026-07-29 11:00:00', '2026-07-29 11:00:00'),

    (6, 1, 7, 'QA_ENGINEER',
     '[{"name":"自动化测试","level":"熟练","required":true},{"name":"接口测试","level":"熟练","required":true},{"name":"CI/CD","level":"熟悉","required":false}]',
     '[{"name":"严谨细致","importance":"重要"},{"name":"推动力","importance":"一般"}]',
     '有SaaS平台质量保障经验优先',
     '[{"requirement":"有质量体系建设经验","basis":"需要搭建测试体系","inferred":true,"confidence":0.7}]',
     '["自动化框架设计","测试用例设计","线上质量度量"]',
     'MANUAL', 1, 8, '2026-08-02 11:00:00', '2026-08-02 11:00:00', '2026-08-02 11:00:00'),

    (7, 1, 8, 'DEVOPS',
     '[{"name":"Linux","level":"精通","required":true},{"name":"监控告警","level":"熟练","required":true},{"name":"容器化","level":"熟练","required":true},{"name":"Kubernetes","level":"熟悉","required":false}]',
     '[{"name":"抗压能力","importance":"重要"},{"name":"应急处置","importance":"重要"}]',
     '有互联网SRE经验优先',
     '[{"requirement":"能独立处理线上故障","basis":"运维岗需要7x24应急","inferred":true,"confidence":0.8}]',
     '["故障定位与应急","容量规划","CI/CD流水线"]',
     'MANUAL', 1, 12, '2026-07-31 11:00:00', '2026-07-31 11:00:00', '2026-07-31 11:00:00'),

    (8, 1, 9, 'DATA_ENGINEER',
     '[{"name":"Hive","level":"熟练","required":true},{"name":"Spark","level":"熟练","required":true},{"name":"Flink","level":"熟悉","required":false},{"name":"ETL","level":"精通","required":true}]',
     '[{"name":"逻辑思维","importance":"重要"},{"name":"业务理解","importance":"重要"}]',
     '有数据中台建设经验优先',
     '[{"requirement":"有数仓建模方法论","basis":"需要主导数仓模型设计","inferred":true,"confidence":0.8}]',
     '["数据建模","ETL性能优化","数据质量治理"]',
     'MANUAL', 1, 8, '2026-07-28 11:00:00', '2026-07-28 11:00:00', '2026-07-28 11:00:00'),

    (9, 1, 10, 'CUSTOMER_SUCCESS',
     '[{"name":"客户运营","level":"熟练","required":true},{"name":"续费增购","level":"熟练","required":true}]',
     '[{"name":"服务意识","importance":"重要"},{"name":"沟通表达","importance":"重要"}]',
     '有企业服务/ToB客户运营经验优先',
     '[{"requirement":"能承受客户交付压力","basis":"SaaS客户成功岗","inferred":true,"confidence":0.7}]',
     '["客户关系维护","续费与增购策略","客户成功案例"]',
     'MANUAL', 1, 12, '2026-08-03 11:00:00', '2026-08-03 11:00:00', '2026-08-03 11:00:00'),

    (10, 1, 11, 'UI_DESIGNER',
     '[{"name":"界面设计","level":"熟练","required":true},{"name":"交互设计","level":"熟练","required":true},{"name":"Figma","level":"熟练","required":false}]',
     '[{"name":"审美","importance":"重要"},{"name":"沟通","importance":"重要"}]',
     '有B端/SaaS设计经验优先',
     '[{"requirement":"能独立搭建设计规范","basis":"需要建设设计组件库","inferred":true,"confidence":0.7}]',
     '["设计规范与组件库","高保真交付","设计走查"]',
     'MANUAL', 1, 8, '2026-08-01 11:00:00', '2026-08-01 11:00:00', '2026-08-01 11:00:00');

-- ============================================================
-- 3. 求职者用户（sys_user，id=20~29，CANDIDATE）
-- ============================================================
INSERT INTO sys_user
    (id, phone, password_hash, name, avatar, email, role, status, created_at, updated_at, name_updated_at, first_login)
VALUES
    (20, '13800000020', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '张伟', NULL, 'zhangwei@example.com', 'CANDIDATE', 'ACTIVE', '2026-08-01 09:00:00', '2026-08-01 09:00:00', NULL, 0),
    (21, '13800000021', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '李娜', NULL, 'lina@example.com',        'CANDIDATE', 'ACTIVE', '2026-08-01 09:00:00', '2026-08-01 09:00:00', NULL, 0),
    (22, '13800000022', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '王强', NULL, 'wangqiang@example.com',   'CANDIDATE', 'ACTIVE', '2026-08-02 09:00:00', '2026-08-02 09:00:00', NULL, 0),
    (23, '13800000023', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '刘洋', NULL, 'liuyang@example.com',     'CANDIDATE', 'ACTIVE', '2026-08-02 09:00:00', '2026-08-02 09:00:00', NULL, 0),
    (24, '13800000024', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '陈静', NULL, 'chenjing@example.com',    'CANDIDATE', 'ACTIVE', '2026-08-02 09:00:00', '2026-08-02 09:00:00', NULL, 0),
    (25, '13800000025', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '杨光', NULL, 'yangguang@example.com',   'CANDIDATE', 'ACTIVE', '2026-08-03 09:00:00', '2026-08-03 09:00:00', NULL, 0),
    (26, '13800000026', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '赵敏', NULL, 'zhaomin@example.com',     'CANDIDATE', 'ACTIVE', '2026-08-03 09:00:00', '2026-08-03 09:00:00', NULL, 0),
    (27, '13800000027', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '黄磊', NULL, 'huanglei@example.com',    'CANDIDATE', 'ACTIVE', '2026-08-03 09:00:00', '2026-08-03 09:00:00', NULL, 0),
    (28, '13800000028', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '周杰', NULL, 'zhoujie@example.com',     'CANDIDATE', 'ACTIVE', '2026-08-04 09:00:00', '2026-08-04 09:00:00', NULL, 0),
    (29, '13800000029', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '吴婷', NULL, 'wuting@example.com',       'CANDIDATE', 'ACTIVE', '2026-08-04 09:00:00', '2026-08-04 09:00:00', NULL, 0);

-- ============================================================
-- 4. 求职者画像（sys_user_profile，id=3~12）
--    ⚠ 服务器基线已存在 id=1/2，这里从 3 起避开冲突
-- ============================================================
INSERT INTO sys_user_profile
    (id, user_id, city, job_status, desired_job, desired_city, desired_salary_min, desired_salary_max,
     available_from, resume_public, blind_mode, created_at, updated_at, gender, work_years, education, match_notify)
VALUES
    (3,  20, '北京', 'JOB_SEEKING',       'Java后端开发工程师', '北京', 18000, 25000, '2026-09-01', 1, 0, '2026-08-01 09:00:00', '2026-08-01 09:00:00', 'MALE',   '3-5',  'BACHELOR', 1),
    (4,  21, '北京', 'JOB_SEEKING',       '前端开发工程师',     '北京', 15000, 22000, '2026-09-01', 1, 0, '2026-08-01 09:00:00', '2026-08-01 09:00:00', 'FEMALE', '1-3',  'BACHELOR', 1),
    (5,  22, '北京', 'EMPLOYED_LOOKING',  '高级Java开发工程师', '北京', 25000, 35000, '2026-10-01', 1, 0, '2026-08-02 09:00:00', '2026-08-02 09:00:00', 'MALE',   '5-10', 'MASTER',   1),
    (6,  23, '北京', 'JOB_SEEKING',       '产品经理',           '北京', 22000, 30000, '2026-09-01', 1, 0, '2026-08-02 09:00:00', '2026-08-02 09:00:00', 'MALE',   '3-5',  'BACHELOR', 1),
    (7,  24, '北京', 'JOB_SEEKING',       '测试开发工程师',     '北京', 16000, 22000, '2026-09-01', 1, 0, '2026-08-02 09:00:00', '2026-08-02 09:00:00', 'FEMALE', '3-5',  'BACHELOR', 1),
    (8,  25, '北京', 'JOB_SEEKING',       '运维/SRE工程师',     '北京', 20000, 28000, '2026-09-01', 1, 0, '2026-08-03 09:00:00', '2026-08-03 09:00:00', 'MALE',   '3-5',  'BACHELOR', 1),
    (9,  26, '北京', 'EMPLOYED_LOOKING',  '数据仓库工程师',     '北京', 30000, 42000, '2026-10-01', 1, 0, '2026-08-03 09:00:00', '2026-08-03 09:00:00', 'FEMALE', '5-10', 'MASTER',   1),
    (10, 27, '北京', 'JOB_SEEKING',       '客户成功经理',       '北京', 12000, 18000, '2026-09-01', 1, 0, '2026-08-03 09:00:00', '2026-08-03 09:00:00', 'MALE',   '1-3',  'COLLEGE',  1),
    (11, 28, '北京', 'EMPLOYED_LOOKING',  '高级前端工程师',     '北京', 28000, 40000, '2026-10-01', 1, 0, '2026-08-04 09:00:00', '2026-08-04 09:00:00', 'MALE',   '5-10', 'BACHELOR', 1),
    (12, 29, '北京', 'JOB_SEEKING',       'UI设计师',           '北京', 14000, 20000, '2026-09-01', 1, 0, '2026-08-04 09:00:00', '2026-08-04 09:00:00', 'FEMALE', '1-3',  'BACHELOR', 1);

-- ============================================================
-- 5. 简历（resume，id=100~109，均为默认简历且解析完成）
-- ============================================================
INSERT INTO resume
    (id, candidate_id, file_name, file_url, file_format, file_size, is_default, parse_status,
     resume_md_url, face_photo_url, card_structure, phone, email, wechat, candidate_name,
     deleted_at, created_at, updated_at)
VALUES
    (100, 20, '张伟-5年Java后端开发.pdf', 'oss://lingxi-h/resumes/100/张伟-5年Java后端开发.pdf', 'pdf', 204800, 1, 'COMPLETED',
     '/resumes/100/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"5年Java后端开发经验，主导过电商订单中心"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Java/Spring Boot/MySQL/Redis"},{"id":"p2","text":"RocketMQ分布式消息"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"电商平台订单中心，日订单百万级"}]}]}',
     '13800000020', 'zhangwei@example.com', 'zw_2020', '张伟', NULL, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),

    (101, 21, '李娜-前端开发简历.pdf', 'oss://lingxi-h/resumes/101/李娜-前端开发简历.pdf', 'pdf', 180000, 1, 'COMPLETED',
     '/resumes/101/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"3年前端开发经验，熟悉React技术栈"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"React/TypeScript/Vite"},{"id":"p2","text":"Webpack/Vite工程化"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"企业级中后台管理系统"}]}]}',
     '13800000021', 'lina@example.com', 'lina_fe', '李娜', NULL, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),

    (102, 22, '王强-高级Java开发.pdf', 'oss://lingxi-h/resumes/102/王强-高级Java开发.pdf', 'pdf', 260000, 1, 'COMPLETED',
     '/resumes/102/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"6年Java经验，硕士学历，主导过高并发支付系统"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Java/Spring Cloud/MySQL"},{"id":"p2","text":"分布式事务/高并发架构"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"支付平台，峰值QPS 2万+"}]}]}',
     '13800000022', 'wangqiang@example.com', 'wq_pay', '王强', NULL, '2026-08-02 09:00:00', '2026-08-02 09:00:00'),

    (103, 23, '刘洋-产品经理简历.pdf', 'oss://lingxi-h/resumes/103/刘洋-产品经理简历.pdf', 'pdf', 160000, 1, 'COMPLETED',
     '/resumes/103/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"4年SaaS产品经验，主导过2个产品从0到1"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"需求分析/PRD/原型"},{"id":"p2","text":"数据分析/SQL"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"企业协作SaaS平台"}]}]}',
     '13800000023', 'liuyang@example.com', 'ly_pm', '刘洋', NULL, '2026-08-02 09:00:00', '2026-08-02 09:00:00'),

    (104, 24, '陈静-测试开发简历.pdf', 'oss://lingxi-h/resumes/104/陈静-测试开发简历.pdf', 'pdf', 150000, 1, 'COMPLETED',
     '/resumes/104/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"3年测试开发经验，搭建过自动化测试体系"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Python/Java自动化"},{"id":"p2","text":"接口测试/JMeter/CI"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"SaaS平台自动化测试框架"}]}]}',
     '13800000024', 'chenjing@example.com', 'cj_qa', '陈静', NULL, '2026-08-02 09:00:00', '2026-08-02 09:00:00'),

    (105, 25, '杨光-运维工程师简历.pdf', 'oss://lingxi-h/resumes/105/杨光-运维工程师简历.pdf', 'pdf', 170000, 1, 'COMPLETED',
     '/resumes/105/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"4年运维经验，主导过容器化改造"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Linux/Docker/Kubernetes"},{"id":"p2","text":"监控告警/故障应急"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"100+微服务容器化与CI/CD"}]}]}',
     '13800000025', 'yangguang@example.com', 'yg_ops', '杨光', NULL, '2026-08-03 09:00:00', '2026-08-03 09:00:00'),

    (106, 26, '赵敏-数据仓库简历.pdf', 'oss://lingxi-h/resumes/106/赵敏-数据仓库简历.pdf', 'pdf', 240000, 1, 'COMPLETED',
     '/resumes/106/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"5年数据经验，硕士学历，主导过数据中台建设"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Hive/Spark/Flink"},{"id":"p2","text":"ETL/数仓建模"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"集团数据中台，300+指标"}]}]}',
     '13800000026', 'zhaomin@example.com', 'zm_data', '赵敏', NULL, '2026-08-03 09:00:00', '2026-08-03 09:00:00'),

    (107, 27, '黄磊-客户成功简历.pdf', 'oss://lingxi-h/resumes/107/黄磊-客户成功简历.pdf', 'pdf', 120000, 1, 'COMPLETED',
     '/resumes/107/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"2年ToB客户运营经验，续费率达95%"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"客户运营/续费增购"},{"id":"p2","text":"企业服务/CRM"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"服务40+企业客户"}]}]}',
     '13800000027', 'huanglei@example.com', 'hl_cs', '黄磊', NULL, '2026-08-03 09:00:00', '2026-08-03 09:00:00'),

    (108, 28, '周杰-高级前端简历.pdf', 'oss://lingxi-h/resumes/108/周杰-高级前端简历.pdf', 'pdf', 230000, 1, 'COMPLETED',
     '/resumes/108/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"6年前端经验，主导过微前端架构改造"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"React/微前端/工程化"},{"id":"p2","text":"性能优化/监控"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"大型中后台微前端改造，20+子应用"}]}]}',
     '13800000028', 'zhoujie@example.com', 'zj_fe', '周杰', NULL, '2026-08-04 09:00:00', '2026-08-04 09:00:00'),

    (109, 29, '吴婷-UI设计简历.pdf', 'oss://lingxi-h/resumes/109/吴婷-UI设计简历.pdf', 'pdf', 110000, 1, 'COMPLETED',
     '/resumes/109/resume.md', NULL,
     '{"cards":[{"id":"c1","title":"个人优势","points":[{"id":"p1","text":"2年SaaS产品设计经验"}]},{"id":"c2","title":"技能","points":[{"id":"p1","text":"Figma/界面设计/交互设计"},{"id":"p2","text":"设计规范搭建"}]},{"id":"c3","title":"项目","points":[{"id":"p1","text":"SaaS后台设计规范与组件库"}]}]}',
     '13800000029', 'wuting@example.com', 'wt_ui', '吴婷', NULL, '2026-08-04 09:00:00', '2026-08-04 09:00:00');

-- ============================================================
-- 6. 能力模型（resume_ability_model，id=100~109，与简历一一对应）
-- ============================================================
INSERT INTO resume_ability_model
    (id, candidate_id, resume_id, professional_skill_score, work_experience_score, industry_knowledge_score,
     comprehensive_quality_score, learning_growth_score, sub_dimensions, created_at, updated_at)
VALUES
    (100, 20, 100, 82, 85, 70, 78, 80, '{"skills":["Java","Spring Boot","MySQL","Redis"],"workYears":"5年"}', '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
    (101, 21, 101, 78, 72, 68, 75, 82, '{"skills":["React","TypeScript","Vite"],"workYears":"3年"}',   '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
    (102, 22, 102, 92, 90, 80, 85, 82, '{"skills":["Java","Spring Cloud","MySQL"],"workYears":"6年"}',  '2026-08-02 09:00:00', '2026-08-02 09:00:00'),
    (103, 23, 103, 75, 78, 82, 84, 80, '{"skills":["需求分析","PRD","数据分析"],"workYears":"4年"}',      '2026-08-02 09:00:00', '2026-08-02 09:00:00'),
    (104, 24, 104, 80, 76, 70, 78, 84, '{"skills":["自动化测试","接口测试","CI"],"workYears":"3年"}',    '2026-08-02 09:00:00', '2026-08-02 09:00:00'),
    (105, 25, 105, 84, 82, 72, 80, 78, '{"skills":["Linux","Docker","Kubernetes"],"workYears":"4年"}',  '2026-08-03 09:00:00', '2026-08-03 09:00:00'),
    (106, 26, 106, 90, 86, 82, 84, 80, '{"skills":["Hive","Spark","Flink","ETL"],"workYears":"5年"}',   '2026-08-03 09:00:00', '2026-08-03 09:00:00'),
    (107, 27, 107, 72, 70, 80, 82, 76, '{"skills":["客户运营","续费增购","CRM"],"workYears":"2年"}',     '2026-08-03 09:00:00', '2026-08-03 09:00:00'),
    (108, 28, 108, 88, 90, 78, 86, 84, '{"skills":["React","微前端","工程化"],"workYears":"6年"}',       '2026-08-04 09:00:00', '2026-08-04 09:00:00'),
    (109, 29, 109, 82, 68, 66, 80, 84, '{"skills":["Figma","界面设计","交互设计"],"workYears":"2年"}',   '2026-08-04 09:00:00', '2026-08-04 09:00:00');

-- ============================================================
-- 7. 投递记录（resume_application，id=50100~50129）
--    状态机：SUBMITTED/VIEWED/SCREENED/INTERVIEWING/OFFERABLE/OFFERED/REJECTED
--    均投递到公司1的岗位，匹配分快照由 Job Agent 口径模拟
-- ============================================================
INSERT INTO resume_application
    (id, job_id, candidate_id, resume_id, status, match_score, reject_feedback,
     submitted_at, viewed_at, screened_at, interviewing_at, offerable_at, offered_at, rejected_at, withdrawn_at,
     created_at, updated_at)
VALUES
    -- 张伟(20)
    (50100, 2, 20, 100, 'INTERVIEWING', 88.00, NULL,
     '2026-08-02 09:30:00', '2026-08-02 14:00:00', '2026-08-03 10:00:00', '2026-08-04 11:00:00', NULL, NULL, NULL, NULL, '2026-08-02 09:30:00', '2026-08-04 11:00:00'),
    (50101, 3, 20, 100, 'SCREENED',     92.00, NULL,
     '2026-08-01 10:00:00', '2026-08-01 16:00:00', '2026-08-03 09:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-01 10:00:00', '2026-08-03 09:00:00'),
    (50102, 9, 20, 100, 'VIEWED',       70.00, NULL,
     '2026-08-02 11:00:00', '2026-08-03 09:30:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-02 11:00:00', '2026-08-03 09:30:00'),
    -- 李娜(21)
    (50103, 4, 21, 101, 'SCREENED',     90.00, NULL,
     '2026-08-01 09:00:00', '2026-08-01 15:00:00', '2026-08-02 10:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-01 09:00:00', '2026-08-02 10:00:00'),
    (50104, 5, 21, 101, 'SUBMITTED',    78.00, NULL,
     '2026-08-02 10:30:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-02 10:30:00', '2026-08-02 10:30:00'),
    (50105, 11, 21, 101, 'REJECTED',    45.00,
     '{"reason":"您的设计经验与岗位要求存在一定差距，暂不匹配。","suggestions":["补充B端设计项目案例","突出设计规范搭建能力"]}',
     '2026-08-02 15:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-03 16:00:00', NULL, '2026-08-02 15:00:00', '2026-08-03 16:00:00'),
    -- 王强(22)
    (50106, 2, 22, 102, 'OFFERED',      95.00, NULL,
     '2026-08-02 09:00:00', '2026-08-02 13:00:00', '2026-08-03 10:30:00', '2026-08-04 09:30:00', '2026-08-04 17:00:00', '2026-08-05 10:00:00', NULL, NULL, '2026-08-02 09:00:00', '2026-08-05 10:00:00'),
    (50107, 3, 22, 102, 'SCREENED',     88.00, NULL,
     '2026-08-03 10:00:00', '2026-08-03 14:00:00', '2026-08-04 10:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-03 10:00:00', '2026-08-04 10:00:00'),
    (50108, 9, 22, 102, 'SUBMITTED',    65.00, NULL,
     '2026-08-03 15:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-03 15:00:00', '2026-08-03 15:00:00'),
    -- 刘洋(23)
    (50109, 6, 23, 103, 'INTERVIEWING', 85.00, NULL,
     '2026-08-02 09:30:00', '2026-08-02 15:00:00', '2026-08-03 10:00:00', '2026-08-04 14:00:00', NULL, NULL, NULL, NULL, '2026-08-02 09:30:00', '2026-08-04 14:00:00'),
    (50110, 10, 23, 103, 'VIEWED',      72.00, NULL,
     '2026-08-02 14:00:00', '2026-08-03 11:00:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-02 14:00:00', '2026-08-03 11:00:00'),
    (50111, 2, 23, 103, 'REJECTED',     40.00,
     '{"reason":"您的产品背景与本岗位技术方向不匹配。","suggestions":["建议关注产品类岗位机会"]}',
     '2026-08-02 16:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-03 15:00:00', NULL, '2026-08-02 16:00:00', '2026-08-03 15:00:00'),
    (50112, 9, 23, 103, 'REJECTED',     52.00,
     '{"reason":"您缺少数据仓库方向的实操经验。","suggestions":["补充ETL/数仓项目经历"]}',
     '2026-08-03 09:00:00', '2026-08-03 16:00:00', NULL, NULL, NULL, NULL, '2026-08-04 10:00:00', NULL, '2026-08-03 09:00:00', '2026-08-04 10:00:00'),
    -- 陈静(24)
    (50113, 7, 24, 104, 'OFFERABLE',    89.00, NULL,
     '2026-08-03 10:00:00', '2026-08-03 15:00:00', '2026-08-04 10:00:00', '2026-08-05 09:00:00', '2026-08-05 14:00:00', NULL, NULL, NULL, '2026-08-03 10:00:00', '2026-08-05 14:00:00'),
    (50114, 4, 24, 104, 'REJECTED',     55.00,
     '{"reason":"您的前端开发经验与岗位要求存在差距。","suggestions":["补充前端实际项目经历"]}',
     '2026-08-03 11:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-04 11:00:00', NULL, '2026-08-03 11:00:00', '2026-08-04 11:00:00'),
    -- 杨光(25)
    (50115, 8, 25, 105, 'INTERVIEWING', 86.00, NULL,
     '2026-08-03 09:30:00', '2026-08-03 15:30:00', '2026-08-04 10:00:00', '2026-08-05 09:30:00', NULL, NULL, NULL, NULL, '2026-08-03 09:30:00', '2026-08-05 09:30:00'),
    (50116, 3, 25, 105, 'VIEWED',       60.00, NULL,
     '2026-08-03 14:00:00', '2026-08-04 14:00:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-03 14:00:00', '2026-08-04 14:00:00'),
    (50117, 7, 25, 105, 'SUBMITTED',    68.00, NULL,
     '2026-08-04 10:30:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-04 10:30:00', '2026-08-04 10:30:00'),
    -- 赵敏(26)
    (50118, 9, 26, 106, 'OFFERED',      93.00, NULL,
     '2026-08-03 10:30:00', '2026-08-03 16:00:00', '2026-08-04 10:30:00', '2026-08-04 15:00:00', '2026-08-05 10:00:00', '2026-08-05 15:00:00', NULL, NULL, '2026-08-03 10:30:00', '2026-08-05 15:00:00'),
    (50119, 6, 26, 106, 'VIEWED',       66.00, NULL,
     '2026-08-04 09:00:00', '2026-08-04 15:00:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-04 09:00:00', '2026-08-04 15:00:00'),
    (50120, 10, 26, 106, 'REJECTED',    48.00,
     '{"reason":"您更偏数据方向，客户成功岗位匹配度不足。","suggestions":["关注数据类岗位机会"]}',
     '2026-08-04 11:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-05 09:30:00', NULL, '2026-08-04 11:00:00', '2026-08-05 09:30:00'),
    -- 黄磊(27)
    (50121, 10, 27, 107, 'SCREENED',    91.00, NULL,
     '2026-08-03 10:00:00', '2026-08-03 16:30:00', '2026-08-04 11:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-03 10:00:00', '2026-08-04 11:00:00'),
    (50122, 6, 27, 107, 'SUBMITTED',    74.00, NULL,
     '2026-08-04 10:00:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-04 10:00:00', '2026-08-04 10:00:00'),
    (50123, 8, 27, 107, 'REJECTED',     42.00,
     '{"reason":"您缺少运维技术背景。","suggestions":["补充Linux/网络基础知识"]}',
     '2026-08-04 14:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-05 11:00:00', NULL, '2026-08-04 14:00:00', '2026-08-05 11:00:00'),
    -- 周杰(28)
    (50124, 5, 28, 108, 'INTERVIEWING', 87.00, NULL,
     '2026-08-04 09:30:00', '2026-08-04 15:00:00', '2026-08-05 10:00:00', '2026-08-05 16:00:00', NULL, NULL, NULL, NULL, '2026-08-04 09:30:00', '2026-08-05 16:00:00'),
    (50125, 4, 28, 108, 'VIEWED',       79.00, NULL,
     '2026-08-04 11:00:00', '2026-08-05 10:30:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-04 11:00:00', '2026-08-05 10:30:00'),
    (50126, 2, 28, 108, 'REJECTED',     50.00,
     '{"reason":"您主攻前端方向，Java后端岗位匹配度不足。","suggestions":["关注前端类岗位机会"]}',
     '2026-08-04 15:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-05 15:30:00', NULL, '2026-08-04 15:00:00', '2026-08-05 15:30:00'),
    -- 吴婷(29)
    (50127, 11, 29, 109, 'SCREENED',    88.00, NULL,
     '2026-08-04 10:00:00', '2026-08-04 15:30:00', '2026-08-05 11:00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-04 10:00:00', '2026-08-05 11:00:00'),
    (50128, 4, 29, 109, 'VIEWED',       75.00, NULL,
     '2026-08-04 13:00:00', '2026-08-05 11:30:00', NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-04 13:00:00', '2026-08-05 11:30:00'),
    (50129, 5, 29, 109, 'SUBMITTED',    70.00, NULL,
     '2026-08-05 09:30:00', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-05 09:30:00', '2026-08-05 09:30:00');

-- ============================================================
-- 8. 投递状态流转日志（resume_status_log，时间线）
--    幂等键格式对齐 C 侧：app:{applicationId}:{from}:{to}:{operatorId}
-- ============================================================
INSERT INTO resume_status_log
    (application_id, from_status, to_status, operator_id, operator_role, reason, idempotent_key, created_at)
VALUES
    -- 50100 张伟@高级Java：SUBMITTED→VIEWED→SCREENED→INTERVIEWING
    (50100, NULL, 'SUBMITTED', 20, 'CANDIDATE', '一键投递', 'app:50100:null:SUBMITTED:20', '2026-08-02 09:30:00'),
    (50100, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50100:SUBMITTED:VIEWED:8', '2026-08-02 14:00:00'),
    (50100, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50100:VIEWED:SCREENED:8', '2026-08-03 10:00:00'),
    (50100, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50100:SCREENED:INTERVIEWING:8', '2026-08-04 11:00:00'),
    -- 50101 张伟@Java开发：SUBMITTED→VIEWED→SCREENED
    (50101, NULL, 'SUBMITTED', 20, 'CANDIDATE', '一键投递', 'app:50101:null:SUBMITTED:20', '2026-08-01 10:00:00'),
    (50101, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50101:SUBMITTED:VIEWED:8', '2026-08-01 16:00:00'),
    (50101, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50101:VIEWED:SCREENED:8', '2026-08-03 09:00:00'),
    -- 50102 张伟@数据仓库：SUBMITTED→VIEWED
    (50102, NULL, 'SUBMITTED', 20, 'CANDIDATE', '一键投递', 'app:50102:null:SUBMITTED:20', '2026-08-02 11:00:00'),
    (50102, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50102:SUBMITTED:VIEWED:8', '2026-08-03 09:30:00'),
    -- 50103 李娜@前端：SUBMITTED→VIEWED→SCREENED
    (50103, NULL, 'SUBMITTED', 21, 'CANDIDATE', '一键投递', 'app:50103:null:SUBMITTED:21', '2026-08-01 09:00:00'),
    (50103, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50103:SUBMITTED:VIEWED:8', '2026-08-01 15:00:00'),
    (50103, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50103:VIEWED:SCREENED:8', '2026-08-02 10:00:00'),
    -- 50104 李娜@高级前端：SUBMITTED
    (50104, NULL, 'SUBMITTED', 21, 'CANDIDATE', '一键投递', 'app:50104:null:SUBMITTED:21', '2026-08-02 10:30:00'),
    -- 50105 李娜@UI：SUBMITTED→REJECTED
    (50105, NULL, 'SUBMITTED', 21, 'CANDIDATE', '一键投递', 'app:50105:null:SUBMITTED:21', '2026-08-02 15:00:00'),
    (50105, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50105:SUBMITTED:REJECTED:8', '2026-08-03 16:00:00'),
    -- 50106 王强@高级Java：SUBMITTED→VIEWED→SCREENED→INTERVIEWING→OFFERABLE→OFFERED
    (50106, NULL, 'SUBMITTED', 22, 'CANDIDATE', '一键投递', 'app:50106:null:SUBMITTED:22', '2026-08-02 09:00:00'),
    (50106, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50106:SUBMITTED:VIEWED:8', '2026-08-02 13:00:00'),
    (50106, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50106:VIEWED:SCREENED:8', '2026-08-03 10:30:00'),
    (50106, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50106:SCREENED:INTERVIEWING:8', '2026-08-04 09:30:00'),
    (50106, 'INTERVIEWING', 'OFFERABLE', 0, 'SYSTEM', '面试通过', 'app:50106:INTERVIEWING:OFFERABLE:0', '2026-08-04 17:00:00'),
    (50106, 'OFFERABLE', 'OFFERED', 22, 'CANDIDATE', '接受Offer', 'app:50106:OFFERABLE:OFFERED:22', '2026-08-05 10:00:00'),
    -- 50107 王强@Java开发：SUBMITTED→VIEWED→SCREENED
    (50107, NULL, 'SUBMITTED', 22, 'CANDIDATE', '一键投递', 'app:50107:null:SUBMITTED:22', '2026-08-03 10:00:00'),
    (50107, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50107:SUBMITTED:VIEWED:8', '2026-08-03 14:00:00'),
    (50107, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50107:VIEWED:SCREENED:8', '2026-08-04 10:00:00'),
    -- 50108 王强@数据仓库：SUBMITTED
    (50108, NULL, 'SUBMITTED', 22, 'CANDIDATE', '一键投递', 'app:50108:null:SUBMITTED:22', '2026-08-03 15:00:00'),
    -- 50109 刘洋@产品经理：SUBMITTED→VIEWED→SCREENED→INTERVIEWING
    (50109, NULL, 'SUBMITTED', 23, 'CANDIDATE', '一键投递', 'app:50109:null:SUBMITTED:23', '2026-08-02 09:30:00'),
    (50109, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50109:SUBMITTED:VIEWED:8', '2026-08-02 15:00:00'),
    (50109, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50109:VIEWED:SCREENED:8', '2026-08-03 10:00:00'),
    (50109, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50109:SCREENED:INTERVIEWING:8', '2026-08-04 14:00:00'),
    -- 50110 刘洋@客户成功：SUBMITTED→VIEWED
    (50110, NULL, 'SUBMITTED', 23, 'CANDIDATE', '一键投递', 'app:50110:null:SUBMITTED:23', '2026-08-02 14:00:00'),
    (50110, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50110:SUBMITTED:VIEWED:8', '2026-08-03 11:00:00'),
    -- 50111 刘洋@高级Java：SUBMITTED→REJECTED
    (50111, NULL, 'SUBMITTED', 23, 'CANDIDATE', '一键投递', 'app:50111:null:SUBMITTED:23', '2026-08-02 16:00:00'),
    (50111, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50111:SUBMITTED:REJECTED:8', '2026-08-03 15:00:00'),
    -- 50112 刘洋@数据仓库：SUBMITTED→VIEWED→REJECTED
    (50112, NULL, 'SUBMITTED', 23, 'CANDIDATE', '一键投递', 'app:50112:null:SUBMITTED:23', '2026-08-03 09:00:00'),
    (50112, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50112:SUBMITTED:VIEWED:8', '2026-08-03 16:00:00'),
    (50112, 'VIEWED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50112:VIEWED:REJECTED:8', '2026-08-04 10:00:00'),
    -- 50113 陈静@测试开发：SUBMITTED→VIEWED→SCREENED→INTERVIEWING→OFFERABLE
    (50113, NULL, 'SUBMITTED', 24, 'CANDIDATE', '一键投递', 'app:50113:null:SUBMITTED:24', '2026-08-03 10:00:00'),
    (50113, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50113:SUBMITTED:VIEWED:8', '2026-08-03 15:00:00'),
    (50113, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50113:VIEWED:SCREENED:8', '2026-08-04 10:00:00'),
    (50113, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50113:SCREENED:INTERVIEWING:8', '2026-08-05 09:00:00'),
    (50113, 'INTERVIEWING', 'OFFERABLE', 0, 'SYSTEM', '面试通过', 'app:50113:INTERVIEWING:OFFERABLE:0', '2026-08-05 14:00:00'),
    -- 50114 陈静@前端：SUBMITTED→REJECTED
    (50114, NULL, 'SUBMITTED', 24, 'CANDIDATE', '一键投递', 'app:50114:null:SUBMITTED:24', '2026-08-03 11:00:00'),
    (50114, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50114:SUBMITTED:REJECTED:8', '2026-08-04 11:00:00'),
    -- 50115 杨光@运维：SUBMITTED→VIEWED→SCREENED→INTERVIEWING
    (50115, NULL, 'SUBMITTED', 25, 'CANDIDATE', '一键投递', 'app:50115:null:SUBMITTED:25', '2026-08-03 09:30:00'),
    (50115, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50115:SUBMITTED:VIEWED:8', '2026-08-03 15:30:00'),
    (50115, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50115:VIEWED:SCREENED:8', '2026-08-04 10:00:00'),
    (50115, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50115:SCREENED:INTERVIEWING:8', '2026-08-05 09:30:00'),
    -- 50116 杨光@Java开发：SUBMITTED→VIEWED
    (50116, NULL, 'SUBMITTED', 25, 'CANDIDATE', '一键投递', 'app:50116:null:SUBMITTED:25', '2026-08-03 14:00:00'),
    (50116, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50116:SUBMITTED:VIEWED:8', '2026-08-04 14:00:00'),
    -- 50117 杨光@测试开发：SUBMITTED
    (50117, NULL, 'SUBMITTED', 25, 'CANDIDATE', '一键投递', 'app:50117:null:SUBMITTED:25', '2026-08-04 10:30:00'),
    -- 50118 赵敏@数据仓库：SUBMITTED→VIEWED→SCREENED→INTERVIEWING→OFFERABLE→OFFERED
    (50118, NULL, 'SUBMITTED', 26, 'CANDIDATE', '一键投递', 'app:50118:null:SUBMITTED:26', '2026-08-03 10:30:00'),
    (50118, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50118:SUBMITTED:VIEWED:8', '2026-08-03 16:00:00'),
    (50118, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50118:VIEWED:SCREENED:8', '2026-08-04 10:30:00'),
    (50118, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50118:SCREENED:INTERVIEWING:8', '2026-08-04 15:00:00'),
    (50118, 'INTERVIEWING', 'OFFERABLE', 0, 'SYSTEM', '面试通过', 'app:50118:INTERVIEWING:OFFERABLE:0', '2026-08-05 10:00:00'),
    (50118, 'OFFERABLE', 'OFFERED', 26, 'CANDIDATE', '接受Offer', 'app:50118:OFFERABLE:OFFERED:26', '2026-08-05 15:00:00'),
    -- 50119 赵敏@产品经理：SUBMITTED→VIEWED
    (50119, NULL, 'SUBMITTED', 26, 'CANDIDATE', '一键投递', 'app:50119:null:SUBMITTED:26', '2026-08-04 09:00:00'),
    (50119, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50119:SUBMITTED:VIEWED:8', '2026-08-04 15:00:00'),
    -- 50120 赵敏@客户成功：SUBMITTED→REJECTED
    (50120, NULL, 'SUBMITTED', 26, 'CANDIDATE', '一键投递', 'app:50120:null:SUBMITTED:26', '2026-08-04 11:00:00'),
    (50120, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50120:SUBMITTED:REJECTED:8', '2026-08-05 09:30:00'),
    -- 50121 黄磊@客户成功：SUBMITTED→VIEWED→SCREENED
    (50121, NULL, 'SUBMITTED', 27, 'CANDIDATE', '一键投递', 'app:50121:null:SUBMITTED:27', '2026-08-03 10:00:00'),
    (50121, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50121:SUBMITTED:VIEWED:8', '2026-08-03 16:30:00'),
    (50121, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50121:VIEWED:SCREENED:8', '2026-08-04 11:00:00'),
    -- 50122 黄磊@产品经理：SUBMITTED
    (50122, NULL, 'SUBMITTED', 27, 'CANDIDATE', '一键投递', 'app:50122:null:SUBMITTED:27', '2026-08-04 10:00:00'),
    -- 50123 黄磊@运维：SUBMITTED→REJECTED
    (50123, NULL, 'SUBMITTED', 27, 'CANDIDATE', '一键投递', 'app:50123:null:SUBMITTED:27', '2026-08-04 14:00:00'),
    (50123, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50123:SUBMITTED:REJECTED:8', '2026-08-05 11:00:00'),
    -- 50124 周杰@高级前端：SUBMITTED→VIEWED→SCREENED→INTERVIEWING
    (50124, NULL, 'SUBMITTED', 28, 'CANDIDATE', '一键投递', 'app:50124:null:SUBMITTED:28', '2026-08-04 09:30:00'),
    (50124, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50124:SUBMITTED:VIEWED:8', '2026-08-04 15:00:00'),
    (50124, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50124:VIEWED:SCREENED:8', '2026-08-05 10:00:00'),
    (50124, 'SCREENED', 'INTERVIEWING', 8, 'HR', '预约面试', 'app:50124:SCREENED:INTERVIEWING:8', '2026-08-05 16:00:00'),
    -- 50125 周杰@前端：SUBMITTED→VIEWED
    (50125, NULL, 'SUBMITTED', 28, 'CANDIDATE', '一键投递', 'app:50125:null:SUBMITTED:28', '2026-08-04 11:00:00'),
    (50125, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50125:SUBMITTED:VIEWED:8', '2026-08-05 10:30:00'),
    -- 50126 周杰@高级Java：SUBMITTED→REJECTED
    (50126, NULL, 'SUBMITTED', 28, 'CANDIDATE', '一键投递', 'app:50126:null:SUBMITTED:28', '2026-08-04 15:00:00'),
    (50126, 'SUBMITTED', 'REJECTED', 8, 'HR', '筛选未通过', 'app:50126:SUBMITTED:REJECTED:8', '2026-08-05 15:30:00'),
    -- 50127 吴婷@UI：SUBMITTED→VIEWED→SCREENED
    (50127, NULL, 'SUBMITTED', 29, 'CANDIDATE', '一键投递', 'app:50127:null:SUBMITTED:29', '2026-08-04 10:00:00'),
    (50127, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50127:SUBMITTED:VIEWED:8', '2026-08-04 15:30:00'),
    (50127, 'VIEWED', 'SCREENED', 8, 'HR', '筛选通过', 'app:50127:VIEWED:SCREENED:8', '2026-08-05 11:00:00'),
    -- 50128 吴婷@前端：SUBMITTED→VIEWED
    (50128, NULL, 'SUBMITTED', 29, 'CANDIDATE', '一键投递', 'app:50128:null:SUBMITTED:29', '2026-08-04 13:00:00'),
    (50128, 'SUBMITTED', 'VIEWED', 8, 'HR', 'HR查看简历', 'app:50128:SUBMITTED:VIEWED:8', '2026-08-05 11:30:00'),
    -- 50129 吴婷@高级前端：SUBMITTED
    (50129, NULL, 'SUBMITTED', 29, 'CANDIDATE', '一键投递', 'app:50129:null:SUBMITTED:29', '2026-08-05 09:30:00');

-- ============================================================
-- 9. 面试记录（hr_interview，INTERVIEWING 候选人）
-- ============================================================
INSERT INTO hr_interview
    (id, company_id, application_id, interviewer_id, candidate_id, job_id,
     scheduled_at, method, location, remark, candidate_note, status, created_at, updated_at)
VALUES
    (1, 1, 50100, 15, 20, 2, '2026-08-06 14:00:00', 'ONLINE', '视频面试（腾讯会议链接：见邮件）', '技术一面', '请提前准备一段项目介绍', 'SCHEDULED', '2026-08-04 11:00:00', '2026-08-04 15:00:00'),
    (2, 1, 50109, 15, 23, 6, '2026-08-07 10:00:00', 'OFFLINE', '北京朝阳区XX大厦3层面试间', '产品一面', '请携带作品/PRD案例', 'PENDING',   '2026-08-04 14:00:00', '2026-08-04 14:00:00'),
    (3, 1, 50115, 15, 25, 8, '2026-08-06 16:00:00', 'ONLINE', '视频面试（腾讯会议链接：见邮件）', '技术一面', NULL, 'SCHEDULED', '2026-08-05 09:30:00', '2026-08-05 11:00:00'),
    (4, 1, 50124, 15, 28, 5, '2026-08-07 15:00:00', 'OFFLINE', '北京海淀区中关村XX大厦9层', '技术一面', '请准备前端架构案例', 'PENDING',   '2026-08-05 16:00:00', '2026-08-05 16:00:00');

-- ============================================================
-- 10. Offer（hr_offer，id 为 Snowflake 显式值）
-- ============================================================
INSERT INTO hr_offer
    (id, company_id, application_id, candidate_id, job_id, salary, entry_date, level, remark,
     status, expires_at, accepted_at, rejected_at, reject_reason, urge_count, last_urge_at, last_sync_time,
     created_at, updated_at)
VALUES
    (60100, 1, 50106, 22, 2, 30000, '2026-09-01', 'P6', '高级Java工程师Offer',
     'ACCEPTED', '2026-08-15 23:59:59', '2026-08-05 10:00:00', NULL, NULL, 0, NULL, '2026-08-05 10:00:00',
     '2026-08-04 17:30:00', '2026-08-05 10:00:00'),
    (60101, 1, 50118, 26, 9, 40000, '2026-09-15', 'P7', '数据仓库工程师Offer',
     'ACCEPTED', '2026-08-16 23:59:59', '2026-08-05 15:00:00', NULL, NULL, 0, NULL, '2026-08-05 15:00:00',
     '2026-08-05 10:30:00', '2026-08-05 15:00:00'),
    (60102, 1, 50113, 24, 7, 22000, '2026-09-01', 'P5', '测试开发工程师Offer',
     'SENT', '2026-08-12 23:59:59', NULL, NULL, NULL, 0, NULL, NULL,
     '2026-08-05 14:30:00', '2026-08-05 14:30:00');

-- ============================================================
-- 11. HC 预冻结流水（job_hc_reservation，与 Offer 对应）
-- ============================================================
INSERT INTO job_hc_reservation
    (company_id, job_id, offer_id, candidate_id, status, reserved_at, confirmed_at, released_at,
     release_reason, version, created_at, updated_at)
VALUES
    (1, 2, 60100, 22, 'CONFIRMED', '2026-08-04 17:30:00', '2026-08-05 10:00:00', NULL, NULL, 0, '2026-08-04 17:30:00', '2026-08-05 10:00:00'),
    (1, 9, 60101, 26, 'CONFIRMED', '2026-08-05 10:30:00', '2026-08-05 15:00:00', NULL, NULL, 0, '2026-08-05 10:30:00', '2026-08-05 15:00:00'),
    (1, 7, 60102, 24, 'RESERVED',  '2026-08-05 14:30:00', NULL, NULL, NULL, 0, '2026-08-05 14:30:00', '2026-08-05 14:30:00');

-- ============================================================
-- 12. 同步岗位 HC 占用（与 Offer 流水一致；增量累加，避免覆盖既有数据）
-- ============================================================
UPDATE job_post SET confirmed_hc = confirmed_hc + 1, updated_at = '2026-08-05 10:00:00' WHERE id = 2;
UPDATE job_post SET confirmed_hc = confirmed_hc + 1, updated_at = '2026-08-05 15:00:00' WHERE id = 9;
UPDATE job_post SET reserved_hc  = reserved_hc + 1, updated_at = '2026-08-05 14:30:00' WHERE id = 7;

-- ============================================================
-- 13. 会话与消息（msg_*，HR 8 ↔ 候选人 20/28）
-- ============================================================
-- 会话1：HR 8 ↔ 张伟(20)（投递 50100 高级Java）
INSERT INTO msg_conversation
    (id, company_id, candidate_id, hr_id, application_id,
     last_message_preview, last_message_at, candidate_unread, hr_unread, created_at, updated_at)
VALUES
    (2, 1, 20, 8, 50100,
     '好的，那我们明天下午14:00视频面试见。', '2026-08-05 14:30:00', 1, 0, '2026-08-02 14:30:00', '2026-08-05 14:30:00');

INSERT INTO msg_conversation_member
    (id, conversation_id, user_id, member_role, unread_count, is_top, is_muted, is_deleted, last_read_at, joined_at)
VALUES
    (4, 2, 20, 'CANDIDATE', 1, 0, 0, 0, '2026-08-05 14:00:00', '2026-08-02 14:30:00'),
    (5, 2, 8,  'HR',        0, 0, 0, 0, '2026-08-05 14:30:00', '2026-08-02 14:30:00');

INSERT INTO msg_message
    (id, conversation_id, sender_id, sender_role, content_type, msg_type, content, is_read, created_at,
     media_url, file_name, file_size)
VALUES
    (21, 2, 8,  'HR',       'TEXT', 'TEXT', '您好，我是灵犀招聘的HR，看到您投递了「高级Java工程师」岗位，简历已通过初筛，想约您聊聊。', 1, '2026-08-02 14:30:00', NULL, NULL, NULL),
    (22, 2, 20, 'CANDIDATE', 'TEXT', 'TEXT', '您好，很高兴收到回复，我很期待这个机会。', 1, '2026-08-02 15:00:00', NULL, NULL, NULL),
    (23, 2, 8,  'HR',       'TEXT', 'TEXT', '方便介绍下您最近的订单中心项目吗？重点说说您负责的模块和并发设计。', 1, '2026-08-02 15:30:00', NULL, NULL, NULL),
    (24, 2, 20, 'CANDIDATE', 'TEXT', 'TEXT', '我主要负责订单状态机与库存扣减，用Redis做分布式锁，RocketMQ异步削峰，日单量百万级。', 1, '2026-08-02 16:00:00', NULL, NULL, NULL),
    (25, 2, 8,  'HR',       'TEXT', 'TEXT', '技术栈很匹配。想邀请您参加本周四的技术面试，方便吗？', 1, '2026-08-04 10:00:00', NULL, NULL, NULL),
    (26, 2, 20, 'CANDIDATE', 'TEXT', 'TEXT', '周四下午方便，感谢安排。', 1, '2026-08-04 10:30:00', NULL, NULL, NULL),
    (27, 2, 8,  'HR',       'TEXT', 'TEXT', '好的，那我们明天下午14:00视频面试见。', 0, '2026-08-05 14:30:00', NULL, NULL, NULL);

-- 会话2：HR 8 ↔ 周杰(28)（投递 50124 高级前端）
INSERT INTO msg_conversation
    (id, company_id, candidate_id, hr_id, application_id,
     last_message_preview, last_message_at, candidate_unread, hr_unread, created_at, updated_at)
VALUES
    (3, 1, 28, 8, 50124,
     '收到，那本周五下午15:00线下面试，地址稍后发您。', '2026-08-05 16:30:00', 1, 0, '2026-08-04 15:30:00', '2026-08-05 16:30:00');

INSERT INTO msg_conversation_member
    (id, conversation_id, user_id, member_role, unread_count, is_top, is_muted, is_deleted, last_read_at, joined_at)
VALUES
    (6, 3, 28, 'CANDIDATE', 1, 0, 0, 0, '2026-08-05 16:00:00', '2026-08-04 15:30:00'),
    (7, 3, 8,  'HR',        0, 0, 0, 0, '2026-08-05 16:30:00', '2026-08-04 15:30:00');

INSERT INTO msg_message
    (id, conversation_id, sender_id, sender_role, content_type, msg_type, content, is_read, created_at,
     media_url, file_name, file_size)
VALUES
    (28, 3, 8,  'HR',       'TEXT', 'TEXT', '您好，我是HR，看了您投递的「高级前端工程师」岗位，您的微前端经验我们很感兴趣。', 1, '2026-08-04 15:30:00', NULL, NULL, NULL),
    (29, 3, 28, 'CANDIDATE', 'TEXT', 'TEXT', '您好，我主导过20+子应用的微前端改造，可以详细聊聊。', 1, '2026-08-04 16:00:00', NULL, NULL, NULL),
    (30, 3, 8,  'HR',       'TEXT', 'TEXT', '很好，想邀请您来现场面试，本周五下午方便吗？', 1, '2026-08-05 10:00:00', NULL, NULL, NULL),
    (31, 3, 28, 'CANDIDATE', 'TEXT', 'TEXT', '方便的。', 1, '2026-08-05 10:20:00', NULL, NULL, NULL),
    (32, 3, 8,  'HR',       'TEXT', 'TEXT', '收到，那本周五下午15:00线下面试，地址稍后发您。', 0, '2026-08-05 16:30:00', NULL, NULL, NULL);

-- ============================================================
-- 14. 通知（sys_notification，候选人 + HR）
-- ============================================================
INSERT INTO sys_notification
    (user_id, type, title, content, target_type, target_id, is_read, created_at)
VALUES
    -- 候选人：简历筛选结果
    (20, 'RESUME_VIEWED', '简历筛选通过', '您的简历已通过「高级Java工程师」筛选，请留意后续面试安排。', 'application', 50100, 0, '2026-08-03 10:00:00'),
    (21, 'RESUME_VIEWED', '简历筛选通过', '您的简历已通过「前端开发工程师」筛选，请留意后续面试安排。', 'application', 50103, 0, '2026-08-02 10:00:00'),
    (21, 'RESUME_VIEWED', '简历筛选未通过', '很遗憾，「UI设计师」未能通过筛选。您的设计经验与岗位要求存在一定差距。', 'application', 50105, 0, '2026-08-03 16:00:00'),
    (23, 'RESUME_VIEWED', '简历筛选未通过', '很遗憾，「高级Java工程师」未能通过筛选。您的产品背景与本岗位技术方向不匹配。', 'application', 50111, 0, '2026-08-03 15:00:00'),
    (24, 'RESUME_VIEWED', '简历筛选通过', '您的简历已通过「测试开发工程师」筛选，请留意后续面试安排。', 'application', 50113, 0, '2026-08-04 10:00:00'),
    (27, 'RESUME_VIEWED', '简历筛选通过', '您的简历已通过「客户成功经理」筛选，请留意后续面试安排。', 'application', 50121, 0, '2026-08-04 11:00:00'),
    (29, 'RESUME_VIEWED', '简历筛选通过', '您的简历已通过「UI设计师」筛选，请留意后续面试安排。', 'application', 50127, 0, '2026-08-05 11:00:00'),
    -- 候选人：面试邀请
    (20, 'INTERVIEW', '面试邀请', '您已收到「高级Java工程师」的面试邀请，面试时间：08-06 14:00 视频面试，请准时参加。', 'application', 50100, 0, '2026-08-04 15:00:00'),
    (23, 'INTERVIEW', '面试邀请', '您已收到「产品经理」的面试邀请，面试时间：08-07 10:00 线下面试，请准时参加。', 'application', 50109, 0, '2026-08-04 16:00:00'),
    (25, 'INTERVIEW', '面试邀请', '您已收到「运维工程师」的面试邀请，面试时间：08-06 16:00 视频面试，请准时参加。', 'application', 50115, 0, '2026-08-05 11:00:00'),
    (28, 'INTERVIEW', '面试邀请', '您已收到「高级前端工程师」的面试邀请，面试时间：08-07 15:00 线下面试，请准时参加。', 'application', 50124, 0, '2026-08-05 17:00:00'),
    -- 候选人：Offer
    (22, 'OFFER', 'Offer已确认', '您已接受「高级Java工程师」Offer，预计入职日期 2026-09-01，欢迎加入！', 'offer', 60100, 0, '2026-08-05 10:00:00'),
    (26, 'OFFER', 'Offer已确认', '您已接受「数据仓库工程师」Offer，预计入职日期 2026-09-15，欢迎加入！', 'offer', 60101, 0, '2026-08-05 15:00:00'),
    (24, 'OFFER', '收到Offer', '恭喜！「测试开发工程师」Offer 已发出，请于 08-12 前确认。', 'offer', 60102, 0, '2026-08-05 14:30:00'),
    -- HR(8)：新投递
    (8, 'APPLICATION', '收到新简历', '候选人「张伟」投递了「Java开发工程师」岗位，请及时查看。', 'application', 50101, 0, '2026-08-01 10:00:00'),
    (8, 'APPLICATION', '收到新简历', '候选人「王强」投递了「高级Java工程师」岗位，请及时查看。', 'application', 50106, 0, '2026-08-02 09:00:00'),
    (8, 'APPLICATION', '收到新简历', '候选人「赵敏」投递了「数据仓库工程师」岗位，请及时查看。', 'application', 50118, 0, '2026-08-03 10:30:00'),
    (8, 'APPLICATION', '收到新简历', '候选人「周杰」投递了「高级前端工程师」岗位，请及时查看。', 'application', 50124, 0, '2026-08-04 09:30:00'),
    -- HR(8)：面试安排 / Offer / HC
    (8, 'INTERVIEW', '面试安排', '您已与「张伟」约定 08-06 14:00 视频面试，请提前做好准备。', 'application', 50100, 0, '2026-08-04 15:00:00'),
    (8, 'INTERVIEW', '面试安排', '您已与「杨光」约定 08-06 16:00 视频面试，请提前做好准备。', 'application', 50115, 0, '2026-08-05 11:00:00'),
    (8, 'OFFER', 'Offer已发送', '您已向「陈静」发送「测试开发工程师」Offer，等待候选人确认。', 'offer', 60102, 0, '2026-08-05 14:30:00'),
    (8, 'HC_WARNING', 'HC预警', '「高级Java工程师」岗位HC已满，请注意招聘进度。', 'job', 2, 0, '2026-08-05 10:00:00');

-- ============================================================
-- 清理脚本（重复执行前使用，按需取消注释）
-- ============================================================
-- DELETE FROM resume_status_log  WHERE application_id >= 50100;
-- DELETE FROM resume_application WHERE id >= 50100;
-- DELETE FROM hr_interview      WHERE application_id >= 50100;
-- DELETE FROM job_hc_reservation WHERE offer_id >= 60100;
-- DELETE FROM hr_offer          WHERE id >= 60100;
-- DELETE FROM msg_message       WHERE id >= 21;
-- DELETE FROM msg_conversation_member WHERE conversation_id >= 2;
-- DELETE FROM msg_conversation  WHERE id >= 2;
-- DELETE FROM sys_notification  WHERE id >= 11;      -- 保留原有 10 条
-- DELETE FROM resume_ability_model WHERE resume_id >= 100;
-- DELETE FROM resume            WHERE id >= 100;
-- DELETE FROM sys_user_profile  WHERE user_id >= 20;
-- DELETE FROM sys_user          WHERE id >= 20;
-- DELETE FROM job_profile       WHERE job_id >= 3;
-- DELETE FROM job_post          WHERE id >= 3;
-- UPDATE job_post SET confirmed_hc = GREATEST(confirmed_hc - 1, 0) WHERE id IN (2, 9);
-- UPDATE job_post SET reserved_hc  = GREATEST(reserved_hc - 1, 0) WHERE id = 7;
