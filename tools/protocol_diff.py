#!/usr/bin/env python3
"""协议 diff: 对比当前 hermes 源码的协议面与 App 基线快照。

用法: python3 tools/protocol_diff.py [--update]
  默认: 只报告 新增/移除/不变, 退出码 1 表示有移除(必须处理)。
  --update: 把当前协议面写成新基线 (接入/确认后调用)。
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

HERMES = Path.home() / ".hermes/hermes-agent"
TOOLS = Path(__file__).resolve().parent
RPC_FILE = TOOLS / "rpc_methods.txt"
REST_FILE = TOOLS / "rest_endpoints.txt"
MANIFEST = TOOLS / "protocol_manifest.json"


def grep(pattern: str, path: Path) -> list[str]:
    out = subprocess.run(
        ["grep", "-oE", pattern, str(path)],
        capture_output=True, text=True
    ).stdout
    return [l for l in out.splitlines() if l.strip()]


def current_rpc() -> list[str]:
    raw = grep(r'@method\("[a-z_.]+"\)', HERMES / "tui_gateway/server.py")
    return sorted({r.removeprefix('@method("').removesuffix('")') for r in raw})


def current_rest() -> list[str]:
    raw = grep(r'@app\.(get|post|put|delete|patch)\("[^"]+"', HERMES / "hermes_cli/web_server.py")
    out = set()
    for r in raw:
        verb, _, route = r.partition('("')
        verb = verb.removeprefix("@app.")
        route = route.rstrip('"')
        out.add(f"{verb} {route}")
    return sorted(out)


def hermes_version() -> str:
    try:
        out = subprocess.run(["hermes", "--version"], capture_output=True, text=True, timeout=20).stdout
        m = re.search(r"v?(\d+\.\d+\.\d+)", out)
        return m.group(1) if m else "unknown"
    except Exception:
        return "unknown"


def load_baseline(path: Path) -> list[str]:
    return path.read_text().splitlines() if path.exists() else []


def diff(base: list[str], cur: list[str]) -> tuple[list[str], list[str], list[str]]:
    b, c = set(base), set(cur)
    return sorted(c - b), sorted(b - c), sorted(b & c)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--update", action="store_true")
    args = ap.parse_args()

    cur_rpc, cur_rest = current_rpc(), current_rest()
    ver = hermes_version()
    print(f"hermes version: {ver}")
    print(f"current: {len(cur_rpc)} rpc, {len(cur_rest)} rest")

    if args.update:
        RPC_FILE.write_text("\n".join(cur_rpc) + "\n")
        REST_FILE.write_text("\n".join(cur_rest) + "\n")
        m = json.loads(MANIFEST.read_text())
        m["hermes_version"] = ver
        m["counts"] = {"rpc": len(cur_rpc), "rest": len(cur_rest)}
        MANIFEST.write_text(json.dumps(m, indent=2, ensure_ascii=False) + "\n")
        print("baseline updated.")
        return 0

    base_rpc, base_rest = load_baseline(RPC_FILE), load_baseline(REST_FILE)
    if not base_rpc and not base_rest:
        print("no baseline; run with --update first")
        return 2

    exit_code = 0
    for label, base, cur in [("RPC", base_rpc, cur_rpc), ("REST", base_rest, cur_rest)]:
        added, removed, _same = diff(base, cur)
        print(f"\n== {label} ==")
        print(f"  added (+{len(added)}):")
        for a in added:
            print(f"    + {a}")
        print(f"  removed (-{len(removed)}):")
        for r in removed:
            print(f"    - {r}")
        if removed:
            exit_code = 1

    # 对照 App 已接入清单, 看新增里有没有值得接的, 移除里有没有 App 在用的
    m = json.loads(MANIFEST.read_text())
    supported = set(m.get("app_supported_rpc", [])) | set(m.get("app_supported_rest", []))
    all_removed = set(diff(base_rpc, cur_rpc)[1]) | set(diff(base_rest, cur_rest)[1])
    broken = supported & all_removed
    if broken:
        print("\n!! App 在用但上游已移除 (必须修):")
        for b in sorted(broken):
            print(f"    !! {b}")
        exit_code = 1
    else:
        print("\nApp 已接入的方法无被移除项.")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
