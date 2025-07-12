package org.alloytools.alloy.grpc.integration;

import static org.junit.Assert.*;

import org.alloytools.alloy.grpc.proto.OutputFormat;
import org.alloytools.alloy.grpc.proto.SolveRequest;
import org.alloytools.alloy.grpc.proto.SolveResponse;
import org.alloytools.alloy.grpc.proto.SolverType;
import org.alloytools.alloy.grpc.impl.AlloySolverServiceImpl;
import org.junit.Before;
import org.junit.Test;

import io.grpc.stub.StreamObserver;

/**
 * Integration tests for basic model solving using real Alloy models.
 * These tests validate real functionality (not mocks) as preferred.
 */
public class BasicSolvingIntegrationTest {

    private AlloySolverServiceImpl service;
    private TestStreamObserver<SolveResponse> responseObserver;

    // Simple test model embedded in the test
    private static final String SIMPLE_PERSON_MODEL = 
        "sig Person {\n" +
        "    friends: set Person,\n" +
        "    age: Int\n" +
        "}\n" +
        "\n" +
        "fact NoSelfFriendship {\n" +
        "    no p: Person | p in p.friends\n" +
        "}\n" +
        "\n" +
        "fact SymmetricFriendship {\n" +
        "    all p1, p2: Person | p1 in p2.friends implies p2 in p1.friends\n" +
        "}\n" +
        "\n" +
        "fact ReasonableAge {\n" +
        "    all p: Person | p.age >= 0 and p.age <= 150\n" +
        "}\n" +
        "\n" +
        "pred SomeFriendships {\n" +
        "    some p: Person | some p.friends\n" +
        "}\n" +
        "\n" +
        "run SomeFriendships for 3\n" +
        "run {} for 2 but exactly 2 Person\n";

    private static final String UNSATISFIABLE_MODEL = 
        "sig Node {\n" +
        "    edges: set Node\n" +
        "}\n" +
        "\n" +
        "fact Contradiction {\n" +
        "    some Node\n" +
        "    no Node\n" +
        "}\n" +
        "\n" +
        "run {} for 3\n";

    @Before
    public void setUp() {
        service = new AlloySolverServiceImpl();
        responseObserver = new TestStreamObserver<>();
    }

    @Test
    public void testSimplePersonModelSatisfiable() {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_PERSON_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .setCommand("0") // First command: "run SomeFriendships for 3"
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertTrue("Model should be satisfiable", response.getSatisfiable());
        assertTrue("Error message should be empty", response.getErrorMessage().isEmpty());
        assertFalse("Solution data should not be empty", response.getSolutionData().isEmpty());

        // Verify metadata
        assertNotNull("Metadata should be present", response.getMetadata());
        assertTrue("Solving time should be positive", response.getMetadata().getSolvingTimeMs() >= 0);
        assertEquals("Solver should be SAT4J", "sat4j", response.getMetadata().getSolverUsed());
        assertFalse("Executed command should not be empty", response.getMetadata().getExecutedCommand().isEmpty());
    }

    @Test
    public void testUnsatisfiableModel() {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(UNSATISFIABLE_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_TEXT)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should be unsatisfiable", response.getSatisfiable());
        assertTrue("Error message should be empty for unsatisfiable models", response.getErrorMessage().isEmpty());
        assertTrue("Solution data should be empty for unsatisfiable models", response.getSolutionData().isEmpty());

        // Verify metadata
        assertNotNull("Metadata should be present", response.getMetadata());
        assertTrue("Solving time should be positive", response.getMetadata().getSolvingTimeMs() >= 0);
        assertEquals("Solver should be SAT4J", "sat4j", response.getMetadata().getSolverUsed());
    }

    @Test
    public void testMultipleCommandExecution() {
        // Test first command
        SolveRequest request1 = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_PERSON_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_TEXT)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .setCommand("0") // First command
            .build();

        service.solve(request1, responseObserver);
        assertTrue("First response should be received", responseObserver.hasResponse());
        SolveResponse response1 = responseObserver.getResponse();
        assertTrue("First command should be satisfiable", response1.getSatisfiable());

        // Reset observer for second test
        responseObserver = new TestStreamObserver<>();

        // Test second command
        SolveRequest request2 = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_PERSON_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_TEXT)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .setCommand("1") // Second command
            .build();

        service.solve(request2, responseObserver);
        assertTrue("Second response should be received", responseObserver.hasResponse());
        SolveResponse response2 = responseObserver.getResponse();
        assertTrue("Second command should be satisfiable", response2.getSatisfiable());

        // Commands should have different executed command strings
        assertNotEquals("Commands should be different", 
            response1.getMetadata().getExecutedCommand(), 
            response2.getMetadata().getExecutedCommand());
    }

    @Test
    public void testInvalidModelContent() {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent("invalid alloy syntax {{{")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertFalse("Should not have response", responseObserver.hasResponse());
        assertTrue("Should have gRPC error", responseObserver.hasError());

        Throwable error = responseObserver.getError();
        assertTrue("Should be StatusRuntimeException", error instanceof io.grpc.StatusRuntimeException);

        io.grpc.StatusRuntimeException statusError = (io.grpc.StatusRuntimeException) error;
        assertEquals("Should be INVALID_ARGUMENT", io.grpc.Status.Code.INVALID_ARGUMENT, statusError.getStatus().getCode());
        assertTrue("Error message should mention parse error",
            statusError.getMessage().toLowerCase().contains("parse"));
    }

    @Test
    public void testEmptyModelContent() {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent("")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should not be satisfiable", response.getSatisfiable());
        assertFalse("Error message should not be empty", response.getErrorMessage().isEmpty());
        assertTrue("Error message should mention empty content", 
            response.getErrorMessage().toLowerCase().contains("empty"));
    }

    @Test
    public void testInvalidCommandIndex() {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_PERSON_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .setCommand("99") // Invalid command index
            .build();

        service.solve(request, responseObserver);

        assertFalse("Should not have response", responseObserver.hasResponse());
        assertTrue("Should have gRPC error", responseObserver.hasError());

        Throwable error = responseObserver.getError();
        assertTrue("Should be StatusRuntimeException", error instanceof io.grpc.StatusRuntimeException);

        io.grpc.StatusRuntimeException statusError = (io.grpc.StatusRuntimeException) error;
        assertEquals("Should be INVALID_ARGUMENT", io.grpc.Status.Code.INVALID_ARGUMENT, statusError.getStatus().getCode());
        assertTrue("Error message should mention command not found",
            statusError.getMessage().toLowerCase().contains("command not found"));
    }

    /**
     * Helper class to capture gRPC responses in tests.
     */
    private static class TestStreamObserver<T> implements StreamObserver<T> {
        private T response;
        private Throwable error;
        private boolean completed = false;

        @Override
        public void onNext(T value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        public boolean hasResponse() {
            return response != null;
        }

        public boolean hasError() {
            return error != null;
        }

        public T getResponse() {
            return response;
        }

        public Throwable getError() {
            return error;
        }
    }
}
