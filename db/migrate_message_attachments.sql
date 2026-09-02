-- Create the message-to-upload relation table independently from the
-- upload_records ALTER migration so deployments can safely rerun this step.
CREATE TABLE IF NOT EXISTS message_attachments (
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
