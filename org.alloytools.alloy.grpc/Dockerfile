# Multi-stage build for Alloy gRPC Service
FROM openjdk:17-jdk-slim as builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    git \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy gradle wrapper and build files first (for better caching)
COPY gradlew gradlew.bat ./
COPY .gradle-wrapper/ .gradle-wrapper/
COPY build.gradle settings.gradle gradle.properties ./

# Copy source code
COPY cnf/ cnf/
COPY org.alloytools.alloy.core/ org.alloytools.alloy.core/
COPY org.alloytools.alloy.dist/ org.alloytools.alloy.dist/
COPY org.alloytools.alloy.grpc/ org.alloytools.alloy.grpc/
COPY org.alloytools.api/ org.alloytools.api/
COPY org.alloytools.pardinus.core/ org.alloytools.pardinus.core/

# Build the application and create distribution
RUN chmod +x gradlew && ./gradlew :org.alloytools.alloy.grpc:build :org.alloytools.alloy.grpc:distTar -x test

# Production stage
FROM openjdk:17-jdk-slim

# Install runtime dependencies and health check tools
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r alloy && useradd -r -g alloy alloy

# Set working directory
WORKDIR /app

# Extract and copy the distribution
COPY --from=builder /app/org.alloytools.alloy.grpc/build/distributions/org.alloytools.alloy.grpc.tar .
RUN tar -xf org.alloytools.alloy.grpc.tar --strip-components=1 && rm org.alloytools.alloy.grpc.tar

# Copy gRPC health probe
RUN wget -qO/bin/grpc_health_probe \
    https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/v0.4.19/grpc_health_probe-linux-amd64 \
    && chmod +x /bin/grpc_health_probe

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && \
    chown -R alloy:alloy /app

# Switch to non-root user
USER alloy

# Environment variables
ENV JAVA_OPTS="-Xmx2g -Xms512m" \
    GRPC_PORT=50051

# Expose gRPC port
EXPOSE ${GRPC_PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD grpc_health_probe -addr=localhost:${GRPC_PORT} || exit 1

# Default command
CMD ["sh", "-c", "./bin/org.alloytools.alloy.grpc ${GRPC_PORT}"]