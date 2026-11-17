# 毕业设计《基于 Web 的学业导师智能点评分析系统》Abasthan 部署文件
#
# 使用方法：把本文件复制到仓库根目录，文件名为 Dockerfile（不要改名），
# 连同仓库一起提交到 GitHub，然后在 Abasthan 里新建 Web Service 时选这个仓库。
# 不要修改仓库里其他文件；本文件只新增，不改动原有代码。
#
# 部署形态（单服务，适配 Abasthan 免费档“一个免费应用”）：
#   Vue 3 前端先打包成静态文件，放进 Spring Boot 的 static 目录，
#   由后端同一个进程同源提供页面 + /api 接口。前端 axios 用的是相对路径
#   baseURL '/api'，同源部署不需要改任何前端代码，也没有跨域问题。
#   前端用 hash 路由（createWebHashHistory），不需要后端做 SPA fallback。
#
# 数据库：demo profile + H2 内存库，零依赖，不用 MySQL。
#   每次启动自动建表并灌入种子数据；重启后数据重置，只适合答辩演示。
#   演示账号：admin / 123456（教务）、teacher01（导师）、student01（学生）。
#
# 内存：免费档只有 512MB，JVM 堆上限设为 256m，启动参数见 ENTRYPOINT。
#   并发一高仍可能内存不足；如果出现 OOM，只能升到 2GB 的 Standard 档。

# ---------- 第 1 阶段：构建前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /build/frontend
COPY mentor-comment-system/frontend/package.json mentor-comment-system/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY mentor-comment-system/frontend/ ./
RUN npm run build

# ---------- 第 2 阶段：构建后端 ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
# 注意：不要用仓库自带的 mentor-comment-system/maven-settings.xml，
# 它把 localRepository 写死成 Windows 路径 C:/Users/11721/.m2/repository，
# 在 Linux 构建机上不能用。这里只配阿里云镜像（作者注明直连 Maven Central 会超时）。
RUN mkdir -p /root/.m2 && printf '%s\n' \
  '<settings>' \
  '  <mirrors><mirror><id>aliyun-public</id>' \
  '  <url>https://maven.aliyun.com/repository/public</url>' \
  '  <mirrorOf>*</mirrorOf></mirror></mirrors>' \
  '</settings>' > /root/.m2/settings.xml
COPY mentor-comment-system/backend ./backend
# 前端 dist 拷进后端静态资源目录，Spring Boot 默认提供 classpath:/static/
COPY --from=frontend /build/frontend/dist ./backend/src/main/resources/static
WORKDIR /build/backend
RUN mvn -B clean package -DskipTests

# ---------- 第 3 阶段：运行 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /build/backend/target/mentor-comment-backend-1.0.0.jar app.jar
EXPOSE 8090
# 端口：Abasthan 注入 PORT 环境变量就用它，没注入默认 8090。
# README 提到有些环境会注入 SERVER__PORT 并被 Spring 当成 server.port，
# 这里先 unset 掉，再用命令行参数 --server.port 显式指定（命令行优先级最高）。
ENTRYPOINT ["sh", "-c", "PORT=${PORT:-8090}; unset SERVER__PORT; exec java -Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8 -jar app.jar --spring.profiles.active=demo --server.port=$PORT"]
