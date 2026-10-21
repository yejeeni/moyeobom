# 백엔드 이미지. 서버(Oracle A1, ARM)에서 직접 빌드해도 되도록 arm64를 지원하는 공식 이미지만 쓴다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성을 먼저 받아 두면 소스만 바뀌었을 때 이 층을 다시 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
