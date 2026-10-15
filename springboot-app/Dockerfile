FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/integration-process-bootstrap.jar /app/app.jar
ENV PORT=3000
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
