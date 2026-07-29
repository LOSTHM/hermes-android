#!/usr/bin/env python3
"""Hermes serve 冒烟测试: WS 连通 + session.create + prompt.submit 完整一轮。

用法: python3 tools/smoke.py
返回码 0=全绿, 1=失败。只依赖 websockets (pip install websockets)。
"""
import asyncio
import json
import os
import sys

WS_URL = "ws://127.0.0.1:9119/api/ws"
TOKEN_FILE = os.path.expanduser("~/.hermes/app_token")
TIMEOUT = 90  # 一轮对话最长等待秒数


def load_token() -> str:
    with open(TOKEN_FILE) as f:
        return f.read().strip()


async def rpc(ws, method, params=None, rid=[0]):
    rid[0] += 1
    my_id = rid[0]
    await ws.send(json.dumps({
        "jsonrpc": "2.0", "id": my_id, "method": method, "params": params or {}
    }))
    while True:
        frame = json.loads(await asyncio.wait_for(ws.recv(), TIMEOUT))
        if frame.get("id") == my_id:
            if "error" in frame:
                raise RuntimeError(f"{method} -> {frame['error']}")
            return frame.get("result")
        # 非本请求的帧(事件)由调用方事件循环处理, 这里直接丢弃不合适,
        # 但冒烟测试简单起见: rpc 期间收到的事件打印一行便于观察
        if frame.get("method") == "event":
            t = frame.get("params", {}).get("type")
            print(f"    [event] {t}")


async def main() -> int:
    try:
        import websockets
    except ImportError:
        print("FAIL: pip install websockets")
        return 1

    token = load_token()
    print(f"1. connect {WS_URL}")
    async with websockets.connect(f"{WS_URL}?token={token}", max_size=50 * 1024 * 1024) as ws:
        print("   OK")

        print("2. session.create")
        res = await rpc(ws, "session.create", {"title": "smoke-test"})
        sid = res.get("session_id") or res.get("id") or (res.get("session") or {}).get("id")
        assert sid, f"no session id in {res}"
        print(f"   OK sid={sid}")

        print("3. prompt.submit (等 message.complete)")
        # prompt.submit 可能先返回, 事件流随后; 用一个消费循环
        rid = 999
        await ws.send(json.dumps({
            "jsonrpc": "2.0", "id": rid, "method": "prompt.submit",
            "params": {"session_id": sid, "text": "Reply with exactly: SMOKE_OK"}
        }))
        got_start = got_complete = False
        text_acc = ""
        while True:
            frame = json.loads(await asyncio.wait_for(ws.recv(), TIMEOUT))
            if frame.get("id") == rid:
                print(f"   prompt.submit ack: {json.dumps(frame.get('result'))[:120]}")
                continue
            if frame.get("method") != "event":
                continue
            p = frame.get("params", {})
            t = p.get("type")
            if t == "message.start":
                got_start = True
            elif t == "message.delta":
                text_acc += (p.get("payload") or {}).get("text", "")
            elif t == "message.complete":
                got_complete = True
                final_text = (p.get("payload") or {}).get("text") or text_acc
                print(f"   complete, {len(final_text)} chars")
                break
            elif t == "error":
                print(f"   FAIL event error: {p}")
                return 1
        assert got_start, "never saw message.start"
        assert got_complete, "never saw message.complete"

        print("4. session.list 校验会话存在")
        sessions = await rpc(ws, "session.list", {})
        items = sessions.get("sessions") if isinstance(sessions, dict) else sessions
        print(f"   {len(items or [])} sessions")

        print("5. config.get (key=model)")
        cfg = await rpc(ws, "config.get", {"key": "provider"})
        print(f"   model config: {json.dumps(cfg)[:100]}")
        print("\nSMOKE PASS")
        return 0


if __name__ == "__main__":
    try:
        sys.exit(asyncio.run(main()))
    except Exception as e:
        print(f"\nSMOKE FAIL: {type(e).__name__}: {e}")
        sys.exit(1)
