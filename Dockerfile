
FROM maven:3.10-eclipse-temurin-27 AS build

WORKDIR /workspace

COPY pom.xml .
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY backend/pom.xml backend/pom.xml

COPY domain/src domain/src
COPY application/src application/src
COPY backend/src backend/src

RUN mvn -B -DskipTests install \
    && mvn -B -f backend/pom.xml \
       org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies \
       -DincludeScope=runtime

FROM eclipse-temurin:27-jre-ubi10-minimal

WORKDIR /app

COPY --from=build \
    /workspace/backend/target/control-presencia-backend-0.1.0-SNAPSHOT.jar \
    /app/app.jar

COPY --from=build \
    /workspace/backend/target/dependency/ \
    /app/lib/

ENV PORT=7070
ENV DATA_DIR=/data/events

EXPOSE 7070

ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "software.ulpgc.register.infrastructure.configuration.ServerMain"]
