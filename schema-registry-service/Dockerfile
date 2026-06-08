# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# copy wrapper + pom first so dependencies are cached across builds
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B

# now copy the source and build the fat jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# run as a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# only the built jar is carried over from the builder
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
