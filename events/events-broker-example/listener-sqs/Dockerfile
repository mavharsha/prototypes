FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-sqs/pom.xml ./listener-sqs/pom.xml
COPY listener-sqs/aot-jar.properties ./listener-sqs/aot-jar.properties
RUN mvn -pl listener-sqs -am dependency:resolve -q
COPY listener-sqs/src ./listener-sqs/src
RUN mvn -pl listener-sqs -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-sqs/target/listener-sqs-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
