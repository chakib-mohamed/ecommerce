# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the pre-built jar from the target directory relative to the build context (./backend)
COPY ecommerce-api-gateway/target/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE docker

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
