# first stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

COPY user-service/pom.xml user-service/pom.xml
COPY shared/pom.xml shared/pom.xml

COPY user-service/src user-service/src
COPY shared/src shared/src

RUN chmod +x mvnw
RUN ./mvnw clean package -pl user-service -am -DskipTests


# second stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/user-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java","-jar","app.jar"]
