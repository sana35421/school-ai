# 校园智能问答助手 · 后端服务

Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8.0 + Dify + DeepSeek

## 技术栈

- SpringBoot 3.2.5
- Java 17
- MyBatis-Plus 3.5.5
- Spring Security 6 + JWT
- OkHttp 4.12 (调用 Dify)
- MySQL 8.0

## 本地开发

### 1. 准备环境

- JDK 17+
- Maven 3.8+
- MySQL 8.0+（先执行 `db/init.sql` 创建库表）
- Dify 服务（本地或远程）

### 2. 配置 application.yml

修改 `src/main/resources/application.yml`：
- `spring.datasource.password`：你的 MySQL 密码
- `dify.api-base`：Dify 服务地址（默认 `http://localhost/v1`）
- `dify.api-key`：Dify 应用的 API Key
- `jwt.secret`：JWT 签名密钥（生产环境务必修改）

### 3. 启动

```bash
# 在 backend/ 目录下
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/school-ai-backend.jar
```

服务启动在 `http://localhost:8080`

### 4. 健康检查

```bash
curl http://localhost:8080/api/health
```

## API 一览

| 路径 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/auth/login` | POST | 无 | 登录 |
| `/api/auth/me` | GET | 是 | 获取当前用户 |
| `/api/chat/send` | POST | 是 | 流式发送消息 (SSE) |
| `/api/chat/history` | GET | 是 | 一周内历史对话 |
| `/api/chat/messages/{id}` | GET | 是 | 对话详情 |
| `/api/chat/{id}` | DELETE | 是 | 删除对话 |
| `/api/admin/class/students` | GET | 导员/管理员 | 本班学生 |
| `/api/admin/class/conversations` | GET | 导员/管理员 | 本班对话 |
| `/api/admin/class/messages/{id}` | GET | 导员/管理员 | 班级对话详情 |

## 测试账号

执行 `db/init.sql` 后自动生成：

| 学号 | 密码 | 角色 |
|------|------|------|
| admin001 | 123456 | 超级管理员 |
| admin002 | 123456 | 管理员 |
| T001 | 123456 | 导员（计科2201班）|
| 20220101 | 123456 | 学生（计科2201班）|