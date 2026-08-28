-- 为已有数据库补充聊天消息的知识库来源字段；可重复执行。
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'messages'
      AND COLUMN_NAME = 'sources_json'
);
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE messages ADD COLUMN sources_json TEXT NULL COMMENT ''Dify知识库检索来源JSON'' AFTER content',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
