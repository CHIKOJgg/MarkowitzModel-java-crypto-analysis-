# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends \
    libx11-6 libxext6 libxrender1 libxrandr2 libxtst6 libxi6 \
    libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r app && useradd -r -g app -m -d /app app
WORKDIR /app
COPY --from=build /app/target/*-shaded.jar app.jar

# config.properties is mounted at runtime via docker-compose volume
EXPOSE 8080

USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
