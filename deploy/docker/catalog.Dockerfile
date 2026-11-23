# Build reproducibly from the repository root; dependencies resolved at image build time.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY commerce-service/pom.xml commerce-service/pom.xml
RUN mvn -B -ntp -pl catalog-service -am dependency:go-offline
COPY catalog-service/src catalog-service/src
RUN mvn -B -ntp -pl catalog-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system afterform && useradd --system --gid afterform --home-dir /app afterform
WORKDIR /app
COPY --from=build --chown=afterform:afterform /workspace/catalog-service/target/catalog-service-0.1.0-SNAPSHOT.jar /app/app.jar
USER afterform
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
