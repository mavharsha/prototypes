FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-kafka/pom.xml ./listener-kafka/pom.xml
COPY listener-kafka/aot-jar.properties ./listener-kafka/aot-jar.properties
RUN mvn -pl listener-kafka -am dependency:resolve -q
COPY listener-kafka/src ./listener-kafka/src
RUN mvn -pl listener-kafka -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-kafka/target/listener-kafka-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
