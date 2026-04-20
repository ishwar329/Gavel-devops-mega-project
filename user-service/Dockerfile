FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY shared ./shared
COPY user-service ./user-service
RUN ./mvnw package -pl shared,user-service -am -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/user-service/target/user-service-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]
