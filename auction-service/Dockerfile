FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY auction-service ./auction-service
RUN ./mvnw package -pl shared,auction-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/auction-service/target/auction-service-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
