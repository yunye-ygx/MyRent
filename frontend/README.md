# MyRent Frontend

## 启动

```bash
cd frontend
npm install
npm run dev
```

默认开发地址：`http://localhost:5173`

## 环境说明

- 开发环境通过 `vite proxy` 转发到后端 `http://localhost:8081`
- HTTP 调用前缀：`/api`
- WebSocket：`/ws/chat`

## 已实现页面

- 登录 / 注册
- 首页（房源列表）
- 房源详情（提交定金）
- 消息会话列表
- 聊天详情（历史加载 + WebSocket + 补拉）
- 找房（地图跳转 + 附近房源示例）
- 我的（主结构 + 占位入口）
