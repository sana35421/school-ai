# 校园智能问答助手

校园智能问答系统（Campus AI）面向学生、导员和管理员，提供基于 Dify 知识库与大模型的校园问答服务。系统当前重点支持学科竞赛、计算机竞赛和学校政策等场景，并保存登录用户的对话历史。

## 1. 项目能力

- **智能问答**：调用 Dify Chat API，支持知识库检索和流式回答。
- **用户登录**：基于学号/账号、密码和 JWT 鉴权。
- **对话管理**：新建对话、继续历史对话、查看消息和删除对话。
- **权限管理**：学生、导员、管理员和超级管理员使用不同功能。
- **班级管理**：导员或管理员查看授权范围内的学生与对话。
- **知识库管理**：管理员通过 Dify 管理知识库和应用文档。
- **中文支持**：MySQL、Spring Boot、Nginx 全链路使用 UTF-8/utf8mb4。
- **竞赛检索增强**：支持赛事简称/别名归一化，并按赛事严格隔离简介、指导老师和队友资料；易班数字校园联合查询可同时返回两类人员。
- **可靠性保障**：Dify 空回答自动重试并提供可见兜底；知识库未命中且启用配置时，可通过博查搜索返回至少 3 条公开网页结果。

## 2. 技术架构

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus、TailwindCSS |
| 前端服务 | Nginx Alpine，提供静态页面并代理 `/api/` |
| 后端 | Spring Boot 3.2、Java 17、MyBatis-Plus、Spring Security、JWT |
| 数据库 | MySQL 8.0，字符集 `utf8mb4` |
| 缓存 | Redis 7 Alpine |
| AI 服务 | Dify Cloud 或已部署的 Dify API |
| 部署 | Docker Compose |

请求链路如下：

```text
浏览器 -> Nginx 前端容器 -> Spring Boot 后端 -> Dify API -> 知识库/大模型
                                      |
                                  MySQL / Redis
```

## 3. 运行前准备

### 3.1 Docker Compose 运行

推荐使用 Docker Desktop（Windows）或 Docker Engine + Docker Compose 插件（Linux）。

```bash
docker --version
docker compose version
```

### 3.2 配置 Dify

项目不再默认启动一套本地 Dify。生产环境建议使用 Dify Cloud；也可以单独部署与维护版本匹配的 Dify 服务。

在 Dify 中完成以下配置：

1. 创建聊天应用。
2. 配置 DeepSeek 或其他大模型供应商。
3. 创建知识库并上传校园政策、竞赛说明等权威资料。
4. 将知识库关联到聊天应用。
5. 发布应用并复制应用 API Key。

API Key 只写入本机或服务器的 `.env`，不要提交 Git，也不要发送到聊天记录中。

## 4. Docker Compose 启动

### 4.1 创建环境文件

在项目根目录执行：

```powershell
cd D:\school-ai
Copy-Item .env.example .env
notepad .env
```

至少填写以下变量：

```env
MYSQL_PASSWORD=请设置强密码
DIFY_API_BASE=https://api.dify.ai/v1
DIFY_API_KEY=app-你的Dify应用Key
JWT_SECRET=请设置至少32位随机字符串
```

如果使用独立部署的 Dify，将 `DIFY_API_BASE` 改为实际 API 地址。不要在 `.env.example` 或 Git 中填写真实密钥。

### 4.1.1 可选：知识库无匹配时联网检索

系统默认只使用知识库。配置博查搜索 API 后，只有 Dify 明确返回“知识库暂无具体信息”且没有检索来源时，后端才会查询网络；正常知识库问答保持原有流式输出。

```env
WEB_SEARCH_ENABLED=true
BOCHA_API_KEY=请填写你的博查搜索 API Key
WEB_SEARCH_MAX_RESULTS=5
WEB_SEARCH_TIMEOUT_SECONDS=12
# 生产环境建议仅允许官方来源，多个域名用逗号分隔
WEB_SEARCH_ALLOWED_DOMAINS=edu.cn,moe.gov.cn,gov.cn,ccpc.io
```

网络回答会标注“网络检索结果”，并展示可点击的来源链接。未配置密钥、搜索超时或无可信结果时，系统保留知识库的无匹配提示，不会生成网络内容。

### 4.2 启动服务

```powershell
docker compose config
docker compose up -d --build
docker compose ps
```

默认启动的业务服务为：

- `mysql`：业务数据库
- `redis`：缓存
- `backend`：Spring Boot API
- `frontend`：Nginx 前端

