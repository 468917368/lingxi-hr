-- ============================================================
-- 灵犀互聘 — 全局数据库表结构
-- 版本: V2.1
-- 日期: 2026-07-29
-- 单库架构，7个模块，31张表
-- ============================================================

CREATE DATABASE IF NOT EXISTS `lingxi`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `lingxi`;

-- ============================================================
-- 模块1: sys_ 用户模块（成员A，4表）
-- ============================================================

-- -----------------------------------------------------------
-- 1. 用户表
-- 所有角色（求职者/HR/面试官/管理员）统一存储
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone`               VARCHAR(20)     NOT NULL COMMENT '手机号（登录账号）',
    `password_hash`       VARCHAR(128)    COMMENT '密码哈希（管理员账号使用；HR/求职者/面试官可为NULL，走验证码登录）',
    `name`                VARCHAR(32)     NOT NULL COMMENT '真实姓名',
    `avatar`              VARCHAR(512)    COMMENT '头像URL',
    `email`               VARCHAR(64)     COMMENT '邮箱',
    `role`                VARCHAR(16)     NOT NULL COMMENT '角色：CANDIDATE=求职者 HR=HR INTERVIEWER=面试官 ADMIN=管理员',
    `status`              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE=正常 DISABLED=已禁用',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 用户画像表（求职者专用信息）
-- 仅 role=CANDIDATE 时有数据，与 sys_user 表一对一
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_profile` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `city`                VARCHAR(32)     COMMENT '所在城市',
    `job_status`          VARCHAR(20)     COMMENT '求职状态：JOB_SEEKING=求职中 EMPLOYED_LOOKING=在职看机会 NOT_LOOKING=暂不考虑',
    `desired_job`         VARCHAR(64)     COMMENT '期望岗位',
    `desired_city`        VARCHAR(64)     COMMENT '期望城市（逗号分隔多个）',
    `desired_salary_min`  INT             COMMENT '期望最低月薪（元）',
    `desired_salary_max`  INT             COMMENT '期望最高月薪（元）',
    `available_from`      DATE            COMMENT '到岗时间',
    `resume_public`       TINYINT         NOT NULL DEFAULT 1 COMMENT '简历公开：0=关闭 1=开启（允许企业搜索）',
    `blind_mode`          TINYINT         NOT NULL DEFAULT 0 COMMENT '盲选模式：0=关闭 1=开启（隐藏姓名/照片，仅展示技能画像）',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_city` (`city`),
    KEY `idx_job_status` (`job_status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='用户画像表（求职者）';

