package org.alloytools.alloy.grpc.integration;

import static org.junit.Assert.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.alloytools.alloy.grpc.impl.AlloySolverServiceImpl;
import org.alloytools.alloy.grpc.proto.AlloyFile;
import org.alloytools.alloy.grpc.proto.OutputFormat;
import org.alloytools.alloy.grpc.proto.SolveRequest;
import org.alloytools.alloy.grpc.proto.SolveResponse;
import org.alloytools.alloy.grpc.proto.SolverType;
import org.junit.Before;
import org.junit.Test;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * Integration tests for multi-file Alloy model solving via gRPC.
 */
public class MultiFileSolvingIntegrationTest {

    private AlloySolverServiceImpl service;
    private TestStreamObserver<SolveResponse> responseObserver;

    @Before
    public void setUp() {
        service = new AlloySolverServiceImpl();
        responseObserver = new TestStreamObserver<>();
    }

    @Test
    public void testBasicMultiFileSolve() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util.als")
                .setContent("module util\nsig Util {}\npred hasUtil { some Util }"))
            .addFiles(AlloyFile.newBuilder()
                .setFilename("main.als")
                .setContent("module main\nopen util\nrun { hasUtil } for 3"))
            .setMainFile("main.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertTrue("Model should be satisfiable", response.getSatisfiable());
        assertTrue("Error message should be empty", response.getErrorMessage().isEmpty());
        assertFalse("Solution data should not be empty", response.getSolutionData().isEmpty());
    }

    @Test
    public void testSimpleMultiFileImport() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("library.als")
                .setContent("module library\nsig Container {}\npred hasContainer { some Container }"))
            .addFiles(AlloyFile.newBuilder()
                .setFilename("application.als")
                .setContent("module application\nopen library\nrun { hasContainer } for 3"))
            .setMainFile("application.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertTrue("Simple multi-file model should be satisfiable", response.getSatisfiable());
        assertTrue("Error message should be empty", response.getErrorMessage().isEmpty());
    }

    @Test
    public void testNestedDirectoryStructure() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util/base.als")
                .setContent("module util/base\nsig Base {}"))
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util/derived.als")
                .setContent("module util/derived\nopen util/base\nsig Derived extends Base {}"))
            .addFiles(AlloyFile.newBuilder()
                .setFilename("main.als")
                .setContent("module main\nopen util/derived\nrun { some Derived } for 3"))
            .setMainFile("main.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertTrue("Nested directory model should be satisfiable", response.getSatisfiable());
    }

    @Test
    public void testErrorMissingMainFile() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util.als")
                .setContent("sig Util {}"))
            .setMainFile("missing.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should not be satisfiable", response.getSatisfiable());
        assertFalse("Error message should not be empty", response.getErrorMessage().isEmpty());
        assertTrue("Error message should mention main file", 
            response.getErrorMessage().toLowerCase().contains("main"));
    }

    @Test
    public void testErrorEmptyMainFile() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util.als")
                .setContent("sig Util {}"))
            .setMainFile("")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should not be satisfiable", response.getSatisfiable());
        assertFalse("Error message should not be empty", response.getErrorMessage().isEmpty());
        assertTrue("Error message should mention main_file", 
            response.getErrorMessage().toLowerCase().contains("main_file"));
    }

    @Test
    public void testErrorEmptyFilename() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .addFiles(AlloyFile.newBuilder()
                .setFilename("")
                .setContent("sig Util {}"))
            .setMainFile("main.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should not be satisfiable", response.getSatisfiable());
        assertFalse("Error message should not be empty", response.getErrorMessage().isEmpty());
        assertTrue("Error message should mention filename", 
            response.getErrorMessage().toLowerCase().contains("filename"));
    }

    @Test
    public void testErrorBothModelContentAndFiles() throws Exception {
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent("sig Test {}")
            .addFiles(AlloyFile.newBuilder()
                .setFilename("util.als")
                .setContent("sig Util {}"))
            .setMainFile("util.als")
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        service.solve(request, responseObserver);

        assertTrue("Response should be received", responseObserver.hasResponse());
        assertFalse("Should not have gRPC error", responseObserver.hasError());

        SolveResponse response = responseObserver.getResponse();
        assertFalse("Model should not be satisfiable", response.getSatisfiable());
        assertFalse("Error message should not be empty", response.getErrorMessage().isEmpty());
        assertTrue("Error message should mention both", 
            response.getErrorMessage().toLowerCase().contains("both") ||
            response.getErrorMessage().toLowerCase().contains("cannot specify"));
    }




    /**
     * Test stream observer implementation for capturing responses and errors.
     */
    private static class TestStreamObserver<T> implements StreamObserver<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        private volatile boolean completed = false;

        @Override
        public void onNext(T value) {
            future.complete(value);
        }

        @Override
        public void onError(Throwable t) {
            errorFuture.complete(t);
        }

        @Override
        public void onCompleted() {
            completed = true;
            if (!future.isDone()) {
                future.complete(null);
            }
        }

        public boolean hasResponse() {
            return future.isDone() && !errorFuture.isDone();
        }

        public boolean hasError() {
            return errorFuture.isDone();
        }

        public T getResponse() throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(5, TimeUnit.SECONDS);
        }

        public Throwable getError() throws InterruptedException, ExecutionException, TimeoutException {
            return errorFuture.get(5, TimeUnit.SECONDS);
        }
    }
}