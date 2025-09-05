#!/bin/bash

# Comprehensive Multi-File Alloy gRPC Server Test Script
# Tests complex multi-file scenarios including nested imports and real-world examples

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}🧪 Testing Alloy Multi-File gRPC Server...${NC}"
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

echo -e "${PURPLE}1. 📁 Address Book Example (2 Files):${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "names.als",
      "content": "module names\n\nsig Name {}\nsig Addr {}\n\npred validName[n: Name] { some n }\npred validAddr[a: Addr] { some a }"
    },
    {
      "filename": "addressbook.als",
      "content": "module addressbook\nopen names\n\nsig Book {\n  entries: Name -> lone Addr\n}\n\nfact {\n  all n: Name, a: Addr | n->a in Book.entries => validName[n] and validAddr[a]\n}\n\nrun { some b: Book | #b.entries > 1 } for 4"
    }
  ],
  "main_file": "addressbook.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Address book example passed${NC}"
else
    echo -e "${RED}❌ Address book example failed${NC}"
    exit 1
fi

echo ""
echo -e "${PURPLE}2. 📁 Three-Level Import Chain:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "base.als",
      "content": "module base\n\nsig Element {}\npred hasElement { some Element }"
    },
    {
      "filename": "middle.als",
      "content": "module middle\nopen base\n\nsig Container {\n  contents: set Element\n}\n\npred hasContents[c: Container] {\n  some c.contents and hasElement\n}"
    },
    {
      "filename": "top.als",
      "content": "module top\nopen middle\n\nsig System {\n  containers: set Container\n}\n\nrun {\n  some s: System, c: s.containers |\n    hasContents[c]\n} for 3"
    }
  ],
  "main_file": "top.als",
  "output_format": "OUTPUT_FORMAT_TEXT",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Three-level import chain passed${NC}"
else
    echo -e "${RED}❌ Three-level import chain failed${NC}"
    exit 1
fi

echo ""
echo -e "${PURPLE}3. 📁 Parameterized Module with Alias:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "util/ordering.als",
      "content": "module util/ordering[T]\n\none sig First, Last extends T {}\n\npred isOrdered {\n  First != Last\n  some First\n  some Last\n}"
    },
    {
      "filename": "numbers.als",
      "content": "module numbers\nopen util/ordering[Number] as ord\nsig Number {}\n\nrun {\n  ord/isOrdered\n} for 3"
    }
  ],
  "main_file": "numbers.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Parameterized module with alias passed${NC}"
else
    echo -e "${RED}❌ Parameterized module with alias failed${NC}"
    exit 1
fi

echo ""
echo -e "${PURPLE}4. 📁 Four-File Import Chain:${NC}"
"${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "types.als",
      "content": "module types\n\nsig Element {}"
    },
    {
      "filename": "containers.als",
      "content": "module containers\nopen types\n\nsig Container {\n  items: set Element\n}"
    },
    {
      "filename": "operations.als",
      "content": "module operations\nopen containers\n\npred hasItems[c: Container] {\n  some c.items\n}"
    },
    {
      "filename": "main.als",
      "content": "module main\nopen operations\n\nrun {\n  some c: Container | hasItems[c]\n} for 3"
    }
  ],
  "main_file": "main.als",
  "output_format": "OUTPUT_FORMAT_TEXT",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Four-file import chain passed${NC}"
else
    echo -e "${RED}❌ Four-file import chain failed${NC}"
    exit 1
fi

echo ""
echo -e "${PURPLE}5. ❌ Error Test - Circular Import:${NC}"
RESPONSE=$("${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "a.als",
      "content": "module a\nopen b\nsig A {}"
    },
    {
      "filename": "b.als",
      "content": "module b\nopen a\nsig B {}"
    }
  ],
  "main_file": "a.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve 2>&1)
if echo "$RESPONSE" | grep -q "error_message\|ERROR"; then
    echo -e "${GREEN}✅ Circular import error handling passed${NC}"
else
    echo -e "${RED}❌ Circular import should have failed${NC}"
    exit 1
fi

echo ""
echo -e "${PURPLE}6. ❌ Error Test - Missing Import:${NC}"
RESPONSE=$("${GRPCURL_CMD}" -plaintext -d '{
  "files": [
    {
      "filename": "main.als",
      "content": "module main\nopen nonexistent\nsig Main {}"
    }
  ],
  "main_file": "main.als",
  "output_format": "OUTPUT_FORMAT_JSON",
  "solver_type": "SOLVER_TYPE_SAT4J"
}' localhost:50051 org.alloytools.alloy.grpc.SolverService/Solve 2>&1)
if echo "$RESPONSE" | grep -q "error_message\|ERROR"; then
    echo -e "${GREEN}✅ Missing import error handling passed${NC}"
else
    echo -e "${RED}❌ Missing import should have failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}🎉 All multi-file tests completed successfully!${NC}"
echo -e "${YELLOW}📊 Multi-File Test Summary:${NC}"
echo "  ✅ Address Book (Basic 2-file import)"
echo "  ✅ Three-Level Import Chain"
echo "  ✅ Parameterized Module with Directory Structure"
echo "  ✅ Four-File Import Chain"
echo "  ✅ Circular Import Error Handling"
echo "  ✅ Missing Import Error Handling"
echo ""
echo -e "${BLUE}🚀 Multi-file Alloy gRPC functionality is working perfectly!${NC}"
echo -e "${YELLOW}💡 These examples demonstrate:${NC}"
echo "  • Cross-file symbol resolution"
echo "  • Parameterized module imports"
echo "  • Directory structure preservation"
echo "  • Module aliases"
echo "  • Proper error handling for invalid imports"