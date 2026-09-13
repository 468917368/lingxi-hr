-- ============================================================
-- 服务器 DB 补列：msg_message 增加 media_url/file_name/file_size
-- 与本地 DB / 新 chat 代码保持一致
-- 执行：docker exec -i mysql mysql -uroot -p123456 --default-character-set=utf8mb4 lingxi < fix_server_msg_message_schema.sql
-- 若报 Duplicate column 说明已加过，忽略即可
-- ============================================================

ALTER TABLE `msg_message`
    ADD COLUMN `media_url` VARCHAR(512) NULL COMMENT 'media url' AFTER `msg_type`,
    ADD COLUMN `file_name` VARCHAR(255) NULL COMMENT 'file name' AFTER `media_url`,
    ADD COLUMN `file_size` BIGINT UNSIGNED NULL COMMENT 'file size' AFTER `file_name`;
