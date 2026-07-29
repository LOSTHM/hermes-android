#!/data/data/com.termux/files/usr/bin/sh
# 开发期 serve 启动脚本：固定 token 到 ~/.hermes/app_token (600)
TOKEN_FILE="$HOME/.hermes/app_token"
if [ ! -f "$TOKEN_FILE" ]; then
  head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
fi
export HERMES_DASHBOARD_SESSION_TOKEN="$(cat "$TOKEN_FILE")"
exec hermes serve --port 9119 --host 127.0.0.1 --skip-build
