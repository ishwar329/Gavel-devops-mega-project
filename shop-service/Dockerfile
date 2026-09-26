# first stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

COPY shop-service/pom.xml shop-service/pom.xml
COPY shared/pom.xml shared/pom.xml

COPY shop-service/src shop-service/src
COPY shared/src shared/src

RUN chmod +x mvnw
RUN ./mvnw clean package -pl shop-service -am -DskipTests


# second stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/shop-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java","-jar","app.jar"]

