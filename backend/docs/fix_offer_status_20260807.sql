-- ============================================================
-- 投递/Offer 状态对齐最新状态机（2026-08-07）
-- 背景：Offer 模块上线后，投递状态机新增 OFFER_ACCEPTED/OFFER_DECLINED；
--       旧测试数据中 Offer ACCEPTED/REJECTED 对应的投递仍为 OFFERED/OFFERABLE，需对齐。
-- 规范映射：Offer SENT→投递 OFFERED；ACCEPTED→OFFER_ACCEPTED；REJECTED→OFFER_DECLINED；WITHDRAWN/EXPIRED→OFFERABLE(回退)
-- 适用：本地 + 服务器(121.40.45.122) 均可用（条件 WHERE 幂等，重复执行无副作用）
-- 用法：本地 mysql ... < fix_offer_status_20260807.sql；服务器 docker exec -i mysql mysql -uroot -p... lingxi < fix_offer_status_20260807.sql
-- ============================================================

START TRANSACTION;

-- ① 投递状态对齐：Offer ACCEPTED → 投递 OFFER_ACCEPTED（已录用终态）
UPDATE resume_application
SET status = 'OFFER_ACCEPTED', updated_at = NOW()
WHERE id IN (50106, 50118)
  AND status = 'OFFERED';        -- 仅当仍是 OFFERED 才改，避免覆盖已同步状态

-- ② 投递状态对齐：Offer REJECTED（候选人拒绝） → 投递 OFFER_DECLINED（已拒绝终态）
UPDATE resume_application
SET status = 'OFFER_DECLINED', updated_at = NOW()
WHERE id = 50113
  AND status = 'OFFERABLE';      -- 若服务器上 60102 仍为 SENT(投递 OFFERED) 则本行不生效，符合规范无需改

-- ③ 补齐状态流转日志（幂等键对齐 C 侧 app:{id}:{from}:{to}:{operatorId}，uk_idempotent 防重）
INSERT IGNORE INTO resume_status_log
    (application_id, from_status, to_status, operator_id, operator_role, reason, idempotent_key, created_at)
VALUES
(50106, 'OFFERED',      'OFFER_ACCEPTED', 22, 'CANDIDATE', '候选人接受Offer', 'app:50106:OFFERED:OFFER_ACCEPTED:22',     '2026-08-05 10:00:00'),
(50118, 'OFFERED',      'OFFER_ACCEPTED', 26, 'CANDIDATE', '候选人接受Offer', 'app:50118:OFFERED:OFFER_ACCEPTED:26',     '2026-08-05 15:00:00'),
(50113, 'OFFERABLE',    'OFFER_DECLINED', 24, 'CANDIDATE', '候选人拒绝Offer', 'app:50113:OFFERABLE:OFFER_DECLINED:24',   '2026-08-07 14:44:52');

COMMIT;

-- ============================================================
-- 校验（修正后应满足）：
--   offer ACCEPTED   → 投递 OFFER_ACCEPTED
--   offer REJECTED   → 投递 OFFER_DECLINED
--   offer WITHDRAWN  → 投递 OFFERABLE
--   offer SENT       → 投递 OFFERED
-- ============================================================
SELECT o.id AS offer_id, o.status AS offer_status, a.id AS app_id, a.status AS app_status
FROM hr_offer o
LEFT JOIN resume_application a ON a.id = o.application_id
ORDER BY o.id;
