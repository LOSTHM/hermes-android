#!/usr/bin/env python3
"""验证关键假设: 第一次 prompt 超时后, agent build 是否继续? 第二次是否 warm?"""
import asyncio, json, os, sys, time
WS_URL = "ws://127.0.0.1:9119/api/ws"
TOKEN = open(os.path.expanduser("~/.hermes/app_token")).read().strip()

async def rpc(ws, method, params=None, _id=[0]):
    _id[0] += 1; mid = _id[0]
    await ws.send(json.dumps({"jsonrpc":"2.0","id":mid,"method":method,"params":params or {}}))
    while True:
        f = json.loads(await asyncio.wait_for(ws.recv(), 200))
        if f.get("id") == mid:
            if "error" in f: raise RuntimeError(f["error"])
            return f.get("result")
        if f.get("method") == "event": pass

async def send_and_wait(ws, sid, text, tag, budget=200):
    t0 = time.time()
    await ws.send(json.dumps({"jsonrpc":"2.0","id":hash(tag)%10000+1000,"method":"prompt.submit",
        "params":{"session_id":sid,"text":text}}))
    rid = hash(tag)%10000+1000
    while time.time()-t0 < budget:
        f = json.loads(await asyncio.wait_for(ws.recv(), budget))
        if f.get("id") == rid: continue
        if f.get("method") != "event": continue
        p=f["params"]; t=p.get("type")
        if t=="message.complete":
            return ("complete", time.time()-t0, (p.get("payload") or {}).get("text","")[:60])
        if t=="error":
            return ("error", time.time()-t0, (p.get("payload") or {}).get("message"))
    return ("timeout", budget, "")

async def main():
    async with __import__("websockets").connect(f"{WS_URL}?token={TOKEN}", max_size=50*1024*1024) as ws:
        res = await rpc(ws, "session.create", {"title":"warm-retry"})
        sid = res.get("session_id") or res.get("id")
        print(f"sid={sid}")
        r1 = await send_and_wait(ws, sid, "Reply with exactly: PING1", "first")
        print(f"attempt1: {r1[0]} in {r1[1]:.1f}s  {r1[2]!r}")
        if r1[0] == "error":
            print("-> 第一次超时符合预期, 等 build 完成后重试...")
            await asyncio.sleep(45)  # 给 build 时间
            r2 = await send_and_wait(ws, sid, "Reply with exactly: PING2", "second")
            print(f"attempt2: {r2[0]} in {r2[1]:.1f}s  {r2[2]!r}")
            return 0 if r2[0]=="complete" else 1
        return 0

if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
