# Backend image: build with Maven, run on a JRE (glibc base, required by ONNX Runtime's native lib).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 lookit && mkdir -p /app/data/index /app/data/models && chown -R lookit /app
COPY --from=build /src/target/search-engine-*.jar app.jar
COPY data/documents ./data/documents
USER lookit
ENV LOOKIT_INDEX_DIR=/app/data/index \
    LOOKIT_MODEL_DIR=/app/data/models/all-MiniLM-L6-v2 \
    LOOKIT_SEED_DIR=/app/data/beir/scifact
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
