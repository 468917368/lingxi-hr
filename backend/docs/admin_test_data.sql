-- ============================================================
-- Admin 后台模块测试数据（成员E）
-- 库：lingxi
-- 生成日期：2026-08-08
--
-- 内容概览：
--   1. admin_user               新建管理员账号 admin
--   2. admin_config             系统配置 8 条
--   3. admin_announcement       公告 6 条（PUBLISHED/DRAFT/ARCHIVED）
--   4. admin_announcement_read  公告已读记录
--   5. admin_operation_log      管理员操作日志
--   6. admin_statistics_snapshot  统计快照（近 5 天）
--
-- 管理员账号：
--   用户名：admin
--   密码哈希：$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai
--             （与 hr_test_data.sql 候选人账号同一明文密码，BCrypt $2a$）
--   首次登录：first_login=0，可直接登录，无需强制改密
--
-- 注意：
--   1. 使用显式ID，避免自增不确定性；重复执行前请先清理已插入的数据。
--   2. 若服务器 admin 表已有数据，请先核对 id 是否冲突。
--   3. 执行方式：
--      mysql --default-character-set=utf8mb4 -h<host> -P<port> -uroot -p<pass> lingxi < admin_test_data.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1. 管理员账号（admin_user）
-- ------------------------------------------------------------
INSERT INTO admin_user (id, username, password_hash, name, status, last_login_at, created_at, updated_at, first_login)
VALUES (1, 'admin', '$2a$10$wJJ0/DGSbacKau3gZbR07.Um1bbKUp09ozMw5SSPjRB/YG9AYDBai', '超级管理员', 'ACTIVE',
        '2026-08-08 10:30:00', NOW(), NOW(), 0);

-- ------------------------------------------------------------
-- 2. 系统配置（admin_config）
-- ------------------------------------------------------------
INSERT INTO admin_config (id, config_key, config_value, description, updated_at) VALUES
(1, 'platform.name',                '灵犀招聘',            '平台名称', NOW()),
(2, 'platform.icp',                 '粤ICP备20260001号',   'ICP备案号', NOW()),
(3, 'offer.expire.hours',           '72',                  'Offer有效期（小时）', NOW()),
(4, 'mock.interview.daily.quota',   '3',                   '每日模拟面试次数上限', NOW()),
(5, 'resume.analysis.enabled',      'true',                '简历AI解析开关', NOW()),
(6, 'job.auto.refresh.days',        '30',                  '岗位自动刷新间隔（天）', NOW()),
(7, 'contact.hotline',              '400-000-0000',        '客服热线', NOW()),
(8, 'maintenance.tip',              '每周日 02:00-04:00 例行维护', '平台维护提示', NOW());

-- ------------------------------------------------------------
-- 3. 平台公告（admin_announcement）
-- ------------------------------------------------------------
INSERT INTO admin_announcement (id, title, content, target_role, status, created_by, created_at, updated_at) VALUES
(1, '平台系统升级公告', '为了提供更稳定的服务，灵犀招聘平台将于 2026-08-10 00:00-02:00 进行系统升级，期间部分功能可能短暂不可用，给您带来不便敬请谅解。', 'ALL', 'PUBLISHED', 1, '2026-08-05 10:00:00', '2026-08-05 10:00:00'),
(2, '新功能上线：AI模拟面试', '灵犀招聘全新上线 AI 模拟面试功能，支持求职者提前演练面试、获取 AI 个性化点评，帮助提升面试表现。欢迎体验！', 'CANDIDATE', 'PUBLISHED', 1, '2026-08-06 09:30:00', '2026-08-06 09:30:00'),
(3, '企业认证审核流程调整', '为加快企业入驻审核效率，企业认证将支持提交更多证明材料（如营业执照、商标注册证等），审核周期缩短至 1-2 个工作日。', 'HR', 'PUBLISHED', 1, '2026-08-06 14:00:00', '2026-08-06 14:00:00'),
(4, '面试官操作规范提醒', '请各位面试官在面试结束后及时提交面试评估，评估内容需不少于 20 字，以免影响候选人面试流程推进。', 'INTERVIEWER', 'DRAFT', 1, '2026-08-07 10:00:00', '2026-08-07 10:00:00'),
(5, '春节放假通知', '平台春节期间（2026-02-14 至 2026-02-20）客服暂停服务，简历审核、Offer 确认等功能正常运行。', 'ALL', 'ARCHIVED', 1, '2026-02-10 10:00:00', '2026-02-10 10:00:00'),
(6, '平台维护通知', '平台将于本周日（2026-08-09）02:00-04:00 进行例行维护，届时登录、投递功能可能短暂不可用。', 'ALL', 'PUBLISHED', 1, '2026-08-08 09:00:00', '2026-08-08 09:00:00');

