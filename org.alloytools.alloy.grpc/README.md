# Alloy gRPC Service

A gRPC service for Alloy 6 that exposes model solving capabilities through a well-defined API, integrating with the existing Alloy codebase.

## Features

- Modern Protocol Buffers-based API
- Multiple output formats (JSON, XML, Text, Table)
- Support for SAT4J and other solvers when available
- Health check endpoints and proper error handling

## Installation

### Prerequisites
- Java 17+
- Gradle 7.2+ (via included wrapper)
- grpcurl (optional, for testing)
- Docker (optional, for containerized deployment)

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
  // Single-file mode (backward compatible)
  string model_content = 1;        // Alloy model source code
  
  // Multi-file mode (NEW)
  repeated AlloyFile files = 6;    // Multiple Alloy files
  string main_file = 7;            // Entry point file
  
  // Common parameters
  OutputFormat output_format = 2;  // JSON, XML, TEXT, TABLE
  SolverType solver_type = 3;      // SAT4J, MINISAT, GLUCOSE, etc.
  SolverOptions solver_options = 4; // Optional solver configuration
  string command = 5;              // Optional command to execute
}

message AlloyFile {
  string filename = 1;             // File path (e.g., "util.als", "models/core.als")
  string content = 2;              // Complete file content
}
```

#### Multi-File Mode Features

- **Cross-file imports**: Use `open` directive to import other modules
- **Parameterized modules**: Support for generic modules with type parameters  
- **Directory structure**: Preserve relative paths in filenames
- **Module aliases**: Support for `open module as alias` syntax
- **Backward compatibility**: Single-file mode continues to work unchanged

### Single-File Examples

#### Using grpcurl

```bash
# Simple model solving
grpcurl -plaintext -d '{
  "model_content": "sig Person {} run {} for 3",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
```

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

### Multi-File Examples

#### Basic Import

```bash
grpcurl -plaintext -d '{
  "files": [
    {
      "filename": "util.als",
      "content": "module util\nsig Util {}\npred hasUtil { some Util }"
    },
    {
      "filename": "main.als", 
      "content": "module main\nopen util\nrun { hasUtil } for 3"
    }
  ],
  "main_file": "main.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
```

#### Parameterized Modules

```bash
grpcurl -plaintext -d '{
  "files": [
    {
      "filename": "library.als",
      "content": "module library[T]\nsig Container { items: set T }\npred hasItems[c: Container] { some c.items }"
    },
    {
      "filename": "application.als",
      "content": "module application\nopen library[String] as lib\nsig String {}\nrun { some c: lib/Container | lib/hasItems[c] } for 3"
    }
  ],
  "main_file": "application.als",
  "output_format": "OUTPUT_FORMAT_TEXT",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
```

#### Nested Directory Structure

```bash
grpcurl -plaintext -d '{
  "files": [
    {
      "filename": "util/base.als",
      "content": "module util/base\nsig Base {}"
    },
    {
      "filename": "util/derived.als", 
      "content": "module util/derived\nopen util/base\nsig Derived extends Base {}"
    },
    {
      "filename": "main.als",
      "content": "module main\nopen util/derived\nrun { some Derived } for 3"
    }
  ],
  "main_file": "main.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
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

# Start the server and run comprehensive tests
./org.alloytools.alloy.grpc/hacks/test-server.sh

# Run multi-file specific tests
./org.alloytools.alloy.grpc/hacks/test-multifile-examples.sh
```

## Deployment

### Docker

The Dockerfile uses a multi-stage build. The builder stage compiles only the gRPC
module (it uses pre-built JARs from `lib/`, not sibling module sources). The
runtime stage uses an Eclipse Temurin JRE image.

```bash
# Build image (run from the repo root)
docker build -f org.alloytools.alloy.grpc/Dockerfile -t alloy-grpc:latest .

# Run container
docker run -p 50051:50051 alloy-grpc:latest

# Run with custom port and JVM settings
docker run -p 8080:8080 \
  -e GRPC_PORT=8080 \
  -e JAVA_OPTS="-Xmx4g -Xms1g" \
  alloy-grpc:latest
```

### Google Cloud Build + Cloud Run

```bash
gcloud builds submit --config=org.alloytools.alloy.grpc/cloudbuild.yaml .
```

This builds the Docker image, pushes it to Artifact Registry, and deploys to
Cloud Run. See [cloudbuild.yaml](cloudbuild.yaml) for the full pipeline.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xmx2g -Xms512m` | JVM options |
| `GRPC_PORT` | `50051` | gRPC server port |

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
  - `test-server.sh` - Script to test server functionality including multi-file tests
  - `test-multifile-examples.sh` - Comprehensive multi-file scenario tests

- **`Dockerfile`** - Docker configuration for containerized deployment

## Troubleshooting

### Server Issues
- **Port already in use**: Change the port with `--args="8080"` or check if another instance is running
- **Build failures**: Run `./gradlew clean :org.alloytools.alloy.grpc:build` to rebuild from scratch
- **Java version**: Ensure Java 11+ is installed and `JAVA_HOME` is set correctly

### Multi-File Import Issues
- **Module not found**: Ensure the filename in `files` array exactly matches the `open` directive
- **Case sensitivity**: Filenames are case-sensitive, verify exact spelling
- **Main file not found**: The `main_file` must exactly match one of the filenames in the `files` array
- **Circular imports**: Check for circular dependencies between modules
- **Module name mismatch**: Module declaration should match the expected path structure

### Example Debugging

#### Wrong filename case
```bash
# ❌ This will fail
{
  "files": [{"filename": "Util.als", "content": "module util\n..."}],
  "main_file": "Util.als"
}

# ✅ This works
{
  "files": [{"filename": "util.als", "content": "module util\n..."}],
  "main_file": "util.als"
}
```

#### Missing main file
```bash
# ❌ This will fail - main_file not in files array
{
  "files": [{"filename": "util.als", "content": "..."}],
  "main_file": "main.als"
}

# ✅ This works
{
  "files": [
    {"filename": "util.als", "content": "..."},
    {"filename": "main.als", "content": "..."}
  ],
  "main_file": "main.als"
}
```

### Performance Considerations
- **Large models**: Consider increasing JVM heap size with `JAVA_OPTS=-Xmx4g`
- **Many files**: File processing is optimized but very large file sets may need tuning
- **Temporary files**: The server creates temporary files for multi-file models - ensure adequate disk space

### Common Error Messages
- `"main_file must be specified when using multi-file mode"`: Set the `main_file` field
- `"main_file 'X' not found in provided files"`: Add the main file to the `files` array
- `"Cannot specify both model_content and files"`: Use either single-file or multi-file mode, not both
- `"All files must have non-empty filenames"`: Check that every file in the `files` array has a filename