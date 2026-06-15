# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libx11 libxext libxrender libxrandr libxtst 2>/dev/null || true
WORKDIR /app
COPY --from=build /app/target/*-shaded.jar app.jar
COPY config.properties ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
