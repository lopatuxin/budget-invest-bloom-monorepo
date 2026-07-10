# syntax=docker/dockerfile:1

# ============================================================================
# Stage 1 — build the single bootJar from the multi-module monorepo.
# Build context is the repo root; only the modules that make up the monolith
# are copied. Layers are ordered least-changed → most-changed for cache hits.
# ============================================================================
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /workspace

# Gradle wrapper + root build scripts first (rarely change).
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./

# Module build scripts (dependency graph — changes less often than sources).
COPY shared/build.gradle.kts shared/
COPY auth/build.gradle.kts auth/
COPY budget/build.gradle.kts budget/
COPY investment/build.gradle.kts investment/
COPY security/build.gradle.kts security/
COPY app/build.gradle.kts app/
RUN chmod +x gradlew && ./gradlew :app:dependencies --no-daemon || true

# Sources last (change most often).
COPY shared/src shared/src
COPY auth/src auth/src
COPY budget/src budget/src
COPY investment/src investment/src
COPY security/src security/src
COPY app/src app/src
RUN ./gradlew :app:bootJar --no-daemon -x test \
    && cp app/build/libs/app.jar /workspace/app.jar

# ============================================================================
# Stage 2 — slim JRE runtime. One JVM for the whole monolith, tuned for a low
# RAM/CPU footprint (single user): SerialGC, C1-only JIT and a heap capped under
# the container memory limit. AppCDS: a shared class archive is auto-created on
# first graceful shutdown and reused by later starts (no DB is available at
# image-build time, so a build-stage training run is replaced by this runtime
# auto-archive, which yields the same class-sharing benefit).
# ============================================================================
FROM eclipse-temurin:24-jre
WORKDIR /app

# curl is used by the container healthcheck; drop apt lists to keep the image small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user; /app must be writable so the JVM can create the CDS archive.
RUN groupadd -r app && useradd -r -g app app && chown app:app /app
USER app

COPY --from=builder --chown=app:app /workspace/app.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=65.0", \
  "-XX:+UseSerialGC", \
  "-XX:TieredStopAtLevel=1", \
  "-XX:+AutoCreateSharedArchive", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-jar", "app.jar"]
