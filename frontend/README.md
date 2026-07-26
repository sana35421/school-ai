# 校园智能问答助手 · 前端

Vue 3 + TypeScript + Vite + TailwindCSS + Element Plus 实现。

## 设计美学

**Modern Academic Editorial** — 学院派编辑设计
- 主色：`#FAF7F2`（温润纸张白）
- 强调色：`#B85C38`（赤陶橙）
- AI 气泡：`#6B7F6B`（灰苔绿）
- 字体：Fraunces（显示） / IBM Plex Sans（正文） / JetBrains Mono（代码）

## 本地开发

```bash
# 1. 安装依赖（需要 Node 18+）
npm install

# 2. 启动开发服务器
npm run dev

# 浏览器访问 http://localhost:5173
```

后端默认在 `http://localhost:8080`，通过 Vite 代理转发。

## 构建生产版本

```bash
npm run build
# 产物在 dist/ 目录
```

## 目录结构

```
src/
├── api/           # API 封装
├── components/    # 通用组件
├── router/        # 路由
├── stores/        # Pinia 状态
├── styles/        # 全局样式
├── views/         # 页面
├── App.vue
└── main.ts
```

## 路由说明

| 路径 | 页面 | 权限 |
|------|------|------|
| `/login` | 登录 | 公开 |
| `/chat` | 聊天主页 | 登录用户 |
| `/chat/:id` | 继续对话 | 登录用户 |
| `/admin` | 班级对话审计 | 导员/管理员 |
| `/admin/knowledge` | 知识库管理 | 管理员 |

## 接入后端

修改 `.env.development` 中的 `VITE_API_BASE` 即可。默认 `http://localhost:8080/api`。