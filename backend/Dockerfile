# -----------------------------
# Stage 1: Build the application
# -----------------------------

# Use Eclipse Temurin JDK 21 on Alpine Linux as the build environment.
# The JDK is required to compile and package the Java application.
FROM eclipse-temurin:21-jdk-alpine AS build

# Set the working directory inside the build container.
WORKDIR /workspace

# Copy the Maven Wrapper configuration files.
COPY .mvn .mvn

# Copy the Maven Wrapper script and the Maven project configuration file.
# These files are copied separately to make better use of Docker layer caching.
COPY mvnw pom.xml ./

# Copy the application source code.
COPY src src

# Build the application and generate the executable JAR file.
# -B enables Maven batch mode.
# -DskipTests skips running tests during the build.
RUN ./mvnw -B package -DskipTests


# -----------------------------
# Stage 2: Run the application
# -----------------------------

# Use the smaller JRE image for the runtime environment.
# The JDK is not needed because the application has already been compiled.
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user and group named "app".
# Running the application as a non-root user improves container security.
RUN addgroup -S app && adduser -S app -G app

# Set the working directory for the running application.
WORKDIR /app

# Copy the generated JAR file from the build stage.
# Rename it to app.jar inside the runtime container.
COPY --from=build /workspace/target/booking-engine-*.jar app.jar

# Run the application using the non-root user.
USER app

# Document that the application listens on port 8080.
EXPOSE 8080

# Start the Spring Boot application when the container launches.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
