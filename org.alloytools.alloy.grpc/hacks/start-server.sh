#!/bin/bash

# Alloy gRPC Server Startup Script
# Usage: ./start-server.sh [port]
# Default port: 50051

set -e

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default port
PORT=${1:-50051}

echo "🚀 Starting Alloy gRPC Server..."
echo "📍 Project root: $PROJECT_ROOT"
echo "🔌 Port: $PORT"
echo ""

# Change to project root directory
cd "$PROJECT_ROOT"

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: gradlew not found in $PROJECT_ROOT"
    echo "   Make sure you're running this script from the correct directory"
    exit 1
fi

# Build the project first
echo "🔨 Building project..."
./gradlew :org.alloytools.alloy.grpc:build -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"
echo ""
echo "🌟 Starting Alloy gRPC Server on port $PORT..."
echo "   Press Ctrl+C to stop the server"
echo "   Server will be available at: localhost:$PORT"
echo ""

# Start the server
./gradlew :org.alloytools.alloy.grpc:run --args="$PORT" --console=plain
