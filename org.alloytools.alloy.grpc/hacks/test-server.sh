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
echo -e "${BLUE}7. 📁 Multi-File Model Test (Basic):${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
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
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Multi-file basic test passed${NC}"
else
    echo -e "${RED}❌ Multi-file basic test failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}8. 📁 Multi-File Model Test (Parameterized):${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "library.als", 
      "content": "module library[T]\nsig Container { items: set T }\npred hasItems[c: Container] { some c.items }"
    },
    {
      "filename": "application.als", 
      "content": "module application\nopen library[Int] as lib\nrun { some c: lib/Container | lib/hasItems[c] } for 3"
    }
  ],
  "main_file": "application.als",
  "output_format": "OUTPUT_FORMAT_TEXT",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Multi-file parameterized test passed${NC}"
else
    echo -e "${RED}❌ Multi-file parameterized test failed${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}9. ❌ Multi-File Error Test (Missing Main File):${NC}"
RESPONSE=$("${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "util.als", 
      "content": "sig Util {}"
    }
  ],
  "main_file": "missing.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve 2>&1)
if echo "$RESPONSE" | grep -q "error_message"; then
    echo -e "${GREEN}✅ Multi-file error handling test passed${NC}"
else
    echo -e "${RED}❌ Multi-file error handling test failed${NC}"
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
echo "  ✅ Multi-File Basic Model"
echo "  ✅ Multi-File Parameterized Model"
echo "  ✅ Multi-File Error Handling"
echo ""
echo -e "${BLUE}🚀 Your Alloy gRPC Server is working perfectly!${NC}"
