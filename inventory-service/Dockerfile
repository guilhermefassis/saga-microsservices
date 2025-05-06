FROM openjdk:17-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew clean build
FROM openjdk:17-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/inventory-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
