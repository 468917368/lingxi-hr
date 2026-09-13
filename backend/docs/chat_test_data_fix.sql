-- ============================================================
-- 修复：测试数据中文内容被粘贴时丢失
-- 按业务键定位会话/消息/通知，更新为正确中文内容
-- 执行：mysql -h<host> -uroot -p --default-character-set=utf8mb4 lingxi < docs/chat_test_data_fix.sql
-- 注意：不要粘贴执行，必须用 < 重定向跑文件，否则中文又会丢
-- ============================================================

-- 定位会话
SET @conv_id = (SELECT id FROM msg_conversation
                WHERE company_id = 1 AND candidate_id = 18 AND hr_id = 8
                ORDER BY id DESC LIMIT 1);

-- 1. 修复会话最后消息摘要
UPDATE msg_conversation
SET last_message_preview = '您好，面试已安排在明天（08-06）下午14:00，地点在深圳市南山区科技园XX大厦12层，请提前10分钟到达'
WHERE id = @conv_id;

-- 2. 修复消息内容（按 created_at + sender 定位）
UPDATE msg_message SET content = '您好，我是灵犀招聘的HR，看到您投递了我们「高级Java工程师」岗位，简历已通过初筛，想和您聊一下后续安排。'
WHERE conversation_id = @conv_id AND sender_id = 8  AND created_at = '2026-08-05 11:35:00';

UPDATE msg_message SET content = '您好，感谢您的认可，我很期待这个机会。'
WHERE conversation_id = @conv_id AND sender_id = 18 AND created_at = '2026-08-05 11:38:00';

UPDATE msg_message SET content = '方便简单介绍下您最近的项目经历吗？重点说一下您负责的模块和技术栈。'
WHERE conversation_id = @conv_id AND sender_id = 8  AND created_at = '2026-08-05 11:40:00';

UPDATE msg_message SET content = '我最近在做电商平台的订单中心，主要负责订单状态机、库存扣减和基于RocketMQ的异步消息处理，技术栈是Spring Boot + MySQL + Redis。'
WHERE conversation_id = @conv_id AND sender_id = 18 AND created_at = '2026-08-05 11:46:00';

UPDATE msg_message SET content = '很匹配我们的技术栈。想邀请您参加一轮技术面试，这周方便吗？'
WHERE conversation_id = @conv_id AND sender_id = 8  AND created_at = '2026-08-05 11:50:00';

UPDATE msg_message SET content = '方便，周三或周四下午都可以。'
WHERE conversation_id = @conv_id AND sender_id = 18 AND created_at = '2026-08-05 11:52:00';

UPDATE msg_message SET content = '好的，我确认好具体时间后通知您。'
WHERE conversation_id = @conv_id AND sender_id = 8  AND created_at = '2026-08-05 11:55:00';

UPDATE msg_message SET content = '您好，面试已安排在明天（08-06）下午14:00，地点在深圳市南山区科技园XX大厦12层，请提前10分钟到达。'
WHERE conversation_id = @conv_id AND sender_id = 8  AND created_at = '2026-08-05 14:30:00';

-- 3. 修复通知（按 user_id + type 定位）
-- 候选人 18（C端）
UPDATE sys_notification SET title = '面试邀请', content = '您已收到「高级Java工程师」的面试邀请，面试时间：明天下午14:00，请准时参加。'
WHERE user_id = 18 AND type = 'INTERVIEW_INVITE' AND created_at = '2026-08-05 14:31:00';

UPDATE sys_notification SET title = '岗位推荐', content = '为您推荐「高级Java工程师」岗位，薪资20-30K·14薪，与您的经历高度匹配。'
WHERE user_id = 18 AND type = 'JOB_RECOMMEND' AND created_at = '2026-08-05 15:00:00';

UPDATE sys_notification SET title = '系统通知', content = '欢迎使用灵犀招聘，完善简历信息可获得更多推荐机会。'
WHERE user_id = 18 AND type = 'SYSTEM' AND created_at = '2026-08-05 15:30:00';

-- HR 8（B端）
UPDATE sys_notification SET title = '收到新简历', content = '候选人「测试候选人」投递了「高级Java工程师」岗位，请及时查看。'
WHERE user_id = 8 AND type = 'NEW_APPLICATION' AND created_at = '2026-08-05 11:24:00';

UPDATE sys_notification SET title = '面试安排', content = '您已与「测试候选人」约定明天下午14:00进行面试，请提前做好准备。'
WHERE user_id = 8 AND type = 'INTERVIEW_SCHEDULE' AND created_at = '2026-08-05 14:31:00';

UPDATE sys_notification SET title = 'HC预警', content = '「高级Java工程师」岗位HC仅剩1个，请注意招聘进度。'
WHERE user_id = 8 AND type = 'HC_WARNING' AND created_at = '2026-08-05 15:00:00';