-- ------------------------------------------------------------
-- 4. 公告已读记录（admin_announcement_read）
--    仅对已发布公告（PUBLISHED）记录已读，用户取现有测试账号
-- ------------------------------------------------------------
INSERT INTO admin_announcement_read (id, announcement_id, user_id, user_type, read_at) VALUES
(1,  1, 8,  'HR',         '2026-08-05 11:00:00'),
(2,  1, 20, 'CANDIDATE',  '2026-08-05 11:30:00'),
(3,  1, 21, 'CANDIDATE',  '2026-08-05 12:00:00'),
(4,  2, 18, 'CANDIDATE',  '2026-08-06 10:00:00'),
(5,  2, 20, 'CANDIDATE',  '2026-08-06 10:30:00'),
(6,  2, 22, 'CANDIDATE',  '2026-08-06 11:00:00'),
(7,  3, 8,  'HR',         '2026-08-06 15:00:00'),
(8,  3, 12, 'HR',         '2026-08-06 15:30:00'),
(9,  6, 15, 'INTERVIEWER','2026-08-08 09:30:00'),
(10, 6, 8,  'HR',         '2026-08-08 09:40:00');

-- ------------------------------------------------------------
-- 5. 管理员操作日志（admin_operation_log）
--    登录日志由 sys_login_log 联表展示，此处只放管理操作
-- ------------------------------------------------------------
INSERT INTO admin_operation_log (id, user_id, op_type, detail, ip_address, created_at) VALUES
(1, 1, 'REVIEW',   '审核企业认证：test1（企业服务/SaaS），审核通过', '127.0.0.1', '2026-08-03 12:00:00'),
(2, 1, 'REVIEW',   '审核企业认证：test2（互联网/电商），审核通过', '127.0.0.1', '2026-08-03 18:30:00'),
(3, 1, 'ANNOUNCE', '发布公告：平台系统升级公告', '127.0.0.1', '2026-08-05 10:00:00'),
(4, 1, 'CONFIG',   '修改系统配置：offer.expire.hours=72, mock.interview.daily.quota=3', '127.0.0.1', '2026-08-05 11:00:00'),
(5, 1, 'PUBLISH',  '发布公告：新功能上线：AI模拟面试', '127.0.0.1', '2026-08-06 09:30:00'),
(6, 1, 'DISABLE',  '禁用用户：hr（违规账号）', '127.0.0.1', '2026-08-06 16:00:00'),
(7, 1, 'ENABLE',   '启用用户：hr（申诉通过）', '127.0.0.1', '2026-08-07 09:00:00'),
(8, 1, 'CREATE',   '创建公告：面试官操作规范提醒（草稿）', '127.0.0.1', '2026-08-07 10:00:00'),
(9, 1, 'UPDATE',   '更新公告：企业认证审核流程调整', '127.0.0.1', '2026-08-07 14:00:00'),
(10, 1, 'REVIEW',  '审核企业认证：test1（企业服务/SaaS），驳回：材料不全', '127.0.0.1', '2026-08-08 10:00:00');

-- ------------------------------------------------------------
-- 6. 统计快照（admin_statistics_snapshot）
--    数据与 lingxi 库现有实际规模一致（用户16/企业2/岗位10/投递32/面试9/Offer6）
-- ------------------------------------------------------------
INSERT INTO admin_statistics_snapshot (id, snapshot_date, user_count, enterprise_count, position_count, application_count, interview_count, offer_count, created_at) VALUES
(1, '2026-08-04', 13, 2, 10, 22, 4, 2, '2026-08-05 00:10:00'),
(2, '2026-08-05', 14, 2, 10, 26, 6, 5, '2026-08-06 00:10:00'),
(3, '2026-08-06', 15, 2, 10, 28, 7, 5, '2026-08-07 00:10:00'),
(4, '2026-08-07', 16, 2, 10, 31, 8, 6, '2026-08-08 00:10:00'),
(5, '2026-08-08', 16, 2, 10, 32, 9, 6, '2026-08-08 12:00:00');

-- ============================================================
-- 清理脚本（重复执行前先跑）
-- ============================================================
-- DELETE FROM admin_operation_log WHERE user_id = 1;
-- DELETE FROM admin_announcement_read;
-- DELETE FROM admin_announcement WHERE id BETWEEN 1 AND 6;
-- DELETE FROM admin_config WHERE id BETWEEN 1 AND 8;
-- DELETE FROM admin_user WHERE username = 'admin';
-- DELETE FROM admin_statistics_snapshot WHERE snapshot_date BETWEEN '2026-08-04' AND '2026-08-08';
