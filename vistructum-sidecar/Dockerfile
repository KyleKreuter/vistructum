FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY models models
COPY vistructum-api vistructum-api
COPY vistructum-inference vistructum-inference
COPY vistructum-core vistructum-core
COPY vistructum-sidecar vistructum-sidecar
COPY vistructum-ui vistructum-ui
RUN mvn -q -B -pl vistructum-sidecar -am package

FROM eclipse-temurin:21-jre

ENV MODEL_DIR=/models PORT=8000 ORT_THREADS=1 MODELS_UPDATE=auto MODELS_CHECK_HOURS=24

RUN groupadd --system sidecar && useradd --system --gid sidecar --home-dir /app sidecar \
    && mkdir -p /app /models && chown -R sidecar:sidecar /app /models

COPY --from=build /build/vistructum-sidecar/target/vistructum-sidecar.jar /app/vistructum-sidecar.jar
USER sidecar
WORKDIR /app

VOLUME /models
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
    CMD ["java", "-jar", "/app/vistructum-sidecar.jar", "health"]

CMD ["java", "-jar", "/app/vistructum-sidecar.jar"]
