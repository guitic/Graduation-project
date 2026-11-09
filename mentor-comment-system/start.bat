@echo off
REM 一键启动：后端 8090（demo/H2）+ 前端 5173，各自独立窗口
setlocal
cd /d "%~dp0"

REM 关键：宿主机可能注入 SERVER__PORT，会覆盖 application.yml 的 server.port
set SERVER__PORT=8090

echo 释放 8090 / 5173 端口占用…
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":8090 .*LISTENING"') do taskkill /PID %%p /F >nul 2>&1
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":5173 .*LISTENING"') do taskkill /PID %%p /F >nul 2>&1

if not exist "frontend\node_modules" (
  echo 安装前端依赖…
  pushd frontend
  call npm install
  popd
)

echo 启动后端…
start "backend-8090" cmd /k "cd /d %~dp0backend && set SERVER__PORT=8090 && mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=demo"

echo 启动前端…
start "frontend-5173" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo 后端 http://127.0.0.1:8090
echo 前端 http://127.0.0.1:5173   （admin/123456）
endlocal
