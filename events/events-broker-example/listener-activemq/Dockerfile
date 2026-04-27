FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-activemq/pom.xml ./listener-activemq/pom.xml
COPY listener-activemq/aot-jar.properties ./listener-activemq/aot-jar.properties
RUN mvn -pl listener-activemq -am dependency:resolve -q
COPY listener-activemq/src ./listener-activemq/src
RUN mvn -pl listener-activemq -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-activemq/target/listener-activemq-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
