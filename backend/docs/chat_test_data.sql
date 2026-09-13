-- ============================================================
-- 聊天模块测试数据（成员A）
-- 会话：HR(16735263528, id=8) ↔ 候选人(13800000001, id=18)
-- 库：lingxi，表结构以本地库为准（2026-08-05）
--
-- 依赖的已有数据：
--   公司 hr_company.id=1 (test1)
--   岗位 job_post.id=2 (高级Java工程师, company_id=1)
--   投递 resume_application.id=50003 (candidate_id=18, job_id=2, SCREENED)
--
-- 注意：
--   1. 会话表有唯一键 uk_company_candidate_hr(company_id,candidate_id,hr_id)，
--      重复执行会因唯一键冲突失败。
--   2. 会话成员表有唯一键 uk_conversation_user(conversation_id,user_id)，
--      重复执行同样会冲突。
-- ============================================================

-- 1. 插入会话（HR 8 ↔ 候选人 18）
INSERT INTO msg_conversation
    (company_id, candidate_id, hr_id, application_id,
     last_message_preview, last_message_at,
     candidate_unread, hr_unread,
     created_at, updated_at)
VALUES
    (1, 18, 8, 50003,
     '您好，面试已安排在明天（08-06）下午14:00，地点在深圳市南山区科技园XX大厦12层，请提前10分钟到达',
     '2026-08-05 14:30:00',
     1, 0,
     '2026-08-05 11:35:00', '2026-08-05 14:30:00');

-- 记录新会话ID，后续消息引用
SET @conv_id = LAST_INSERT_ID();

-- 2. 插入会话成员（msg_conversation_member，发送消息/标记已读/推送都依赖此表）
--    候选人 18 有1条未读（最后一条HR消息），HR 8 已读全部
INSERT INTO msg_conversation_member
    (conversation_id, user_id, member_role, unread_count, is_top, is_muted, is_deleted, last_read_at, joined_at)
VALUES
    (@conv_id, 18, 'CANDIDATE', 1, 0, 0, 0, '2026-08-05 11:52:00', '2026-08-05 11:35:00'),
    (@conv_id, 8,  'HR',        0, 0, 0, 0, '2026-08-05 14:30:00', '2026-08-05 11:35:00');

-- 3. 插入会话内的消息（最后一条HR消息未读 → 候选人未读数=1）
INSERT INTO msg_message
    (conversation_id, sender_id, sender_role, content_type, msg_type, content, is_read, created_at)
VALUES
    (@conv_id, 8,  'HR',       'TEXT', 'TEXT', '您好，我是灵犀招聘的HR，看到您投递了我们「高级Java工程师」岗位，简历已通过初筛，想和您聊一下后续安排。', 1, '2026-08-05 11:35:00'),
    (@conv_id, 18, 'CANDIDATE', 'TEXT', 'TEXT', '您好，感谢您的认可，我很期待这个机会。',                                                                  1, '2026-08-05 11:38:00'),
    (@conv_id, 8,  'HR',       'TEXT', 'TEXT', '方便简单介绍下您最近的项目经历吗？重点说一下您负责的模块和技术栈。',                                              1, '2026-08-05 11:40:00'),
    (@conv_id, 18, 'CANDIDATE', 'TEXT', 'TEXT', '我最近在做电商平台的订单中心，主要负责订单状态机、库存扣减和基于RocketMQ的异步消息处理，技术栈是Spring Boot + MySQL + Redis。', 1, '2026-08-05 11:46:00'),
    (@conv_id, 8,  'HR',       'TEXT', 'TEXT', '很匹配我们的技术栈。想邀请您参加一轮技术面试，这周方便吗？',                                              1, '2026-08-05 11:50:00'),
    (@conv_id, 18, 'CANDIDATE', 'TEXT', 'TEXT', '方便，周三或周四下午都可以。',                                                                          1, '2026-08-05 11:52:00'),
    (@conv_id, 8,  'HR',       'TEXT', 'TEXT', '好的，我确认好具体时间后通知您。',                                                                        1, '2026-08-05 11:55:00'),
    (@conv_id, 8,  'HR',       'TEXT', 'TEXT', '您好，面试已安排在明天（08-06）下午14:00，地点在深圳市南山区科技园XX大厦12层，请提前10分钟到达。',        0, '2026-08-05 14:30:00');

-- 4. 通知测试数据（聊天模块走 sys_notification 表）
-- 候选人 18（C端）
INSERT INTO sys_notification (user_id, type, title, content, target_type, target_id, is_read, created_at) VALUES
    (18, 'INTERVIEW_INVITE',   '面试邀请', '您已收到「高级Java工程师」的面试邀请，面试时间：明天下午14:00，请准时参加。', 'application', 50003, 0, '2026-08-05 14:31:00'),
    (18, 'JOB_RECOMMEND',      '岗位推荐', '为您推荐「高级Java工程师」岗位，薪资20-30K·14薪，与您的经历高度匹配。',       'job',         2,     0, '2026-08-05 15:00:00'),
    (18, 'SYSTEM',             '系统通知', '欢迎使用灵犀招聘，完善简历信息可获得更多推荐机会。',                         NULL,          NULL,  0, '2026-08-05 15:30:00');

-- HR 8（B端）
INSERT INTO sys_notification (user_id, type, title, content, target_type, target_id, is_read, created_at) VALUES
    (8, 'NEW_APPLICATION',    '收到新简历', '候选人「测试候选人」投递了「高级Java工程师」岗位，请及时查看。',             'application', 50003, 1, '2026-08-05 11:24:00'),
    (8, 'INTERVIEW_SCHEDULE', '面试安排', '您已与「测试候选人」约定明天下午14:00进行面试，请提前做好准备。',             'application', 50003, 0, '2026-08-05 14:31:00'),
    (8, 'HC_WARNING',         'HC预警', '「高级Java工程师」岗位HC仅剩1个，请注意招聘进度。',                           'job',         2,     0, '2026-08-05 15:00:00');
