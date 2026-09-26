# first stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

COPY payment-service/pom.xml payment-service/pom.xml
COPY shared/pom.xml shared/pom.xml

COPY payment-service/src payment-service/src
COPY shared/src shared/src

RUN chmod +x mvnw
RUN ./mvnw clean package -pl payment-service -am -DskipTests


# second stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/payment-service/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java","-jar","app.jar"]
