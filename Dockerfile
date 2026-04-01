# 1. 指定基础环境：
# 【修复：加上国内镜像代理前缀，防止 JDK 下载超时】
FROM dockerpull.com/library/eclipse-temurin:17-jre-alpine

# 2. 指定工作目录：进入这个微型电脑的 /app 文件夹
WORKDIR /app

# 3. 拷贝产物：把你本地 target 目录下打好的 jar 包，复制到微型电脑里，并改名叫 app.jar
COPY target/*.jar app.jar

# 4. 声明端口：告诉 Docker，这个集装箱内部会使用 8123 端口
EXPOSE 8123

# 5. 启动命令：当集装箱启动时，执行 java -jar app.jar，并强制激活 prod 生产环境配置
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]