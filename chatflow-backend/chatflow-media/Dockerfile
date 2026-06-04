# syntax=docker/dockerfile:1
# Build context = reactor root (so the parent pom + contracts module are available).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl chatflow-media -am -DskipTests package

# ffmpeg is needed for video frame-grab thumbnails.
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/chatflow-media/target/chatflow-media.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
