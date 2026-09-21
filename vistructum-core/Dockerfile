FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY vistructum-api vistructum-api
COPY vistructum-core vistructum-core
COPY vistructum-ui vistructum-ui
RUN mvn -q -B package -DskipTests=false

FROM itzg/minecraft-server:java21

COPY --from=build /build/vistructum-core/target/vistructum-*.jar /data/plugins/
RUN chmod -R a+rwX /data/plugins
