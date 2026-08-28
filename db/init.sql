-- ===========================================================
-- 校园智能问答助手 · 数据库初始化脚本
-- 在 MySQL 8.0+ 上执行
-- ===========================================================

CREATE DATABASE IF NOT EXISTS school_ai DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE school_ai;

-- 班级表
DROP TABLE IF EXISTS kb_documents;
DROP TABLE IF EXISTS message_attachments;
DROP TABLE IF EXISTS upload_records;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS classes;

CREATE TABLE classes (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_name  VARCHAR(100) NOT NULL COMMENT '班级名称',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '班级表';

CREATE TABLE users (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id  VARCHAR(20) UNIQUE NOT NULL COMMENT '学号/工号',
    username    VARCHAR(50) UNIQUE NOT NULL COMMENT '登录用户名',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
    real_name   VARCHAR(50) COMMENT '真实姓名',
    class_id    BIGINT COMMENT '所属班级',
    role        VARCHAR(20) DEFAULT 'student' COMMENT '角色: student/counselor/admin/super_admin',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_class (class_id),
    INDEX idx_role (role),
    CONSTRAINT fk_user_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '用户表';

CREATE TABLE conversations (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id         VARCHAR(100) UNIQUE NOT NULL COMMENT '会话UUID',
    user_id                 BIGINT NOT NULL,
    title                   VARCHAR(200) DEFAULT '新对话' COMMENT '会话标题',
    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_active_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_active (user_id, last_active_at),
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '会话表';

CREATE TABLE messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id VARCHAR(100) NOT NULL COMMENT '关联会话UUID',
    user_id         BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL COMMENT 'user 或 assistant',
    content         TEXT NOT NULL,
    sources_json    TEXT NULL COMMENT 'Dify知识库检索来源JSON',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_time (conversation_id, created_at),
    INDEX idx_user (user_id),
    CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '消息表';

CREATE TABLE upload_records (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type       VARCHAR(100) NOT NULL COMMENT '文件MIME类型',
    file_size       BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    dify_file_id    VARCHAR(100) NULL COMMENT 'Dify上传文件ID',
    status          VARCHAR(30) NOT NULL DEFAULT 'uploaded' COMMENT '上传状态',
    error_message   VARCHAR(500) NULL COMMENT '上传失败原因',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    INDEX idx_user (user_id),
    INDEX idx_upload_status (user_id, status),
    UNIQUE INDEX uk_dify_file_id (dify_file_id),
    CONSTRAINT fk_upload_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '上传文件记录表';

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

CREATE TABLE kb_documents (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(100) NOT NULL COMMENT '文件MIME类型',
    file_size   BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    storage_path VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    status      VARCHAR(30) DEFAULT 'pending' COMMENT '索引状态',
    dify_doc_id VARCHAR(100) DEFAULT '' COMMENT 'Dify文档ID',
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dify_doc (dify_doc_id),
    CONSTRAINT fk_kb_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档审计表';

-- ===========================================================
-- 初始数据（密码统一为 123456，BCrypt 加密）
-- 对应原文：123456 → $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- ===========================================================

INSERT INTO classes (class_name) VALUES
('计算机科学与技术2201班'),
('计算机科学与技术2202班'),
('软件工程2201班');

-- 超级管理员（密码 123456）
INSERT INTO users (student_id, username, password, real_name, role)
VALUES ('admin001', 'superadmin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'super_admin');

-- 管理员（密码 123456）
INSERT INTO users (student_id, username, password, real_name, role)
VALUES ('admin002', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '教务管理员', 'admin');

-- 导员（密码 123456）
INSERT INTO users (student_id, username, password, real_name, class_id, role)
VALUES
('T001', 'teacher_zhang', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张老师', 1, 'counselor'),
('T002', 'teacher_li', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李老师', 2, 'counselor');

-- 学生（密码 123456）
INSERT INTO users (student_id, username, password, real_name, class_id, role)
VALUES
('20220101', 'student_li', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李同学', 1, 'student'),
('20220102', 'student_wang', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王同学', 1, 'student'),
('20220103', 'student_zhao', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵同学', 1, 'student'),
('20220201', 'student_qian', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '钱同学', 2, 'student'),
('yiban_test', 'yiban_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '易班测试用户', 1, 'student');

-- ===========================================================
-- 测试账号
-- 超级管理员：admin001 / 123456
-- 管理员：admin002 / 123456
-- 导员：T001 / 123456
-- 学生：20220101 / 123456
-- ===========================================================
