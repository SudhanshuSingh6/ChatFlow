# syntax=docker/dockerfile:1
# Build context = reactor root (so the parent pom + contracts module are available).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl chatflow-ai -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/chatflow-ai/target/chatflow-ai.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
