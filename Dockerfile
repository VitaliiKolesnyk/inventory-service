FROM openjdk:17-jdk-slim

EXPOSE 8082

ADD target/inventory-service-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]