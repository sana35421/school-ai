# 后端服务

校园智能问答助手后端，基于 Spring Boot 3.2、Java 17、MyBatis-Plus、Spring Security 和 JWT 实现。

## 技术栈

- Spring Boot 3.2.5
- Java 17
- Maven 3.8+
- MyBatis-Plus 3.5.5
- Spring Security 6 + JWT
- OkHttp 4.12
- MySQL 8.0
- Dify API

## 配置

生产环境通过项目根目录 `.env` 注入配置：

```env
MYSQL_PASSWORD=数据库密码
DIFY_API_BASE=https://api.dify.ai/v1
DIFY_API_KEY=app-Dify应用Key
JWT_SECRET=至少32位随机字符串
```

`application.yml` 已配置 MySQL、Dify 和 JWT 的环境变量读取，不要把真实密码或 API Key 写入源码。

本地直接运行后端前，需要准备 MySQL 8.0，并执行 `db/init.sql` 初始化数据库；使用 Docker Compose 时，MySQL 会由 Compose 管理。

## 启动方式

### Docker Compose

在项目根目录执行：

```powershell
docker compose up -d --build mysql redis backend
docker compose ps
```

### Maven

```powershell
cd backend
mvn spring-boot:run
```

### 打包运行

```powershell
cd backend
mvn clean package -DskipTests
java -jar target/school-ai-backend.jar
```

后端默认监听 `http://localhost:8080`。

## API 一览

| 路径 | 方法 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| `/api/auth/login` | POST | 无 | 登录 |
| `/api/auth/me` | GET | 是 | 获取当前用户 |
| `/api/health` | GET | 无 | 服务健康检查 |
| `/api/chat/send` | POST | 是 | 发送问题，SSE 流式响应 |
| `/api/chat/history` | GET | 是 | 获取历史对话 |
| `/api/chat/messages/{id}` | GET | 是 | 获取对话消息 |
| `/api/chat/{id}` | DELETE | 是 | 删除对话 |
| `/api/admin/class/students` | GET | 导员/管理员 | 获取班级学生 |
| `/api/admin/class/conversations` | GET | 导员/管理员 | 查看班级对话 |
| `/api/admin/class/messages/{id}` | GET | 导员/管理员 | 查看对话详情 |

## 接口测试

```powershell
curl.exe http://localhost:8080/api/health

curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"studentId":"20220101","password":"123456"}'
```

## 测试账号

以下账号由 `db/init.sql` 提供，仅用于开发和演示：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin001` | `123456` | 超级管理员 |
| `admin002` | `123456` | 管理员 |
| `T001` | `123456` | 导员 |
| `20220101` | `123456` | 学生 |

正式部署前必须修改默认密码。
