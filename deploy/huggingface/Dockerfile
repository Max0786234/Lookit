# Lookit as a single container for Hugging Face Spaces (or any host that runs one Docker image).
# Builds from the public GitHub repository at a pinned commit, bundles the React UI into the Spring
# Boot jar, and prepares the embedding model, the SciFact starter corpus and its embeddings at build
# time so the Space is searchable as soon as it starts.

FROM alpine:3.20 AS source
ARG REPO=https://github.com/Max0786234/Lookit.git
ARG COMMIT=5377e0bccf2b75d1dcde03ac5d7dd55955869bab
RUN apk add --no-cache git \
 && git init /src && cd /src \
 && git fetch --depth 1 "$REPO" "$COMMIT" && git checkout FETCH_HEAD

FROM node:22-alpine AS web
WORKDIR /web
COPY --from=source /src/frontend/package.json /src/frontend/package-lock.json ./
RUN npm ci
COPY --from=source /src/frontend ./
# same origin: the UI calls the API on the host that served it
ENV VITE_API_URL=/api
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY --from=source /src ./
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q -DskipTests package && cp target/search-engine-*.jar /app.jar

FROM build AS data
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /work
RUN sh /src/scripts/fetch-model.sh && mkdir -p data && mv /src/data/models data/models \
 && sh /src/scripts/fetch-beir.sh scifact && mv /src/data/beir data/beir
# Encode the corpus once (the evaluation runner caches embeddings in data/beir/scifact/minilm.vec).
RUN java -Dloader.main=com.searchengine.eval.EvalRunner -cp /app.jar \
        org.springframework.boot.loader.launch.PropertiesLauncher data/beir/scifact - dense-exact \
 && test -s data/beir/scifact/minilm.vec

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
COPY --from=source /src/data/documents data/documents
COPY --from=data /work/data/models data/models
COPY --from=data /work/data/beir/scifact data/beir/scifact
# Spaces run containers as UID 1000 (the base image's existing "ubuntu" user)
RUN chown -R 1000:1000 /app
USER 1000
ENV SERVER_PORT=7860 \
    LOOKIT_INDEX_DIR=/tmp/lookit-index \
    LOOKIT_MODEL_DIR=/app/data/models/all-MiniLM-L6-v2 \
    LOOKIT_SEED_DIR=/app/data/beir/scifact \
    LOOKIT_CORS_ORIGINS="https://*.hf.space,https://huggingface.co,http://localhost:*,http://127.0.0.1:*"
EXPOSE 7860
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
