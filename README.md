# hermes-android

Hermes Agent 的 Android 原生前端。后端复用本机 Termux 里运行的 `hermes serve`（JSON-RPC/WebSocket + REST）。

## 架构

```
Android App (Kotlin + Jetpack Compose, Material 3)
   │  WS  ws://127.0.0.1:9119/api/ws?token=…   (JSON-RPC 2.0, 实时聊天流)
   │  REST http://127.0.0.1:9119/api/*         (管理面板/配置/cron…)
   ▼
hermes serve (Termux 后台, 127.0.0.1:9119)
   ▼
Hermes Agent 核心 (会话/工具/模型/skills…)
```

App 只是客户端，不跑 agent。所有能力来自 `hermes serve`。

## 认证

本地回环绑定下用固定 token：
- token 存 `~/.hermes/app_token`（权限 600），由 `tools/serve-dev.sh` 生成并注入
- WS 连接：`ws://127.0.0.1:9119/api/ws?token=<token>`
- REST：`?token=<token>` 或 header

## 起后端（开发）

```sh
~/hermes-android/tools/serve-dev.sh   # 前台跑, 端口 9119
```

## 工具（tools/）

- `serve-dev.sh` — 起 `hermes serve`，固定 token
- `smoke.py` — 冒烟测试：WS 连通 + 建会话 + 一轮流式对话 + 列会话 + 读配置。**hermes update 后必跑**
- `protocol_diff.py` — 协议面 diff。`--update` 建基线；默认报告 新增/移除，退出码 1 = 有移除（App 在用的方法被上游删了，必须修）
- `protocol_manifest.json` — 协议基线 + App 已接入清单
- `warm_probe.py` / `warm_retry_probe.py` — 排查 agent build 冷启动用的探针

## 跟版维护流程

1. `hermes update` 后：
   - 检查 venv 是否重建 → `~/.hermes/hermes-agent/venv/pyvenv.cfg` 的 `include-system-site-packages` 要 = `true`（否则 mem0 lazy-install 编译 numpy 卡死 agent build，30s 超时）
2. `python3 tools/smoke.py` → 必须 PASS
3. `python3 tools/protocol_diff.py` → 看新增（可选接入）/移除（必须处理）
4. 接入新功能后 `protocol_diff.py --update` 更新基线

## 已知坑

- **agent build 30s 硬超时**：每个新会话第一次 prompt 时才惰性 build agent。若 `memory.provider=mem0` 且 venv 缺 mem0ai，会现编译 numpy 卡死。解法见上。cold build 约 27–37s，App 首次发消息要给"正在启动 agent"反馈，别当失败。
- `config.get` 合法 key：`provider`/`profile`/`project`/`full`/`prompt`/`skin`/`indicator`，不接受 `model`。
- Termux 无 `/tmp`，脚本用 `$PREFIX/tmp` 或项目目录。
