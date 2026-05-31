# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the pre-built jar from the target directory relative to the build context (./backend/ecommerce-api-gateway)
COPY target/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE docker

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
