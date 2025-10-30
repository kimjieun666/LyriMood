FROM gradle:8.9.0-jdk21 AS build
WORKDIR /workspace

COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle gradle
COPY src src

RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=default

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
