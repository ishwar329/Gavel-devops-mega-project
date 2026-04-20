FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY notification-service ./notification-service
RUN ./mvnw package -pl shared,notification-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
