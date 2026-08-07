FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
COPY fonts fonts
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/jasper-cli.jar /app/jasper-cli.jar
COPY config /app/config
COPY examples /app/examples
ENTRYPOINT ["java", "-jar", "/app/jasper-cli.jar"]
