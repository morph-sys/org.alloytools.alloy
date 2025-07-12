# Alloy gRPC Service

A gRPC service for Alloy 6 that exposes model solving capabilities through a well-defined API, integrating with the existing Alloy codebase.

## Features

- Modern Protocol Buffers-based API
- Multiple output formats (JSON, XML, Text, Table)
- Support for SAT4J and other solvers when available
- Health check endpoints and proper error handling

## Installation

### Prerequisites
- Java 11+
- Gradle 7.0+
- grpcurl (optional, for testing)

### Building
```bash
# Build the module
./gradlew :org.alloytools.alloy.grpc:build
```

## Usage

### Starting the Server

```bash
# Default port (50051)
./gradlew :org.alloytools.alloy.grpc:run

# Custom port
./gradlew :org.alloytools.alloy.grpc:run --args="8080"

# Using the provided script
./org.alloytools.alloy.grpc/hacks/start-server.sh [port]
```

### API Overview

The service provides two main endpoints:

1. **Solve**: Process Alloy models and return solutions
2. **Ping**: Health check and server information

#### Key Request Parameters

```protobuf
message SolveRequest {
  string model_content = 1;        // Alloy model source code
  OutputFormat output_format = 2;  // JSON, XML, TEXT, TABLE
  SolverType solver_type = 3;      // SAT4J, MINISAT, GLUCOSE, etc.
  SolverOptions solver_options = 4; // Optional solver configuration
  string command = 5;              // Optional command to execute
}
```

### Client Examples

#### Python

```python
import grpc
from alloy_solver_pb2 import SolveRequest, OutputFormat, SolverType
from alloy_solver_pb2_grpc import SolverServiceStub

channel = grpc.insecure_channel('localhost:50051')
stub = SolverServiceStub(channel)

request = SolveRequest(
    model_content="sig Person {} run {} for 3",
    output_format=OutputFormat.OUTPUT_FORMAT_JSON,
    solver_type=SolverType.SOLVER_TYPE_SAT4J
)

response = stub.Solve(request)
print(f"Satisfiable: {response.satisfiable}")
print(f"Solution: {response.solution_data}")
```

## Testing

```bash
# Run all tests
./gradlew :org.alloytools.alloy.grpc:test

# Test liveness with grpcurl
grpcurl -plaintext -d '{"message": "test"}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Ping
grpcurl -plaintext localhost:50051 grpc.health.v1.Health/Check

# Simple Model Solving
grpcurl -plaintext -d '{
  "model_content": "sig Person {}\nrun {} for 3",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve

# Model data from file
grpcurl -plaintext -d "$(jq -n \
  --arg mc "$(cat yourfile.als)" \
  '{"model_content": $mc, "output_format": "OUTPUT_FORMAT_JSON", "solver_type": "SOLVER_TYPE_SAT4J"}')" \
  localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve

# Start the server and then run the test script
./org.alloytools.alloy.grpc/hacks/test-server.sh
```

## Deployment

### Docker

```bash
# Build image
docker build -f org.alloytools.alloy.grpc/Dockerfile -t alloy-grpc:latest .

# Run container
docker run -p 50051:50051 alloy-grpc:latest
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xmx2g -Xms512m` | JVM options |
| `GRPC_PORT` | `50051` | gRPC server port |

### JVM Tuning

For production workloads, adjust JVM settings based on your requirements:

```bash
# For high-throughput scenarios
JAVA_OPTS="-Xmx8g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# For low-latency scenarios
JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseZGC"

# For memory-constrained environments
JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseSerialGC"
```

## Project Structure

The Alloy gRPC service module is organized into the following directory structure:

### Source Code Organization

- **`src/main/java/`** - Java source code
  - **`org.alloytools.alloy.grpc.api`** - Public API interfaces and server implementation
    - `AlloyGrpcServer.java` - Main server class that configures and starts the gRPC server
  - **`org.alloytools.alloy.grpc.impl`** - Service implementation classes
    - `AlloySolverServiceImpl.java` - Implementation of the SolverService interface
  - **`org.alloytools.alloy.grpc.service`** - Core service logic
    - `ModelLoader.java` - Utilities for loading and parsing Alloy models
  - **`org.alloytools.alloy.grpc.util`** - Utility classes
    - `ProtocolBufferConverter.java` - Converts between Alloy and Protocol Buffer types

- **`src/main/proto/`** - Protocol Buffer definitions
  - `alloy_solver.proto` - Main service and message definitions

- **`src/test/java/`** - Test source code
  - **`org.alloytools.alloy.grpc.integration`** - Integration tests
    - `BasicSolvingIntegrationTest.java` - End-to-end tests for model solving
    - `OutputFormatIntegrationTest.java` - Tests for different output formats
  - **`org.alloytools.alloy.grpc.error`** - Error handling tests
    - `GrpcErrorHandlingTest.java` - Tests for proper gRPC error responses
  - **`org.alloytools.alloy.grpc.model`** - Model loading tests
    - `ModelLoaderTest.java` - Tests for model parsing and validation
  - **`org.alloytools.alloy.grpc.conversion`** - Conversion tests
    - `ProtocolBufferConverterTest.java` - Tests for Protocol Buffer conversion

- **`test-data/`** - Additional test data
  - `valid-models/` - Valid Alloy models for testing
  - `invalid-models/` - Invalid models for error testing
  - `valid-syntax-check-fails/` - Models with valid syntax but unsatisfiable constraints

### Scripts and Configuration

- **`hacks/`** - Utility scripts
  - `start-server.sh` - Script to start the server
  - `test-server.sh` - Script to test server functionality

- **`Dockerfile`** - Docker configuration for containerized deployment