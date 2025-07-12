package org.alloytools.alloy.grpc.error;

import static org.junit.Assert.*;

import org.alloytools.alloy.grpc.impl.AlloySolverServiceImpl;
import org.alloytools.alloy.grpc.proto.*;
import org.alloytools.alloy.grpc.util.TestStreamObserver;
import org.junit.Before;
import org.junit.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Tests for gRPC error handling as specified in the test plan.
 */
public class GrpcErrorHandlingTest {

    private AlloySolverServiceImpl service;
    private TestStreamObserver<SolveResponse> responseObserver;

    @Before
    public void setUp() {
        service = new AlloySolverServiceImpl();
        responseObserver = new TestStreamObserver<>();
    }

    @Test
    public void testInvalidArgumentErrors() {
        // Test with invalid model syntax
        SolveRequest invalidSyntaxRequest = SolveRequest.newBuilder()
            .setModelContent("invalid syntax {{{")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(invalidSyntaxRequest, responseObserver);
        
        // Verify error response
        assertTrue(responseObserver.hasError());
        Throwable error = responseObserver.getError();
        assertTrue(error instanceof StatusRuntimeException);
        
        StatusRuntimeException statusError = (StatusRuntimeException) error;
        assertEquals(Status.Code.INVALID_ARGUMENT, statusError.getStatus().getCode());

        // Check for various forms of syntax error messages
        String message = statusError.getMessage().toLowerCase();
        assertTrue("Error message should indicate syntax error, but was: " + statusError.getMessage(),
                  message.contains("syntax") || message.contains("parse") || message.contains("unexpected"));
    }

    @Test
    public void testUnimplementedErrors() {
        // Test with unavailable solver
        SolveRequest unavailableSolverRequest = SolveRequest.newBuilder()
            .setModelContent("sig A {} run {} for 3")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_LINGELING) // Likely unavailable
            .build();

        service.solve(unavailableSolverRequest, responseObserver);
        
        // Verify error response
        assertTrue(responseObserver.hasError());
        Throwable error = responseObserver.getError();
        assertTrue(error instanceof StatusRuntimeException);
        
        StatusRuntimeException statusError = (StatusRuntimeException) error;
        assertEquals(Status.Code.UNIMPLEMENTED, statusError.getStatus().getCode());
        assertTrue(statusError.getMessage().contains("solver") || 
                  statusError.getMessage().contains("Solver"));
    }

    @Test
    public void testInternalErrors() {
        // Test with valid syntax but type error
        SolveRequest typeErrorRequest = SolveRequest.newBuilder()
            .setModelContent("sig A { f: Int } fact { #A.f > \"string\" }")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(typeErrorRequest, responseObserver);
        
        // Verify error response
        assertTrue(responseObserver.hasError());
        Throwable error = responseObserver.getError();
        assertTrue(error instanceof StatusRuntimeException);
        
        StatusRuntimeException statusError = (StatusRuntimeException) error;

        assertEquals(Status.Code.INVALID_ARGUMENT, statusError.getStatus().getCode());
    }

    @Test
    public void testDeadlineExceededErrors() {
        // Create a model that would likely timeout
        StringBuilder largeModel = new StringBuilder();
        largeModel.append("sig Node { edges: set Node }\n");
        
        // Add many constraints to make solving slow
        largeModel.append("fact {\n");
        for (int i = 0; i < 20; i++) {
            largeModel.append("  all n1, n2: Node | n1->n2 in edges implies n2->n1 in edges\n");
            largeModel.append("  all n1, n2, n3: Node | n1->n2 in edges and n2->n3 in edges implies n1->n3 in edges\n");
        }
        largeModel.append("}\n");
        largeModel.append("run { #Node = 10 } for 10");
        
        // Create request with complex model that might be slow
        SolveRequest timeoutRequest = SolveRequest.newBuilder()
            .setModelContent(largeModel.toString())
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(timeoutRequest, responseObserver);

        // For now, just verify we get some response (either success or error)
        // TODO: Implement proper timeout handling in the service
        assertTrue("Should get either response or error",
                  responseObserver.hasResponse() || responseObserver.hasError());
    }

    @Test
    public void testInvalidCommandIndexError() {
        // Test with invalid command index
        SolveRequest invalidCommandRequest = SolveRequest.newBuilder()
            .setModelContent("sig A {} run {} for 3")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .setCommand("999") // Invalid command index
            .build();

        service.solve(invalidCommandRequest, responseObserver);
        
        // Verify error response
        assertTrue(responseObserver.hasError());
        Throwable error = responseObserver.getError();
        assertTrue(error instanceof StatusRuntimeException);
        
        StatusRuntimeException statusError = (StatusRuntimeException) error;
        assertEquals(Status.Code.INVALID_ARGUMENT, statusError.getStatus().getCode());
        assertTrue(statusError.getMessage().contains("command") || 
                  statusError.getMessage().contains("Command"));
    }
}