浏览器访问：

```text
http://localhost
```

### 4.3 常用运维命令

```powershell
# 查看状态
docker compose ps

# 查看服务日志
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend

# 实时查看日志
docker compose logs -f backend

# 重启服务
docker compose restart

# 重新构建指定服务
docker compose up -d --build backend
docker compose up -d --build frontend

# 停止容器但保留数据卷
docker compose down
```

不要在生产环境执行 `docker compose down -v`，否则可能删除 MySQL 和 Redis 数据卷。

## 5. 本地开发

### 后端

需要 JDK 17、Maven 3.8+、MySQL 8.0 和可访问的 Dify API。

```powershell
cd D:\school-ai\backend
mvn spring-boot:run
```

后端默认地址：`http://localhost:8080`。

### 前端

需要 Node.js 18+。

```powershell
cd D:\school-ai\frontend
npm install
npm run dev
```

开发服务器默认地址：`http://localhost:5173`。开发环境 API 地址由 Vite 配置代理到后端。

### 生产构建

```powershell
cd D:\school-ai\frontend
npm run build

cd D:\school-ai\backend
mvn clean package -DskipTests
```

## 6. 核心接口

| 接口 | 方法 | 权限 | 说明 |
| --- | --- | --- | --- |
| `/api/auth/login` | POST | 公开 | 用户登录 |
| `/api/auth/me` | GET | 登录用户 | 获取当前用户信息 |
| `/api/chat/send` | POST | 登录用户 | 发送问题并接收 SSE |
| `/api/chat/history` | GET | 登录用户 | 获取历史对话 |
| `/api/chat/messages/{id}` | GET | 登录用户 | 获取对话消息 |
| `/api/chat/{id}` | DELETE | 登录用户 | 删除对话 |
| `/api/admin/class/students` | GET | 导员/管理员 | 获取班级学生 |
| `/api/admin/class/conversations` | GET | 导员/管理员 | 查看班级对话 |
| `/api/admin/class/messages/{id}` | GET | 导员/管理员 | 查看对话详情 |

## 7. 测试账号

初始化脚本中的账号仅用于开发和演示：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin001` | `123456` | 超级管理员 |
| `admin002` | `123456` | 管理员 |
| `T001` | `123456` | 导员 |
| `20220101` | `123456` | 学生 |

正式上线前必须修改默认密码，并确认账号对应的真实人员和权限。

## 8. 目录结构

```text
school-ai/
├── backend/                         # Spring Boot 后端
├── frontend/                        # Vue 3 前端和 Nginx 配置
├── db/init.sql                      # 初始数据库结构和演示数据
├── docker-compose.yml               # 业务容器编排
├── .env.example                     # 环境变量模板，不含真实密钥
├── computer_competition_*.md        # 比赛系统提示词和知识库资料
├── competitions_kb.txt              # 比赛知识库资料
├── scholarship_and_grad_school_kb.md # 奖学金、考研与保研知识库资料
└── docs/                            # 部署和上线文档
```

## 9. 版本验证记录

本版本已完成以下验证：

- `mvn -q -DskipTests=false test`：后端单元测试通过。
- `mvn -q -Dtest=ChatServiceImplAttachmentTest test`：赛事别名、意图分流、空回答重试相关测试通过。
- 线上 `http://8.137.157.182`：易班比赛简介、指导老师、队友及老师与队友联合查询均可返回；后端 `/api/health` 返回 `200`。

发布新版本时，建议先在本地运行测试，再执行：

```bash
git push origin <当前分支>
cd /opt/school-ai
git pull --ff-only origin <当前分支>
docker compose -p school-ai up -d --build backend frontend
docker compose -p school-ai ps
curl http://localhost:8080/api/health
```

## 9. 上线前检查

- [ ] `.env` 已配置，且没有被 Git 跟踪。
- [ ] Dify 应用、知识库和模型调用正常。
- [ ] `DIFY_API_KEY` 已使用有效且未泄露的 Key。
- [ ] 默认账号密码已修改。
- [ ] 登录返回中文姓名和正确角色。
- [ ] 聊天接口可以持续接收 SSE 流式响应。
- [ ] MySQL、Redis 数据已备份。
- [ ] 服务器只开放必要的 22、80、443 端口。
- [ ] 3306、6379 未暴露到公网。
- [ ] 已配置域名、HTTPS、日志和监控。

更多发布步骤请查看 [`docs/部署指南.md`](docs/部署指南.md) 和 [`docs/上线方案.md`](docs/上线方案.md)。
