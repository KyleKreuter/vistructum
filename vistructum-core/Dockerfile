FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY vistructum-api vistructum-api
COPY vistructum-core vistructum-core
RUN mvn -q -B package -DskipTests=false

FROM itzg/minecraft-server:java21

COPY --from=build /build/vistructum-core/target/vistructum-[0-9]*.jar /data/plugins/
RUN mkdir -p /data/plugins/vistructum && chmod -R a+rwX /data/plugins
