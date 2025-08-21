package org.alloytools.alloy.grpc.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.alloytools.alloy.grpc.proto.PingRequest;
import org.alloytools.alloy.grpc.proto.PingResponse;
import org.alloytools.alloy.grpc.proto.SolveRequest;
import org.alloytools.alloy.grpc.proto.SolveResponse;
import org.alloytools.alloy.grpc.proto.SolverServiceGrpc;
import org.alloytools.alloy.grpc.util.ModelLoader;
import org.alloytools.alloy.grpc.util.ProtocolBufferConverter;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import kodkod.engine.satlab.SATFactory;

/**
 * Implementation of the Alloy Solver gRPC service.
 */
public class AlloySolverServiceImpl extends SolverServiceGrpc.SolverServiceImplBase {

    private static final String VERSION = "6.3.0";

    @Override
    public void solve(SolveRequest request, StreamObserver<SolveResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate request
            ValidationResult validation = validateRequest(request);
            if (!validation.isValid()) {
                SolveResponse errorResponse = ProtocolBufferConverter.createErrorResponse(
                    validation.getErrorMessage(), 
                    System.currentTimeMillis() - startTime
                );
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }

            // Check solver availability
            if (!ProtocolBufferConverter.isSolverAvailable(request.getSolverType())) {
                responseObserver.onError(Status.UNIMPLEMENTED
                    .withDescription("Solver " + request.getSolverType() + " is not available on this system")
                    .asRuntimeException());
                return;
            }

            // Load and parse the model
            ModelLoader.CollectingReporter reporter = new ModelLoader.CollectingReporter();
            ModelLoader.ModelLoadResult loadResult = ModelLoader.loadModel(request.getModelContent(), reporter);
            
            if (!loadResult.isSuccess()) {
                responseObserver.onError(Status.fromCode(Status.Code.INVALID_ARGUMENT)
                    .withDescription(loadResult.getErrorMessage())
                    .asRuntimeException());
                return;
            }

            CompModule world = loadResult.getModule();
            List<Command> commands = loadResult.getCommands();

            // Configure solver options
            A4Options options = ProtocolBufferConverter.toA4Options(
                request.getSolverOptions(), 
                request.getSolverType()
            );

            // Check if specific command requested or execute all commands (default)
            if (!request.getCommand().trim().isEmpty() && 
                !"*".equals(request.getCommand().trim()) && 
                !"ALL".equalsIgnoreCase(request.getCommand().trim())) {
                // Execute specific command
                Optional<Command> commandOpt = ModelLoader.findCommand(commands, request.getCommand());
                if (!commandOpt.isPresent()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Command not found: " + request.getCommand())
                        .asRuntimeException());
                    return;
                }

                Command command = commandOpt.get();

                // Execute the specific command
                A4Solution solution = TranslateAlloyToKodkod.execute_command(
                    reporter, 
                    world.getAllReachableSigs(), 
                    command, 
                    options
                );

                long solvingTime = System.currentTimeMillis() - startTime;

                // Convert solution to response
                SolveResponse response = ProtocolBufferConverter.toSolveResponse(
                    solution, 
                    request.getOutputFormat(), 
                    solvingTime, 
                    command.toString()
                );

                responseObserver.onNext(response);
            } else {
                // Execute all commands (default behavior)
                // Execute all commands and combine results
                StringBuilder combinedResults = new StringBuilder();
                boolean anySatisfiable = false;
                long totalSolvingTime = 0;
                String lastSolverUsed = "";
                
                for (int i = 0; i < commands.size(); i++) {
                    Command command = commands.get(i);
                    long commandStartTime = System.currentTimeMillis();
                    
                    A4Solution solution = TranslateAlloyToKodkod.execute_command(
                        reporter, 
                        world.getAllReachableSigs(), 
                        command, 
                        options
                    );

                    long commandSolvingTime = System.currentTimeMillis() - commandStartTime;
                    totalSolvingTime += commandSolvingTime;

                    // Convert individual solution to string
                    SolveResponse individualResponse = ProtocolBufferConverter.toSolveResponse(
                        solution, 
                        request.getOutputFormat(), 
                        commandSolvingTime, 
                        command.toString()
                    );

                    if (individualResponse.getSatisfiable()) {
                        anySatisfiable = true;
                    }
                    
                    lastSolverUsed = individualResponse.getMetadata().getSolverUsed();

                    // Combine results with command separator
                    if (i > 0) {
                        combinedResults.append("\n--- Command ").append(i + 1).append(" ---\n");
                    } else {
                        combinedResults.append("--- Command ").append(i + 1).append(" ---\n");
                    }
                    combinedResults.append("Command: ").append(command.toString()).append("\n");
                    combinedResults.append("Satisfiable: ").append(individualResponse.getSatisfiable()).append("\n");
                    if (!individualResponse.getSolutionData().isEmpty()) {
                        combinedResults.append(individualResponse.getSolutionData()).append("\n");
                    }
                    if (!individualResponse.getErrorMessage().isEmpty()) {
                        combinedResults.append("Error: ").append(individualResponse.getErrorMessage()).append("\n");
                    }
                }

                // Create combined response
                SolveResponse combinedResponse = ProtocolBufferConverter.createCombinedResponse(
                    anySatisfiable,
                    combinedResults.toString(),
                    totalSolvingTime,
                    lastSolverUsed,
                    "All " + commands.size() + " commands"
                );

                responseObserver.onNext(combinedResponse);
            }

            responseObserver.onCompleted();

        } catch (Err err) {
            responseObserver.onError(Status.fromCode(Status.Code.INTERNAL)
                .withDescription("Alloy error: " + err.toString())
                .asRuntimeException());
            
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal server error: " + ex.getMessage())
                .withCause(ex)
                .asRuntimeException());
        }
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        try {
            String message = request.getMessage().isEmpty() ? "pong" : request.getMessage();
            
            // Get available solvers
            List<String> availableSolvers = SATFactory.getAllSolvers().stream()
                .map(SATFactory::id)
                .collect(Collectors.toList());

            PingResponse response = PingResponse.newBuilder()
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .setVersion(VERSION)
                .addAllAvailableSolvers(availableSolvers)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal server error: " + ex.getMessage())
                .withCause(ex)
                .asRuntimeException());
        }
    }

    /**
     * Validate the solve request.
     */
    private ValidationResult validateRequest(SolveRequest request) {
        if (request.getModelContent() == null || request.getModelContent().trim().isEmpty()) {
            return ValidationResult.error("Model content cannot be null or empty");
        }

        // Let the Alloy parser handle syntax validation - it gives better error messages
        return ValidationResult.success();
    }

    /**
     * Simple validation result class.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
