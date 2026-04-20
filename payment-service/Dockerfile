FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY payment-service ./payment-service
COPY user-service/pom.xml ./user-service/pom.xml
COPY shop-service/pom.xml ./shop-service/pom.xml
COPY bid-service/pom.xml ./bid-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY auction-service/pom.xml ./auction-service/pom.xml
COPY scripts/init-tables/pom.xml ./scripts/init-tables/pom.xml
RUN ./mvnw package -pl shared,payment-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app.jar"]
