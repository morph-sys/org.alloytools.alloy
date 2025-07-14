#!/bin/bash

# Alloy gRPC Server Test Script
# Tests all major endpoints to verify the server is working correctly

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}🧪 Testing Alloy gRPC Server...${NC}"
echo -e "${YELLOW}📍 Server should be running on localhost:50051${NC}"
echo ""

# Check if grpcurl exists in PATH or project root
GRPCURL_CMD=""
if command -v grpcurl &> /dev/null; then
    GRPCURL_CMD="grpcurl"
elif [ -f "$PROJECT_ROOT/grpcurl" ]; then
    GRPCURL_CMD="$PROJECT_ROOT/grpcurl"
else
    echo -e "${RED}❌ grpcurl not found!${NC}"
    echo "Please download grpcurl first:"
    echo "curl -L https://github.com/fullstorydev/grpcurl/releases/download/v1.9.1/grpcurl_1.9.1_linux_x86_64.tar.gz -o grpcurl.tar.gz"
    echo "tar -xzf grpcurl.tar.gz"
    exit 1
fi

cd "$PROJECT_ROOT"

echo -e "${BLUE}1. 🏥 Health Check:${NC}"
"${GRPCURL_CMD}" -plaintext localhost:50051 grpc.health.v1.Health/Check
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Health check passed${NC}"
else
    echo -e "${RED}❌ Health check failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}2. 🏓 Ping Test:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{"message": "Hello from test script!"}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Ping
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Ping test passed${NC}"
else
    echo -e "${RED}❌ Ping test failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}3. 🔍 List Services:${NC}"
"${GRPCURL_CMD}" -plaintext localhost:50051 list
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Service listing passed${NC}"
else
    echo -e "${RED}❌ Service listing failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}4. 🧮 Simple Solve Test (JSON):${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "model_content": "sig Person {}\nrun {} for 3",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Simple solve test passed${NC}"
else
    echo -e "${RED}❌ Simple solve test failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}5. 🧮 Complex Model Test:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "model_content": "sig Person {\n  friends: set Person\n}\nfact {\n  no p: Person | p in p.friends\n}\nrun {} for 3",
  "output_format": "OUTPUT_FORMAT_TEXT",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Complex model test passed${NC}"
else
    echo -e "${RED}❌ Complex model test failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}6. ❌ Error Handling Test:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "model_content": "invalid syntax {{{",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ ! $? -eq 0 ]; then
    echo -e "${GREEN}✅ Error handling test passed${NC}"
else
    echo -e "${RED}❌ Error handling test failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}🎉 All tests completed successfully!${NC}"
echo -e "${YELLOW}📊 Test Summary:${NC}"
echo "  ✅ Health Check"
echo "  ✅ Ping Endpoint"
echo "  ✅ Service Discovery"
echo "  ✅ Simple Model Solving"
echo "  ✅ Complex Model Solving"
echo "  ✅ Error Handling"
echo ""
echo -e "${BLUE}🚀 Your Alloy gRPC Server is working perfectly!${NC}"
