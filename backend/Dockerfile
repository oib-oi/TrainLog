FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package

FROM eclipse-temurin:25.0.4.1_1-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system app
COPY --from=build /build/target/trainlog.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
