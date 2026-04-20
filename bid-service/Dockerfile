FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY bid-service ./bid-service
RUN ./mvnw package -pl shared,bid-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/bid-service/target/bid-service-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app.jar"]
