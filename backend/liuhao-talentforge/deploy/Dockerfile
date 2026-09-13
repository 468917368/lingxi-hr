# 灵犀互聘 通用 Java 应用镜像（JDK 8）
# 构建：docker compose 里 build.args.JAR_PATH 指定各服务 jar
FROM eclipse-temurin:8-jre

# 时区（默认 UTC，需装 tzdata 让 TZ 生效）
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=256m"

ARG JAR_PATH
COPY ${JAR_PATH} /app/app.jar

WORKDIR /app
EXPOSE 8080

# 内存/JVM 参数在 compose 里用 environment.JAVA_OPTS 覆盖
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
