
## Stage 1: Build

# Java 21 JDK 이미지
# Gradle 빌드에 필요한 Java 컴파일 환경 제공
FROM eclipse-temurin:21-jdk AS builder

# 빌드 작업 디렉터리
WORKDIR /build

# Gradle Wrapper 및 프로젝트 빌드 설정 복사
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

# Gradle Wrapper 실행 권한 부여
RUN chmod +x ./gradlew

# 애플리케이션 소스코드 복사
COPY src ./src

# Spring Boot 실행 가능한 JAR 생성
# 프로젝트에서 설정한 파일명: atch.jar
RUN ./gradlew bootJar --no-daemon


## Stage 2: Runtime

# 애플리케이션 실행에는 JDK 전체가 아닌 JRE만 필요
FROM eclipse-temurin:21-jre

# 컨테이너 내부 실행 디렉터리
WORKDIR /app

# 애플리케이션 실행 전용 사용자 및 그룹 생성
# root 권한으로 Spring Boot를 실행하지 않기 위함
RUN groupadd --system atch \
    && useradd --system --gid atch atch

# Build 단계에서 완성된 JAR 파일만 복사
COPY --from=builder /build/build/libs/atch.jar /app/app.jar

# 컨테이너의 실행 사용자를 일반 사용자로 변경
USER atch

# Spring Boot 기본 포트
EXPOSE 8080

# 컨테이너 시작 시 Spring Boot 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]