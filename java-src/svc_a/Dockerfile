# Use the official OpenJDK base image
FROM openjdk:17-jdk-slim

# Create a temporary volume
VOLUME /tmp

# Copy the built JAR file into the container
COPY target/svc_a-0.0.1-SNAPSHOT.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

