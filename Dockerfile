# syntax=docker/dockerfile:1

# Stage 1: build the single-page UI
FROM node:24-alpine AS web
WORKDIR /web

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN VITE_OUT_DIR=/out npm run build

# Stage 2: build the fat jar with the UI inside it
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY --from=web /out ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# Stage 3: small runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/ai-knowledge-assistant-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
