# 前端服务

校园智能问答助手前端，基于 Vue 3、TypeScript、Vite、Pinia、Element Plus 和 TailwindCSS 实现。

## 功能页面

| 路径 | 页面 | 权限 |
| --- | --- | --- |
| `/login` | 登录页 | 公开 |
| `/chat` | 聊天主页 | 登录用户 |
| `/chat/:id` | 历史对话 | 登录用户 |
| `/admin` | 班级对话管理 | 导员/管理员 |
| `/admin/knowledge` | 知识库管理 | 管理员 |

## 本地开发

需要 Node.js 18+：

```powershell
cd frontend
npm install
npm run dev
```

开发地址为 `http://localhost:5173`。Vite 将 API 请求代理到本地后端。

## 生产构建

```powershell
cd frontend
npm run build
```

构建前会执行 TypeScript 检查，成功后生成 `dist/`。Docker 生产镜像会把 `dist/` 复制到 Nginx，并使用 `nginx.conf` 代理后端接口。

```powershell
docker compose up -d --build frontend
```

生产访问地址为 `http://localhost`，Nginx 负责：

- 返回 Vue 单页应用静态文件。
- 将 `/api/` 转发至 `backend:8080`。
- 为 SSE 聊天关闭代理缓存并延长读取超时。
- 添加 UTF-8 响应配置。

不要在 `nginx.conf` 中写入本地电脑专用的 Dify 容器名。Dify API 由后端根据 `DIFY_API_BASE` 调用。

## 目录结构

```text
src/
├── api/           # 登录、聊天、管理和上传接口
├── components/    # 通用 Vue 组件
├── router/        # 路由和权限跳转
├── stores/        # 用户和聊天状态
├── styles/        # 全局样式
├── views/         # 登录、聊天、管理页面
├── App.vue
└── main.ts
```

## 常用命令

```powershell
npm run dev
npm run build
npm run preview
```
