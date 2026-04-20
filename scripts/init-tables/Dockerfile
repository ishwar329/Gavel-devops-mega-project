FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY scripts/init-tables ./scripts/init-tables
COPY user-service/pom.xml ./user-service/pom.xml
COPY shop-service/pom.xml ./shop-service/pom.xml
COPY bid-service/pom.xml ./bid-service/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY auction-service/pom.xml ./auction-service/pom.xml
RUN ./mvnw package -pl scripts/init-tables -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/scripts/init-tables/target/init-tables-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
