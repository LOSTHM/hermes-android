# Hermes 协议事件契约（UI 渲染用）

从 `tui_gateway/server.py` 的 `_emit` 点读出的真实 payload。UI 按这个渲染，别猜。

## 帧结构

- 请求 `{jsonrpc:"2.0", id, method, params}`
- 响应 `{id, result}` 或 `{id, error:{code, message}}`
- 事件 `{method:"event", params:{type, session_id, payload}}`

## 事件 payload（按 type）

### message 流
- `message.start` — payload 空。开始一条 assistant 消息，UI 新建气泡
- `message.delta` — `{text}` 追加到当前气泡
- `message.interim` — `{text, ...}` 中间态（如工具调用后继续），可选渲染
- `message.complete` — `{text, status?}` 最终文本。status=error 时当错误显示。**以 complete 的 text 为准**（delta 累积是冗余，complete 给全文）

### thinking / reasoning
- `thinking.delta` / `reasoning.delta` — `{text}` 推理流，渲染成可折叠块
- `reasoning.available` — 有完整推理可取

### 工具调用
- `tool.start` — `{tool_id, name, context, args_text?}`。context 是工具上下文摘要。UI 渲染工具卡片头（名称+context）
- `tool.progress` — `{tool_id, ...}` 进度，可选
- `tool.complete` — `{tool_id, name, args, result, duration_s?, summary?, result_text?}`。result 可能是 JSON 对象或字符串；summary 是人类可读一行摘要（UI 优先显示它，别直接糊 result 全文）
- `tool.generating` — 生成类工具（图片等）进行中

### 交互请求（需要用户响应）
- `clarify.request` — 需要用户回答。响应 `clarify.respond`
- `approval.request` — `{command?, ...}` 危险操作审批。响应 `approval.respond`（command 已脱敏）
- `sudo.request` — sudo 密码。响应 `sudo.respond`
- `secret.request` — 密钥输入。响应 `secret.respond`

### 会话
- `session.info` — 会话元信息更新（model、token 用量等）
- `session.title` — `{session_id, title}`
- `status.update` — `{kind, text}` 状态栏信息（kind: process 等）
- `background.complete` — 后台任务完成
- `error` — `{message}` 会话级错误
- `gateway.ready` — 连接就绪，`{skin}`
- `skin.changed` — 主题变化

## RPC 响应（已实测）

- `session.create` → `{session_id, ...}`（也可能 `id`，取时有兜底）
- `prompt.submit` → `{status:"streaming"}`，随后事件流
- `config.get {key:"provider"}` → `{model, provider, providers:[{id,label,...}]}`
- `session.list` → `{sessions:[...]}` 或直接数组

## 错误帧

- `agent initialization timed out` — 首次 cold build 超 30s。App 应提示"agent 启动中"并允许重试，**不是致命错误**（mem0 修好后 cold ~27-37s）
- error.code 4002 = 参数错误（如 config.get 空 key）