-- -----------------------------------------------------------
-- 3. Agent对话历史表
-- 存储Job Agent与用户的对话记录，支持上下文关联
-- 按用户+会话隔离，敏感数据本地存储不经过百宝箱
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_agent_conversation` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`            VARCHAR(64)     NOT NULL COMMENT '会话ID（UUID格式）',
    `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '用户ID（关联 sys_user 表）',
    `role`                  VARCHAR(16)     NOT NULL COMMENT '消息角色：user=用户 assistant=助手 system=系统',
    `content`               TEXT            NOT NULL COMMENT '消息内容',
    `tool_calls`            JSON            COMMENT '工具调用记录：[{name,args,result}]',
    `tokens_used`           INT UNSIGNED    COMMENT '本次消耗的Token数',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`, `created_at`),
    KEY `idx_user_id` (`user_id`, `created_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='Agent对话历史表';

-- -----------------------------------------------------------
-- 4. Agent审计日志表
-- 记录Agent工具调用的审计信息，用于监控和问题排查
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_agent_audit_log` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `session_id`            VARCHAR(64)     NOT NULL COMMENT '会话ID',
    `action`                VARCHAR(30)     NOT NULL COMMENT '操作类型：CHAT=对话 TOOL_CALL=工具调用 ERROR=错误',
    `tool_name`             VARCHAR(50)     COMMENT '工具名称（tool_call时记录）',
    `input_text`            VARCHAR(2000)   COMMENT '用户输入（脱敏后）',
    `result`                VARCHAR(16)     NOT NULL COMMENT '结果：SUCCESS/FAILED',
    `error_msg`             VARCHAR(500)    COMMENT '错误信息',
    `duration_ms`           INT UNSIGNED    COMMENT '耗时（毫秒）',
    `ip_address`            VARCHAR(45)     COMMENT '客户端IP',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `created_at`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='Agent审计日志表';


-- ============================================================
-- 模块2: msg_ 消息模块（公共，3表）
-- C端求职者、B端HR、A端管理员共用
-- ============================================================

-- -----------------------------------------------------------
-- 5. 会话表
-- HR 与求职者的聊天会话
-- 同一候选人+同一企业+同一HR 唯一
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `msg_conversation` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `company_id`            BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `candidate_id`          BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `hr_id`                 BIGINT UNSIGNED NOT NULL COMMENT 'HR用户ID',
    `application_id`        BIGINT UNSIGNED COMMENT '关联投递记录ID（咨询HR入口创建时可能为空）',
    `last_message_preview`  VARCHAR(100)    COMMENT '最后一条消息摘要（前50字）',
    `last_message_at`       DATETIME        COMMENT '最后消息时间',
    `candidate_unread`      INT             NOT NULL DEFAULT 0 COMMENT '求职者未读数',
    `hr_unread`             INT             NOT NULL DEFAULT 0 COMMENT 'HR未读数',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_company_candidate_hr` (`company_id`, `candidate_id`, `hr_id`),
    KEY `idx_company_hr` (`company_id`, `hr_id`, `last_message_at`),
    KEY `idx_candidate_id` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='会话表（公共）';

-- -----------------------------------------------------------
-- 6. 消息表
-- 会话内的单条消息，支持文本和卡片类型
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `msg_message` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id`     BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    `sender_id`           BIGINT UNSIGNED NOT NULL COMMENT '发送者用户ID',
    `sender_role`         VARCHAR(16)     NOT NULL COMMENT '发送者角色：CANDIDATE=求职者 HR=HR',
    `content_type`        VARCHAR(32)     NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT=文字 CARD_INTERVIEW=面试邀约卡片 CARD_RESUME=简历卡片',
    `content`             TEXT            NOT NULL COMMENT '消息内容（TEXT=文字，卡片类型=JSON）',
    `is_read`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_time` (`conversation_id`, `created_at`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_conversation_unread` (`conversation_id`, `sender_role`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='消息表（公共）';

-- -----------------------------------------------------------
-- 7. 通知表
-- 用户级别的消息通知，B端和C端共用
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `msg_notification` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
    `type`                VARCHAR(32)     NOT NULL COMMENT '通知类型：APPLICATION=投递通知 INTERVIEW=面试提醒 OFFER=Offer通知 SYSTEM=系统通知',
    `title`               VARCHAR(128)    NOT NULL COMMENT '通知标题',
    `content`             VARCHAR(500)    NOT NULL COMMENT '通知内容',
    `target_type`         VARCHAR(32)     COMMENT '关联目标类型：application/interview/offer/job',
    `target_id`           BIGINT UNSIGNED COMMENT '关联目标ID',
    `is_read`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_user_created` (`user_id`, `created_at` DESC),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='通知表（公共）';


-- ============================================================
-- 模块3: job_ 岗位模块（成员B，6表）
-- ============================================================

-- -----------------------------------------------------------
-- 8. 岗位当前信息表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_post` (
    `id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `company_id`               BIGINT UNSIGNED NOT NULL COMMENT '企业ID（关联 hr_company）',
    `title`                    VARCHAR(100)    NOT NULL COMMENT '岗位名称',
    `city_code`                VARCHAR(32)     NOT NULL COMMENT '城市编码',
    `city_name`                VARCHAR(64)     NOT NULL COMMENT '城市展示名称',
    `min_experience_years`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最低工作年限',
    `education_requirement`    VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT '学历要求编码',
    `salary_min_amount`        BIGINT UNSIGNED COMMENT '最低薪资，最小货币单位；CNY为分',
    `salary_max_amount`        BIGINT UNSIGNED COMMENT '最高薪资，最小货币单位；CNY为分',
    `salary_currency`          VARCHAR(8)      NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `salary_period`            VARCHAR(16)     COMMENT '薪资周期：HOUR/DAY/MONTH/YEAR',
    `salary_months`            TINYINT UNSIGNED COMMENT '年薪月数，如13薪',
    `is_salary_negotiable`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否面议：0=否 1=是',
    `salary_raw_text`          VARCHAR(128)    COMMENT 'JD中的原始薪资文本',
    `total_hc`                 SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '岗位总HC',
    `reserved_hc`              SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '待确认Offer预冻结HC',
    `confirmed_hc`             SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已接受Offer正式占用HC',
    `jd_text`                  MEDIUMTEXT      COMMENT 'JD原文',
    `jd_summary`               VARCHAR(1000)   COMMENT '经安全清洗的JD摘要，用于关键词检索',
    `status`                   VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '岗位状态：DRAFT=草稿 PUBLISHED=发布中 PAUSED=已暂停 CLOSED=已关闭',
    `pause_reason`             VARCHAR(32)     COMMENT '暂停原因：HC_RESERVED_FULL=HC预占满',
    `close_reason`             VARCHAR(32)     COMMENT '关闭原因：MANUAL=手动下线 VIOLATION=违规 EXPIRED=过期 HC_CONFIRMED_FULL=正式HC已满',
    `published_at`             DATETIME        COMMENT '首次发布时间',
    `closed_at`                DATETIME        COMMENT '关闭时间',
    `expires_at`               DATETIME        COMMENT '岗位到期时间（P1，HR可选设置）',
    `version`                  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '岗位乐观锁版本号',
    `created_by`               BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
    `updated_by`               BIGINT UNSIGNED COMMENT '最后更新人用户ID',
    `created_at`               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`               DATETIME        COMMENT '逻辑删除时间',
    PRIMARY KEY (`id`),
    KEY `idx_job_hr_list` (`company_id`, `deleted_at`, `status`, `updated_at`, `id`),
    KEY `idx_job_public_list` (`status`, `deleted_at`, `city_code`, `published_at`, `id`),
    KEY `idx_job_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='岗位表';

-- -----------------------------------------------------------
-- 9. 岗位画像表
-- 与 job_post 一对一
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_profile` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '岗位画像ID',
    `company_id`            BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `job_id`                BIGINT UNSIGNED NOT NULL COMMENT '岗位ID（关联 job_post）',
    `job_type`              VARCHAR(64)     COMMENT '标准化岗位类型，如JAVA_BACKEND/FRONTEND/PRODUCT（JD解析建议+HR确认）',
    `core_skills`           JSON            COMMENT '核心技能：[{name,level,required,basis,confidence}]',
    `soft_skills`           JSON            COMMENT '软能力：[{name,importance,inferred,confidence}]',
    `industry_experience`   TEXT            COMMENT '行业或业务经验要求',
    `hidden_requirements`   JSON            COMMENT '隐性要求：[{requirement,basis,inferred,confidence,hr_confirmed}]',
    `interview_focus`       JSON            COMMENT '面试考察重点',
    `profile_source`        VARCHAR(16)     NOT NULL DEFAULT 'MANUAL' COMMENT '画像来源：AI=AI生成 MANUAL=手动填写 MIXED=混合',
    `version`               INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '岗位画像版本',
    `confirmed_by`          BIGINT UNSIGNED COMMENT '最后确认画像的HR用户ID',
    `confirmed_at`          DATETIME        COMMENT '最后确认时间',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_profile` (`company_id`, `job_id`),
    KEY `idx_job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='岗位画像表';

-- -----------------------------------------------------------
-- 10. HC预冻结流水表
-- 每个Offer对应一条HC流水
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_hc_reservation` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'HC流水ID',
    `company_id`            BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `job_id`                BIGINT UNSIGNED NOT NULL COMMENT '岗位ID（关联 job_post）',
    `offer_id`              BIGINT UNSIGNED NOT NULL COMMENT 'Offer业务ID（关联 hr_offer）',
    `candidate_id`          BIGINT UNSIGNED NOT NULL COMMENT '候选人用户ID',
    `status`                VARCHAR(20)     NOT NULL DEFAULT 'RESERVED' COMMENT '流水状态：RESERVED=预冻结 CONFIRMED=已确认占用 RELEASED=已释放',
    `reserved_at`           DATETIME        NOT NULL COMMENT '预冻结时间',
    `confirmed_at`          DATETIME        COMMENT '确认占用时间',
    `released_at`           DATETIME        COMMENT '释放时间',
    `release_reason`        VARCHAR(32)     COMMENT '释放原因编码',
    `version`               INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hc_offer` (`company_id`, `offer_id`),
    KEY `idx_hc_job_status` (`company_id`, `job_id`, `status`),
    KEY `idx_hc_candidate` (`company_id`, `candidate_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='HC预冻结流水表';

-- -----------------------------------------------------------
-- 11. 企业私有题库表
-- 企业私有题库，仅供成员B的Interview Agent在lingxi-job内部检索
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_question` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '企业题目ID',
    `company_id`            BIGINT UNSIGNED NOT NULL COMMENT '企业ID（题库隔离）',
    `job_type`              VARCHAR(64)     NOT NULL COMMENT '通用岗位类型',
    `skill_tags`            JSON            COMMENT '标准化技能标签数组',
    `question_type`         VARCHAR(24)     NOT NULL COMMENT '题目类型：BASIC=基础验证 PROJECT=项目深挖 BOUNDARY=能力边界 COMPREHENSIVE=综合素养',
    `difficulty`            VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM' COMMENT '难度：EASY=基础 MEDIUM=中等 HARD=困难',
    `content`               VARCHAR(2000)   NOT NULL COMMENT '题目内容',
    `content_sha256`        CHAR(64)        NOT NULL COMMENT '标准化题干SHA-256（企业内精确去重）',
    `key_points`            VARCHAR(1000)   COMMENT '考察要点',
    `reference_answer`      VARCHAR(4000)   COMMENT '参考答案',
    `evaluation_points`     JSON            COMMENT '结构化评分要点',
    `source`                VARCHAR(20)     NOT NULL DEFAULT 'DEMO_SEED' COMMENT '来源：DEMO_SEED HR_CREATED AI_GENERATED',
    `status`                VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：PENDING_REVIEW=待审核 ACTIVE=已启用 INACTIVE=已停用',
    `version`               INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_by`            BIGINT UNSIGNED COMMENT '创建人用户ID',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`            DATETIME        COMMENT '逻辑删除时间',
    `reviewed_by`           BIGINT UNSIGNED  COMMENT '审核人用户ID（P1）',
    `reviewed_at`           DATETIME         COMMENT '审核时间（P1）',
    `review_reason`         VARCHAR(255)     COMMENT '审核意见/拒绝原因（P1）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_content` (`company_id`, `content_sha256`),
    KEY `idx_question_search` (`company_id`, `status`, `job_type`, `question_type`, `difficulty`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='企业私有题库表';

-- -----------------------------------------------------------
-- 12. 岗位收藏表
-- 求职者收藏岗位，uk_candidate_job 防重复收藏
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_favorite` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `candidate_id`    BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `job_id`          BIGINT UNSIGNED NOT NULL COMMENT '岗位ID',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_candidate_job` (`candidate_id`, `job_id`),
    KEY `idx_candidate` (`candidate_id`, `created_at`),
    KEY `idx_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='岗位收藏表';

-- -----------------------------------------------------------
-- 13. 岗位状态变更日志表（P1）
-- 每次状态变更写入一条，与 job_post 状态更新同一事务
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_status_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `company_id`      BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `job_id`          BIGINT UNSIGNED NOT NULL COMMENT '岗位ID',
    `from_status`     VARCHAR(20)     COMMENT '原状态（首次创建时为NULL）',
    `to_status`       VARCHAR(20)     NOT NULL COMMENT '目标状态',
    `reason`          VARCHAR(32)     COMMENT '变更原因编码',
    `reason_detail`   VARCHAR(500)    COMMENT '补充说明',
    `operator_id`     BIGINT UNSIGNED NOT NULL COMMENT '操作人（0=SYSTEM）',
    `operator_role`   VARCHAR(16)     NOT NULL COMMENT 'HR / SYSTEM / ADMIN',
    `request_id`      VARCHAR(64)     COMMENT '请求追踪ID',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_job_id` (`company_id`, `job_id`, `created_at`),
    KEY `idx_operator` (`operator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位状态变更日志表';


-- ============================================================
-- 模块4: resume_ 简历模块（成员C，5表）
-- ============================================================

-- -----------------------------------------------------------
-- 14. 简历表
-- 一个求职者最多维护 5 份简历（统计时不包含已删除的）
-- card_structure 存储前端卡片渲染的结构化指令
-- resume_md_url 指向 MinIO 中 Agent 解析的 Markdown 文件
-- 逻辑删除：用户删除后标记 deleted_at，不物理删除，避免已投递记录关联断裂
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '简历ID',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `file_name`           VARCHAR(128)    NOT NULL COMMENT '原始文件名',
    `file_url`            VARCHAR(512)    NOT NULL COMMENT '原始文件存储URL（MinIO/OSS）',
    `file_format`         VARCHAR(8)      NOT NULL COMMENT '文件格式：pdf/docx/doc/jpg/png',
    `file_size`           INT UNSIGNED    COMMENT '文件大小（字节）',
    `is_default`          TINYINT         NOT NULL DEFAULT 0 COMMENT '是否默认简历：0=否 1=是',
    `parse_status`        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING=待解析 PARSING=解析中 COMPLETED=已完成 FAILED=失败',
    `resume_md_url`       VARCHAR(512)    COMMENT 'Agent解析产出的MD文件MinIO路径（/resumes/{resumeId}/resume.md）',
    `card_structure`      JSON            COMMENT '前端卡片渲染指令：cards[{id,title,points[{id,text}]}]，confidenceLOW时保留raw_text',
    `deleted_at`          DATETIME        COMMENT '逻辑删除时间（NULL=未删除，FAILED解析自动标记删除）',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_candidate_id` (`candidate_id`),
    KEY `idx_parse_status` (`parse_status`),
    KEY `idx_is_default` (`candidate_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='简历表';

-- -----------------------------------------------------------
-- 15. 能力模型表（5 通用维度雷达图数据）
-- 每份简历一条记录，维度子项由 AI 动态生成存入 sub_dimensions JSON
-- 不包含岗位匹配度（匹配度存 resume_diagnosis_report 表）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_ability_model` (
    `id`                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `candidate_id`                 BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `resume_id`                    BIGINT UNSIGNED NOT NULL COMMENT '关联简历ID',
    `professional_skill_score`     TINYINT NOT NULL DEFAULT 0 COMMENT '专业技能(0-100)',
    `work_experience_score`        TINYINT NOT NULL DEFAULT 0 COMMENT '项目与工作经验(0-100)',
    `industry_knowledge_score`     TINYINT NOT NULL DEFAULT 0 COMMENT '行业认知(0-100)',
    `comprehensive_quality_score`  TINYINT NOT NULL DEFAULT 0 COMMENT '综合素质(0-100)',
    `learning_growth_score`        TINYINT NOT NULL DEFAULT 0 COMMENT '学习成长(0-100)',
    `sub_dimensions`               JSON    COMMENT 'AI动态生成的各维度子项分析依据',
    `created_at`                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_id` (`resume_id`),
    KEY `idx_candidate_id` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='能力模型表（5通用维度雷达图）';

-- -----------------------------------------------------------
-- 16. 投递记录表
-- 状态机: SUBMITTED→VIEWED→SCREENED→INTERVIEWING→OFFERABLE→OFFERED
-- 终态: REJECTED / WITHDRAWN / OFFERED
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_application` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投递记录ID',
    `job_id`              BIGINT UNSIGNED NOT NULL COMMENT '岗位ID',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `resume_id`           BIGINT UNSIGNED COMMENT '使用的简历ID',
    `status`              VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED' COMMENT '投递状态',
    `match_score`         DECIMAL(5,2)    COMMENT '投递匹配度(0-100)，A的Job Agent在投递时计算并快照',
    `reject_feedback`     JSON            COMMENT 'AI落选反馈（仅REJECTED）：{"reason":"原因","suggestions":["建议"]}',
    `submitted_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
    `viewed_at`           DATETIME        COMMENT 'HR首次查看时间',
    `screened_at`         DATETIME        COMMENT '筛选通过时间',
    `interviewing_at`     DATETIME        COMMENT '进入面试时间',
    `offerable_at`        DATETIME        COMMENT '可录用时间',
    `offered_at`          DATETIME        COMMENT '已录用时间',
    `rejected_at`         DATETIME        COMMENT '淘汰时间',
    `withdrawn_at`        DATETIME        COMMENT '撤回时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_candidate` (`job_id`, `candidate_id`),
    KEY `idx_candidate_id` (`candidate_id`),
    KEY `idx_job_id_status` (`job_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_submitted_at` (`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='投递记录表';

-- -----------------------------------------------------------
-- 17. 投递状态变更日志表
-- 审计每次状态变更的完整信息
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_status_log` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `application_id`      BIGINT UNSIGNED NOT NULL COMMENT '投递记录ID',
    `from_status`         VARCHAR(20)     COMMENT '原状态',
    `to_status`           VARCHAR(20)     NOT NULL COMMENT '目标状态',
    `operator_id`         BIGINT UNSIGNED COMMENT '操作人用户ID',
    `operator_role`       VARCHAR(16)     COMMENT '操作人角色：CANDIDATE/HR/SYSTEM',
    `reason`              VARCHAR(500)    COMMENT '变更原因',
    `idempotent_key`      VARCHAR(64)     COMMENT '幂等键',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent` (`idempotent_key`),
    KEY `idx_application_id` (`application_id`, `created_at`),
    KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='投递状态变更日志表';


-- -----------------------------------------------------------
-- 18. 简历诊断报告表
-- Agent 诊断产出的 Markdown 正文存储在 MinIO，本表仅存元数据
-- 同一 (resume_id, career) 允许多条历史记录，按 created_at DESC 查询最新
-- career 为用户输入的目标职业名，不关联平台岗位
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_diagnosis_report` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '诊断报告ID',
    `resume_id`       BIGINT UNSIGNED NOT NULL COMMENT '简历ID（关联 resume 表）',
    `career`          VARCHAR(64)     NOT NULL COMMENT '目标职业名（如"高级前端工程师"），不关联平台岗位',
    `file_url`        VARCHAR(512)    NOT NULL COMMENT 'MinIO文件路径（含时间戳）：/diagnosis/{resume_id}/{career}/{yyyy-MM-dd-HHmmss}.md',
    `match_score`     DECIMAL(5,2)    COMMENT '综合匹配度(0-100)，C的Resume Agent诊断时5维加权计算',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '诊断时间',
    PRIMARY KEY (`id`),
    KEY `idx_resume_career_time` (`resume_id`, `career`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='简历诊断报告表（Markdown正文存MinIO）';


-- ============================================================
-- 模块5: hr_ HR模块（成员D，6表）
-- ============================================================

-- -----------------------------------------------------------
-- 19. 企业信息表
-- 多企业入驻核心表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_company` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '企业ID',
    `name`                  VARCHAR(128)    NOT NULL COMMENT '企业全称（认证后不可修改）',
    `short_name`            VARCHAR(64)     COMMENT '企业简称',
    `description`           TEXT            COMMENT '企业简介',
    `industry`              VARCHAR(32)     NOT NULL COMMENT '所属行业',
    `scale`                 VARCHAR(20)     NOT NULL COMMENT '企业规模：0-50/50-200/200-500/500-2000/2000+',
    `logo_url`              VARCHAR(512)    COMMENT '企业Logo URL',
    `address`               VARCHAR(256)    COMMENT '详细办公地址',
    `website`               VARCHAR(256)    COMMENT '企业官网',
    `invite_code`           VARCHAR(6)      NOT NULL COMMENT '企业邀请码（6位）',
    `business_license_url`  VARCHAR(512)    COMMENT '营业执照URL',
    `cert_status`           VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '认证状态：PENDING=待审核 APPROVED=已通过 REJECTED=已拒绝',
    `cert_reject_reason`    VARCHAR(500)    COMMENT '认证拒绝原因',
    `status`                VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '企业状态：ACTIVE=正常 DISABLED=已禁用',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_cert_status` (`cert_status`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='企业信息表';

-- -----------------------------------------------------------
-- 20. 企业成员表
-- 关联用户与企业的成员身份
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_company_member` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `company_id`          BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role`                VARCHAR(16)     NOT NULL DEFAULT 'INTERVIEWER' COMMENT '角色：HR_ADMIN=HR管理员 INTERVIEWER=面试官',
    `department`          VARCHAR(64)     NOT NULL COMMENT '所属部门',
    `tech_direction`      VARCHAR(32)     COMMENT '技术方向',
    `interview_count`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '累计面试场次',
    `status`              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE=正常 DISABLED=已禁用',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_company_user` (`company_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role` (`company_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='企业成员表';

-- -----------------------------------------------------------
-- 21. 企业认证申请表
-- HR注册后提交企业认证，管理员审核
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_company_certification` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `company_id`            BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `applicant_id`          BIGINT UNSIGNED NOT NULL COMMENT '申请人用户ID',
    `business_license_url`  VARCHAR(512)    COMMENT '营业执照URL',
    `cert_material_url`     VARCHAR(512)    COMMENT '其他证明材料URL',
    `status`                VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING=待审核 APPROVED=已通过 REJECTED=已拒绝',
    `reject_reason`         VARCHAR(500)    COMMENT '拒绝原因',
    `reviewer_id`           BIGINT UNSIGNED COMMENT '审核人管理员ID',
    `reviewed_at`           DATETIME        COMMENT '审核时间',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_company_id` (`company_id`),
    KEY `idx_applicant_id` (`applicant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='企业认证申请表';

-- -----------------------------------------------------------
-- 22. 面试记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_interview` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '面试记录ID',
    `company_id`          BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `application_id`      BIGINT UNSIGNED NOT NULL COMMENT '投递记录ID',
    `interviewer_id`      BIGINT UNSIGNED NOT NULL COMMENT '面试官用户ID',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '候选人用户ID',
    `job_id`              BIGINT UNSIGNED NOT NULL COMMENT '岗位ID',
    `scheduled_at`        DATETIME        NOT NULL COMMENT '预约面试时间',
    `method`              VARCHAR(16)     NOT NULL DEFAULT 'OFFLINE' COMMENT '面试方式：OFFLINE=线下面试 ONLINE=视频面试 PHONE=电话面试',
    `location`            VARCHAR(256)    COMMENT '面试地点或视频链接',
    `remark`              VARCHAR(500)    COMMENT '面试备注',
    `candidate_note`      VARCHAR(500)    COMMENT '给候选人的留言',
    `status`              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '面试状态：PENDING=待面试 CONFIRMED=已确认 IN_PROGRESS=进行中 COMPLETED=已完成 CANCELLED=已取消',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_company_id` (`company_id`),
    KEY `idx_application_id` (`application_id`),
    KEY `idx_interviewer_id` (`interviewer_id`, `status`),
    KEY `idx_candidate_id` (`candidate_id`),
    KEY `idx_scheduled_at` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='面试记录表';

-- -----------------------------------------------------------
-- 23. 面试评估表
-- 与面试记录一对一，支持草稿保存
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_interview_evaluation` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `interview_id`          BIGINT UNSIGNED NOT NULL COMMENT '面试记录ID',
    `conclusion`            VARCHAR(16)     NOT NULL COMMENT '面试结论：PASS=通过 PENDING=待定 REJECT=淘汰',
    `tech_score`            TINYINT         NOT NULL COMMENT '技术能力评分(1-5)',
    `communication_score`   TINYINT         NOT NULL COMMENT '沟通表达评分(1-5)',
    `match_score`           TINYINT         NOT NULL COMMENT '岗位匹配评分(1-5)',
    `potential_score`       TINYINT         NOT NULL COMMENT '发展潜力评分(1-5)',
    `comment`               VARCHAR(2000)   NOT NULL COMMENT '面试评语',
    `feedback`              VARCHAR(2000)   COMMENT 'AI生成的反馈',
    `is_draft`              TINYINT         NOT NULL DEFAULT 0 COMMENT '是否草稿：0=正式提交 1=草稿',
    `evaluator_id`          BIGINT UNSIGNED NOT NULL COMMENT '评估人用户ID',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_interview_id` (`interview_id`),
    KEY `idx_evaluator_id` (`evaluator_id`),
    KEY `idx_conclusion` (`conclusion`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='面试评估表';

-- -----------------------------------------------------------
-- 24. Offer 记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `hr_offer` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Offer ID',
    `company_id`          BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `application_id`      BIGINT UNSIGNED NOT NULL COMMENT '投递记录ID',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '候选人用户ID',
    `job_id`              BIGINT UNSIGNED NOT NULL COMMENT '岗位ID',
    `salary`              INT             NOT NULL COMMENT '月薪（元）',
    `entry_date`          DATE            NOT NULL COMMENT '预计入职日期',
    `level`               VARCHAR(16)     COMMENT '职级',
    `remark`              VARCHAR(500)    COMMENT '备注',
    `status`              VARCHAR(16)     NOT NULL DEFAULT 'SENT' COMMENT 'Offer状态：SENT=待确认 ACCEPTED=已确认 REJECTED=已拒绝 EXPIRED=已过期',
    `expires_at`          DATETIME        NOT NULL COMMENT 'Offer有效期截止时间',
    `accepted_at`         DATETIME        COMMENT '确认时间',
    `rejected_at`         DATETIME        COMMENT '拒绝时间',
    `reject_reason`       VARCHAR(500)    COMMENT '拒绝原因',
    `urge_count`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '催促次数',
    `last_urge_at`        DATETIME        COMMENT '最近一次催促时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_id` (`application_id`),
    KEY `idx_company_id` (`company_id`),
    KEY `idx_candidate_id` (`candidate_id`),
    KEY `idx_job_id` (`job_id`),
    KEY `idx_status_expires` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='Offer表';


-- ============================================================
-- 模块6: admin_ 管理模块（成员E，4表）
-- ============================================================

-- -----------------------------------------------------------
-- 25. 管理员账号表
-- 独立于普通用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_user` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
    `username`            VARCHAR(32)     NOT NULL COMMENT '登录用户名',
    `password_hash`       VARCHAR(128)    NOT NULL COMMENT '密码哈希',
    `name`                VARCHAR(32)     NOT NULL COMMENT '管理员姓名',
    `status`              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE=正常 DISABLED=已禁用',
    `last_login_at`       DATETIME        COMMENT '最后登录时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='管理员账号表';

-- -----------------------------------------------------------
-- 26. 系统配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_config` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key`          VARCHAR(64)     NOT NULL COMMENT '配置键',
    `config_value`        TEXT            NOT NULL COMMENT '配置值',
    `description`         VARCHAR(256)    COMMENT '配置说明',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='系统配置表';

-- -----------------------------------------------------------
-- 27. 平台公告表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_announcement` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `title`               VARCHAR(128)    NOT NULL COMMENT '公告标题',
    `content`             TEXT            NOT NULL COMMENT '公告内容',
    `target_role`         VARCHAR(32)     COMMENT '目标角色：ALL=全部 CANDIDATE=求职者 HR=HR INTERVIEWER=面试官',
    `status`              VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED=已发布 ARCHIVED=已归档',
    `created_by`          BIGINT UNSIGNED NOT NULL COMMENT '发布人管理员ID',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_target_role` (`target_role`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='平台公告表';

-- -----------------------------------------------------------
-- 28. 操作日志表
-- 管理员操作审计
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '操作用户ID',
    `op_type`             VARCHAR(32)     NOT NULL COMMENT '操作类型',
    `detail`              VARCHAR(500)    NOT NULL COMMENT '操作详情',
    `ip_address`          VARCHAR(45)     COMMENT '操作IP',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `created_at`),
    KEY `idx_op_type` (`op_type`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='操作日志表';


-- ============================================================
-- 模块7: mock_ 模拟面试模块（成员D，3表）
-- ============================================================

-- -----------------------------------------------------------
-- 29. 模拟面试会话表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mock_session` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`          VARCHAR(32)     NOT NULL COMMENT '会话唯一标识',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `job_id`              BIGINT UNSIGNED COMMENT '目标岗位ID',
    `job_title`           VARCHAR(128)    COMMENT '目标岗位名称（冗余，便于历史查询）',
    `total_questions`     TINYINT         NOT NULL DEFAULT 5 COMMENT '总题数：5 或 8',
    `overall_score`       DECIMAL(5,2)    COMMENT '综合评分(0-100)',
    `status`              VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS=进行中 COMPLETED=已完成',
    `started_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `completed_at`        DATETIME        COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_candidate_id` (`candidate_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='模拟面试会话表';

-- -----------------------------------------------------------
-- 30. 模拟面试答题记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mock_answer` (
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`              VARCHAR(32)     NOT NULL COMMENT '会话ID',
    `question_number`         TINYINT         NOT NULL COMMENT '题号（从1开始）',
    `question_content`        VARCHAR(2000)   NOT NULL COMMENT '题目内容',
    `question_dimension`      VARCHAR(64)     COMMENT '考察维度',
    `question_type`           VARCHAR(20)     COMMENT '题目类型',
    `candidate_answer`        TEXT            COMMENT '求职者作答内容',
    `is_skipped`              TINYINT         NOT NULL DEFAULT 0 COMMENT '是否跳过：0=正常作答 1=跳过',
    `tech_accuracy_score`     DECIMAL(5,2)    COMMENT '技术准确度评分(0-100)',
    `expression_score`        DECIMAL(5,2)    COMMENT '表达逻辑评分(0-100)',
    `knowledge_depth_score`   DECIMAL(5,2)    COMMENT '知识深度评分(0-100)',
    `overall_score`           DECIMAL(5,2)    COMMENT '本题综合评分(0-100)',
    `ai_comment`              VARCHAR(500)    COMMENT 'AI点评',
    `answered_at`             DATETIME        COMMENT '作答时间',
    `created_at`              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`, `question_number`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='模拟面试答题记录表';

-- -----------------------------------------------------------
-- 31. 模拟面试报告表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mock_report` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`          VARCHAR(32)     NOT NULL COMMENT '会话ID',
    `candidate_id`        BIGINT UNSIGNED NOT NULL COMMENT '求职者用户ID',
    `overall_score`       DECIMAL(5,2)    NOT NULL COMMENT '综合评分(0-100)',
    `overall_level`       VARCHAR(16)     NOT NULL COMMENT '综合等级：EXCELLENT/GOOD/AVERAGE/NEED_IMPROVE',
    `tech_accuracy_score` DECIMAL(5,2)    COMMENT '技术准确度评分',
    `expression_score`    DECIMAL(5,2)    COMMENT '表达逻辑评分',
    `knowledge_depth_score` DECIMAL(5,2)  COMMENT '知识深度评分',
    `project_score`       DECIMAL(5,2)    COMMENT '项目经验评分',
    `highlights`          JSON            COMMENT '亮点列表',
    `weaknesses`          JSON            COMMENT '短板列表',
    `improvement_plan`    JSON            COMMENT '提升方案',
    `total_duration_sec`  INT UNSIGNED    COMMENT '总用时（秒）',
    `answered_count`      TINYINT         COMMENT '已答题数',
    `skipped_count`       TINYINT         COMMENT '跳过题数',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_candidate_id` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COMMENT='模拟面试报告表';


-- ============================================================
-- 关键设计说明
-- ============================================================
--
-- 1. 单库架构：
--    - 所有31张表在同一个 lingxi 数据库
--    - 各微服务通过代码层面解耦，不通过数据库隔离
--    - 同一服务内部表可以 JOIN 查询
--    - 不同微服务之间不建立物理外键、不跨模块直接 JOIN，通过接口交互
--
-- 2. 模块前缀规范：
--    - sys_  : 用户模块（成员A）
--    - msg_  : 消息模块（公共）
--    - job_  : 岗位模块（成员B）
--    - resume_: 简历模块（成员C）
--    - hr_   : HR模块（成员D）
--    - admin_: 管理模块（成员E）
--    - mock_ : 模拟面试模块（成员D）
--
-- 3. 公共模块说明：
--    - msg_conversation: C端+B端共用的会话表
--    - msg_message: C端+B端共用的消息表
--    - msg_notification: C端+B端+A端共用的通知表
--
-- 4. 幂等设计：
--    - resume_application: uk_job_candidate 防重复投递
--    - resume_status_log: uk_idempotent 防重复状态变更
--    - job_hc_reservation: uk_hc_offer 防Offer重复预冻结
--    - job_question: uk_question_content 防企业内重复题目
--
-- 5. 乐观锁：
--    - job_post: version 字段
--    - job_profile: version 字段
--    - job_hc_reservation: version 字段
--    - job_question: version 字段
--
-- 6. 软删除：
--    - job_post: deleted_at 字段
--    - job_question: deleted_at 字段
--    - resume: deleted_at 字段
--
-- ============================================================
