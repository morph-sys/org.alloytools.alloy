# Valid Syntax - Check Fails Test Data

This directory contains Alloy models with **valid syntax** that will **fail during the checking/solving phase**. These models are designed to test error handling in the gRPC service when:

1. **Parsing succeeds** - The `.als` files have correct Alloy syntax
2. **Type checking succeeds** - All signatures, fields, and expressions are well-typed  
3. **Solving fails** - The constraints are unsatisfiable or contradictory

## Test Models

### 1. `impossible-constraints.als`
**Purpose**: Tests contradictory logical constraints
**Failures**:
- Requires everyone to be friends with everyone AND no one to have friends
- Ages must be both positive AND negative
- People must be both Students AND Teachers (but they're disjoint)
- Must have 3, 5, and 0 people simultaneously

**Expected Behavior**:
- Parse: ✅ Success
- Execute: ❌ UNSAT (no satisfying instances)

### 2. `unsatisfiable-graph.als`
**Purpose**: Tests impossible graph structure constraints
**Failures**:
- Graph must be connected but have no edges
- All nodes must have different colors AND same color
- Graph must be acyclic AND every node must reach itself
- 5 nodes with 3 colors where all must be different

**Expected Behavior**:
- Parse: ✅ Success  
- Execute: ❌ UNSAT (impossible graph structure)

### 3. `arithmetic-overflow.als`
**Purpose**: Tests integer arithmetic edge cases and overflows
**Failures**:
- Values must be max int AND max int + 1 (overflow)
- Values must be both max and min integer simultaneously
- Transaction amounts larger than max int but must sum to valid balance
- Self-referential arithmetic constraints

**Expected Behavior**:
- Parse: ✅ Success
- Execute: ❌ UNSAT or arithmetic error

### 4. `temporal-paradox.als`
**Purpose**: Tests contradictory temporal/sequential constraints
**Failures**:
- Sequence must be both cyclic AND acyclic
- Values must increase AND decrease along sequence
- Every state needs predecessor but one must be initial
- Current state must be both in AND not in process states

**Expected Behavior**:
- Parse: ✅ Success
- Execute: ❌ UNSAT (temporal paradox)

## Usage in Tests

### Unit Tests
```go
func TestUnsatisfiableModels(t *testing.T) {
    testCases := []struct {
        name     string
        file     string
        expected string
    }{
        {
            name:     "impossible_constraints",
            file:     "impossible-constraints.als",
            expected: "UNSAT",
        },
        {
            name:     "unsatisfiable_graph", 
            file:     "unsatisfiable-graph.als",
            expected: "UNSAT",
        },
        // ... more cases
    }
    
    for _, tc := range testCases {
        t.Run(tc.name, func(t *testing.T) {
            content := readTestFile(tc.file)
            
            // Parse should succeed
            parseResp := service.ParseModel(content)
            assert.NoError(t, parseResp.Error)
            
            // Execute should fail with UNSAT
            execResp := service.ExecuteCommand(parseResp.ModelId, 0)
            assert.False(t, execResp.Solution.Satisfiable)
            assert.Contains(t, execResp.Error.Message, "UNSAT")
        })
    }
}
```

### Integration Tests
```go
func TestErrorHandlingWorkflow(t *testing.T) {
    // Test complete workflow with unsatisfiable model
    model := loadTestModel("impossible-constraints.als")
    
    // Step 1: Parse (should succeed)
    parseResp := client.ParseModel(ctx, &pb.ParseModelRequest{
        Source: &pb.ParseModelRequest_ModelContent{
            ModelContent: model,
        },
    })
    require.NoError(t, err)
    require.NotNil(t, parseResp.GetModel())
    
    // Step 2: Execute (should fail gracefully)
    execResp := client.ExecuteCommand(ctx, &pb.ExecuteCommandRequest{
        ModelId: parseResp.GetModel().ModelId,
        CommandSelector: &pb.ExecuteCommandRequest_CommandIndex{
            CommandIndex: 0,
        },
    })
    require.NoError(t, err) // gRPC call succeeds
    require.NotNil(t, execResp.GetSolution())
    
    // But solution should be unsatisfiable
    solution := execResp.GetSolution()
    assert.False(t, solution.Satisfiable)
    assert.Empty(t, solution.Instances)
    assert.NotEmpty(t, solution.UnsatCore) // Should provide unsat core
}
```

### Performance Tests
```go
func BenchmarkUnsatisfiableModels(b *testing.B) {
    models := loadUnsatisfiableModels()
    
    b.ResetTimer()
    for i := 0; i < b.N; i++ {
        model := models[i%len(models)]
        
        // Measure time to detect unsatisfiability
        start := time.Now()
        result := service.ExecuteCommand(model)
        duration := time.Since(start)
        
        // Should quickly determine UNSAT
        assert.False(b, result.Satisfiable)
        assert.Less(b, duration, 5*time.Second)
    }
}
```

## Expected Error Types

### gRPC Error Responses
```protobuf
// For unsatisfiable models
SolutionResponse {
  solution_id: "unsat-12345"
  satisfiable: false
  has_next: false
  instances: []  // empty
  unsat_core: ["fact1", "fact2"]  // conflicting constraints
  stats: {
    solving_time_ms: 150
    total_variables: 45
    clauses: 120
  }
}
```

### Error Categories
1. **UNSAT**: No satisfying instances exist
2. **Timeout**: Solver exceeded time limit  
3. **Resource**: Out of memory or other resource limits
4. **Arithmetic**: Integer overflow/underflow errors

## Benefits for Testing

1. **Error Handling**: Validates graceful handling of unsatisfiable models
2. **Performance**: Tests solver efficiency on impossible constraints
3. **User Experience**: Ensures meaningful error messages
4. **Robustness**: Confirms service stability under edge cases
5. **Debugging**: Provides unsat cores for constraint analysis

These test models complement the valid models by ensuring the gRPC service handles the full spectrum of Alloy analysis scenarios, from successful solving to graceful failure handling.
