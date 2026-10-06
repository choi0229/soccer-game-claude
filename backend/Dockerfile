# 빌드 컨텍스트는 저장소 루트 (테스트가 config/ 를 읽는다)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY config config
COPY backend/pom.xml backend/pom.xml
COPY backend/domain/pom.xml backend/domain/pom.xml
COPY backend/simulator/pom.xml backend/simulator/pom.xml
COPY backend/api/pom.xml backend/api/pom.xml
RUN --mount=type=cache,target=/root/.m2 cd backend && mvn -B -q dependency:go-offline -DexcludeArtifactIds=domain || true
COPY backend backend
RUN --mount=type=cache,target=/root/.m2 cd backend && mvn -B -q package

FROM eclipse-temurin:21-jre AS api
WORKDIR /app
COPY --from=build /workspace/backend/api/target/api.jar api.jar
COPY --from=build /workspace/backend/simulator/target/simulator.jar simulator.jar
ENV GAME_CONFIG_DIR=/config
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
