# 校园智能问答助手 · Campus AI

> 类 DeepSeek 的校园智能对话系统，集成 Dify 知识库与 DeepSeek 大模型，针对学生**学科竞赛**与**学校政策**提问提供基于校内权威文档的精准回答。

![architecture](.trae/documents/Technical-Architecture.md)

## ✨ 核心功能

- 🎓 **智能问答**：基于知识库 + DeepSeek 的 RAG 问答
- 📚 **一周历史**：自动保存对话，可继续提问
- 🆕 **新建对话**：点击即开空白会话
- 👥 **导员后台**：查看本班学生一周内对话
- 📂 **知识库管理**：管理员上传/管理文档（Dify）

## 🛠 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + TailwindCSS + Element Plus |
| 后端 | Spring Boot 3 + Java 17 + MyBatis-Plus + Spring Security + JWT |
| 数据库 | MySQL 8.0 |
| AI 引擎 | Dify 1.0 + DeepSeek API |

## 🎨 设计美学

**Modern Academic Editorial** — 学院派编辑设计
- 主色：`#FAF7F2`（温润纸张白）
- 强调色：`#B85C38`（赤陶橙）
- AI 气泡：`#6B7F6B`（灰苔绿）
- 字体：Fraunces（显示） / IBM Plex Sans（正文）

## 🚀 快速开始

### 方式一：Docker Compose 一键启动（推荐）

```bash
# 1. 克隆代码
git clone <repo-url>
cd school-ai

# 2. 启动所有服务（MySQL + Redis + Dify + 后端 + 前端）
docker compose up -d

# 3. 等待约 1 分钟，访问
open http://localhost
```

### 方式二：本地开发模式

**1. 启动 MySQL 与 Dify**
```bash
docker compose up -d mysql redis dify-api dify-web
```

**2. 初始化数据库**
```bash
mysql -uroot -p < db/init.sql
```

**3. 启动后端**
```bash
cd backend
mvn spring-boot:run
```

**4. 启动前端**
```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`

## 👤 测试账号

| 学号 | 密码 | 角色 |
|------|------|------|
| admin001 | 123456 | 超级管理员 |
| admin002 | 123456 | 管理员 |
| T001 | 123456 | 导员（计科2201班）|
| 20220101 | 123456 | 学生（计科2201班）|

## 📂 目录结构

```
school-ai/
├── frontend/                  # Vue 3 前端
├── backend/                   # SpringBoot 后端
├── db/init.sql                # 数据库初始化脚本
├── docker-compose.yml         # 一键部署
├── .trae/documents/
│   ├── PRD.md                 # 产品需求文档
│   └── Technical-Architecture.md  # 技术架构文档
└── README.md                  # 本文件
```

## 📚 文档

- 📋 [产品需求文档 PRD](.trae/documents/PRD.md)
- 🏗️ [技术架构文档](.trae/documents/Technical-Architecture.md)
- 🎨 [前端设计说明](frontend/README.md)
- ⚙️ [后端开发指南](backend/README.md)

## 🎯 部署到 D 盘

本项目所有文件已存放在 `D:\school-ai\`。

## ⚠️ 上线检查清单

- [ ] 修改 `application.yml` 的 MySQL 密码
- [ ] 修改 `jwt.secret` 为复杂随机字符串
- [ ] 在 Dify 中配置 DeepSeek API Key
- [ ] 修改所有默认账号密码（admin001、123456）
- [ ] 配置 Nginx HTTPS（Let's Encrypt）
- [ ] 配置数据库备份策略
- [ ] 配置日志收集与监控