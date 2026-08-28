ALTER TABLE upload_records
    ADD COLUMN dify_file_id VARCHAR(100) NULL COMMENT 'Dify上传文件ID' AFTER file_size,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'metadata_only' COMMENT '上传状态' AFTER dify_file_id,
    ADD COLUMN error_message VARCHAR(500) NULL COMMENT '上传失败原因' AFTER status,
    ADD INDEX idx_upload_status (user_id, status),
    ADD UNIQUE INDEX uk_dify_file_id (dify_file_id);

CREATE TABLE message_attachments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    upload_record_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_upload (message_id, upload_record_id),
    INDEX idx_message (message_id),
    INDEX idx_upload (upload_record_id),
    CONSTRAINT fk_attachment_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_attachment_upload FOREIGN KEY (upload_record_id) REFERENCES upload_records(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息附件关联表';
