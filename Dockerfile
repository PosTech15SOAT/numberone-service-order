FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package \
    && ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
       -Dartifact=com.datadoghq:dd-java-agent:1.64.2 -DoutputDirectory=target/agent

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/numberone-service-order-*.jar app.jar
COPY --from=build /app/target/agent/dd-java-agent-1.64.2.jar dd-java-agent.jar
COPY --chmod=755 entrypoint.sh /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
