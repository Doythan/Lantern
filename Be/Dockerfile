# 1. 베이스 이미지 지정
FROM openjdk:17-jdk-slim

# 2. 애플리케이션 JAR 복사 (경로는 Gradle 빌드 결과물 위치에 맞춰 조정)
COPY build/libs/*.jar app.jar

# 3. 컨테이너 포트 노출
EXPOSE 8050

# 4. 실행 커맨드
ENTRYPOINT ["java", "-jar", "/app.jar"]
