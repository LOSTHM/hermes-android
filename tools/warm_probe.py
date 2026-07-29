#!/usr/bin/env python3
"""验证: 建会话后等 agent ready (不发 prompt), 看 build 要多久。"""
import asyncio, json, os, sys, time

WS_URL = "ws://127.0.0.1:9119/api/ws"
TOKEN = open(os.path.expanduser("~/.hermes/app_token")).read().strip()

async def rpc(ws, method, params=None, _id=[0]):
    _id[0] += 1
    mid = _id[0]
    await ws.send(json.dumps({"jsonrpc":"2.0","id":mid,"method":method,"params":params or {}}))
    while True:
        f = json.loads(await asyncio.wait_for(ws.recv(), 120))
        if f.get("id") == mid:
            if "error" in f: raise RuntimeError(f"{method} -> {f['error']}")
            return f.get("result")
        if f.get("method") == "event":
            print("   [event]", f["params"].get("type"))

async def main():
    async with __import__("websockets").connect(f"{WS_URL}?token={TOKEN}", max_size=50*1024*1024) as ws:
        t0 = time.time()
        res = await rpc(ws, "session.create", {"title":"warm-probe"})
        sid = res.get("session_id") or res.get("id")
        print(f"session.create ok sid={sid} ({time.time()-t0:.1f}s)")

        # 发第一个 prompt, 一直等到 message.complete 或 error, 记录总时长
        t1 = time.time()
        await ws.send(json.dumps({"jsonrpc":"2.0","id":999,"method":"prompt.submit",
            "params":{"session_id":sid,"text":"Reply with exactly: WARM_OK"}}))
        while True:
            f = json.loads(await asyncio.wait_for(ws.recv(), 180))
            if f.get("id") == 999:
                print(f"prompt ack ({time.time()-t1:.1f}s): {json.dumps(f.get('result'))[:100]}")
                continue
            if f.get("method") != "event": continue
            p = f["params"]; t = p.get("type")
            if t == "message.complete":
                txt = (p.get("payload") or {}).get("text","")
                print(f"COMPLETE ({time.time()-t1:.1f}s) text={txt[:80]!r}")
                return 0
            if t == "error":
                print(f"ERROR ({time.time()-t1:.1f}s): {(p.get('payload') or {}).get('message')}")
                return 1

if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
