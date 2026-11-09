#!/usr/bin/env bash
# 一键启动：后端 8090（demo/H2）+ 前端 5173
# 用法：bash start.sh          # 前台日志，Ctrl+C 全部退出
#      bash start.sh -d       # 后台运行，日志写 logs/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PORT=8090
FRONTEND_PORT=5173
# 关键：宿主机可能注入 SERVER__PORT，会覆盖 application.yml 的 server.port，必须显式固定
export SERVER__PORT="$BACKEND_PORT"

free_port() {
  local port=$1
  local pids
  pids=$(netstat -ano 2>/dev/null | grep -E "[:.]$port[[:space:]]" | grep LISTENING | awk '{print $NF}' | sort -u || true)
  for pid in $pids; do
    [ -n "$pid" ] || continue
    echo "端口 $port 被 PID $pid 占用，正在结束…"
    taskkill //PID "$pid" //F >/dev/null 2>&1 || kill -9 "$pid" 2>/dev/null || true
  done
}

free_port "$BACKEND_PORT"
free_port "$FRONTEND_PORT"

if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "前端依赖缺失，先执行 npm install…"
  (cd "$ROOT/frontend" && npm install)
fi

if [ "${1:-}" = "-d" ]; then
  mkdir -p "$ROOT/logs"
  (cd "$ROOT/backend" && nohup mvn -s ../maven-settings.xml spring-boot:run \
    -Dspring-boot.run.profiles=demo >"$ROOT/logs/backend.log" 2>&1 &)
  (cd "$ROOT/frontend" && nohup npm run dev >"$ROOT/logs/frontend.log" 2>&1 &)
  echo "已后台启动。日志：logs/backend.log、logs/frontend.log"
  echo "访问 http://127.0.0.1:$FRONTEND_PORT"
  exit 0
fi

(cd "$ROOT/backend" && mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo) &
BACK_PID=$!
(cd "$ROOT/frontend" && npm run dev) &
FRONT_PID=$!
trap 'kill $BACK_PID $FRONT_PID 2>/dev/null || true' INT TERM EXIT
wait
