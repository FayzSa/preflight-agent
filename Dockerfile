FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Cache Maven dependencies first
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="ai-fix"
LABEL org.opencontainers.image.description="AI-Native Code Review CLI"
LABEL org.opencontainers.image.source="https://github.com/fayzsa/preflight-agent"

RUN apk add --no-cache git bash

WORKDIR /workspace

COPY --from=builder /app/target/preflight-agent-1.0.0.jar /usr/local/lib/ai-fix.jar

# Thin wrapper so `ai-fix scan` works as a command
RUN printf '#!/bin/sh\nexec java -jar /usr/local/lib/ai-fix.jar "$@"\n' > /usr/local/bin/ai-fix && \
    chmod +x /usr/local/bin/ai-fix

ENTRYPOINT ["ai-fix"]
CMD ["--help"]
