# first stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

COPY scripts/init-tables/pom.xml scripts/init-tables/pom.xml
COPY scripts/init-tables/src scripts/init-tables/src

RUN chmod +x mvnw
RUN ./mvnw clean package -pl scripts/init-tables -am -DskipTests


# second stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/scripts/init-tables/target/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
