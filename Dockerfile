FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SERVER_PORT=8080
ENV SESSION_COOKIE_SECURE=true
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build /workspace/target/ai-based-recommendation-system-0.1.0-SNAPSHOT.jar /app/application.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
