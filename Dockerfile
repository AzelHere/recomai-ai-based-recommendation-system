FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend backend
COPY frontend frontend
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/pom.xml clean verify

FROM eclipse-temurin:21-jre@sha256:d77e090ae1f6970c047b443c6f0a6c1358b0426d44432bb5a7753408cd43de0d
WORKDIR /app
ENV SERVER_PORT=8080
ENV SESSION_COOKIE_SECURE=true
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl perl-base \
    && rm -f /usr/bin/pebble \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build /workspace/backend/target/ai-based-recommendation-system-0.1.0-SNAPSHOT.jar /app/application.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
