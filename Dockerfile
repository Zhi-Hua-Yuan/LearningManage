# 1. 指定基础环境：
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-alpine
FROM ${RUNTIME_IMAGE}

# 2. 指定工作目录：进入这个微型电脑的 /app 文件夹
WORKDIR /app

# 3. 拷贝产物：把你本地 target 目录下打好的 jar 包，复制到微型电脑里，并改名叫 app.jar
RUN addgroup -S app && adduser -S -G app app
COPY --chown=app:app target/LearningManage-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

# 4. 声明端口：告诉 Docker，这个集装箱内部会使用 8123 端口
EXPOSE 8123 9123

# 5. 启动命令：当集装箱启动时，执行 java -jar app.jar，并强制激活 prod 生产环境配置
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
