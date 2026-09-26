# first stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

COPY notification-service/pom.xml notification-service/pom.xml
COPY shared/pom.xml shared/pom.xml

COPY notification-service/src notification-service/src
COPY shared/src shared/src

RUN chmod +x mvnw
RUN ./mvnw clean package -pl notification-service -am -DskipTests


# second stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/notification-service/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
