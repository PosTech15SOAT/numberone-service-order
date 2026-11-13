FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
COPY doc/contracts/saga doc/contracts/saga
RUN chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699 AS runtime
WORKDIR /app
ADD --checksum=sha256:bdd2f818138b683d0c266637fbda4bccf01e940d55bdeb2ea43aaa6d43218ddf https://repo.maven.apache.org/maven2/com/datadoghq/dd-java-agent/1.64.2/dd-java-agent-1.64.2.jar /app/dd-java-agent.jar
RUN chmod 0444 /app/dd-java-agent.jar
COPY --chmod=755 entrypoint.sh /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]

FROM runtime AS verified
COPY target/numberone-service-order-*.jar app.jar

FROM runtime AS local
COPY --from=build /app/target/numberone-service-order-*.jar app.jar
