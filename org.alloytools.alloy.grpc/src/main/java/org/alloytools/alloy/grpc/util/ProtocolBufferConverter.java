package org.alloytools.alloy.grpc.util;

import org.alloytools.alloy.grpc.proto.OutputFormat;
import org.alloytools.alloy.grpc.proto.SolveResponse;
import org.alloytools.alloy.grpc.proto.SolverOptions;
import org.alloytools.alloy.grpc.proto.SolverType;
import org.alloytools.alloy.grpc.proto.SolutionMetadata;

import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import kodkod.engine.satlab.SATFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for converting between Alloy objects and Protocol Buffer messages.
 */
public class ProtocolBufferConverter {

    /**
     * Convert SolverOptions protobuf message to A4Options.
     */
    public static A4Options toA4Options(SolverOptions solverOptions, SolverType solverType) {
        A4Options options = new A4Options();
        
        if (solverOptions != null) {
            if (solverOptions.getUnrolls() != 0) {
                options.unrolls = solverOptions.getUnrolls();
            }
            if (solverOptions.getSkolemDepth() != 0) {
                options.skolemDepth = solverOptions.getSkolemDepth();
            }
            if (solverOptions.getCoreMinimization() != 0) {
                options.coreMinimization = solverOptions.getCoreMinimization();
            }
            if (solverOptions.getCoreGranularity() != 0) {
                options.coreGranularity = solverOptions.getCoreGranularity();
            }
            if (solverOptions.getDecomposeMode() != 0) {
                options.decompose_mode = solverOptions.getDecomposeMode();
            }
            if (solverOptions.getDecomposeThreads() != 0) {
                options.decompose_threads = solverOptions.getDecomposeThreads();
            }
            
            options.symmetry = solverOptions.getSymmetryBreaking() ? 20 : 0;
            options.noOverflow = solverOptions.getNoOverflow();
            options.inferPartialInstance = solverOptions.getInferPartialInstance();
        }
        
        // Set solver based on SolverType
        options.solver = toSATFactory(solverType);
        
        return options;
    }

    /**
     * Convert A4Options to SolverOptions protobuf message.
     */
    public static SolverOptions toSolverOptions(A4Options options) {
        return SolverOptions.newBuilder()
            .setUnrolls(options.unrolls)
            .setSkolemDepth(options.skolemDepth)
            .setCoreMinimization(options.coreMinimization)
            .setCoreGranularity(options.coreGranularity)
            .setDecomposeMode(options.decompose_mode)
            .setDecomposeThreads(options.decompose_threads)
            .setSymmetryBreaking(options.symmetry > 0)
            .setNoOverflow(options.noOverflow)
            .setInferPartialInstance(options.inferPartialInstance)
            .build();
    }

    /**
     * Convert SolverType enum to SATFactory.
     */
    public static SATFactory toSATFactory(SolverType solverType) {
        switch (solverType) {
            case SOLVER_TYPE_SAT4J:
            case SOLVER_TYPE_UNSPECIFIED:
                return SATFactory.DEFAULT; // SAT4J
            case SOLVER_TYPE_MINISAT:
                return findSolverByName("minisat");
            case SOLVER_TYPE_GLUCOSE:
                return findSolverByName("glucose");
            case SOLVER_TYPE_LINGELING:
                return findSolverByName("lingeling");
            case SOLVER_TYPE_PLINGELING:
                return findSolverByName("plingeling");
            case SOLVER_TYPE_CRYPTOMINISAT:
                return findSolverByName("cryptominisat");
            default:
                return SATFactory.DEFAULT;
        }
    }

    /**
     * Convert SATFactory to SolverType enum.
     */
    public static SolverType toSolverType(SATFactory factory) {
        String id = factory.id().toLowerCase();
        switch (id) {
            case "sat4j":
                return SolverType.SOLVER_TYPE_SAT4J;
            case "minisat":
                return SolverType.SOLVER_TYPE_MINISAT;
            case "glucose":
                return SolverType.SOLVER_TYPE_GLUCOSE;
            case "lingeling":
                return SolverType.SOLVER_TYPE_LINGELING;
            case "plingeling":
                return SolverType.SOLVER_TYPE_PLINGELING;
            case "cryptominisat":
                return SolverType.SOLVER_TYPE_CRYPTOMINISAT;
            default:
                return SolverType.SOLVER_TYPE_SAT4J;
        }
    }

    /**
     * Create SolveResponse from A4Solution.
     */
    public static SolveResponse toSolveResponse(A4Solution solution, OutputFormat outputFormat, 
                                               long solvingTimeMs, String executedCommand) {
        SolveResponse.Builder responseBuilder = SolveResponse.newBuilder()
            .setSatisfiable(solution.satisfiable());

        // Set solution data based on output format
        if (solution.satisfiable()) {
            String solutionData = formatSolution(solution, outputFormat);
            responseBuilder.setSolutionData(solutionData);
        }

        // Create metadata
        SolutionMetadata metadata = createSolutionMetadata(solution, solvingTimeMs, executedCommand);
        responseBuilder.setMetadata(metadata);

        return responseBuilder.build();
    }

    /**
     * Create SolveResponse for error cases.
     */
    public static SolveResponse createErrorResponse(String errorMessage, long solvingTimeMs) {
        SolutionMetadata metadata = SolutionMetadata.newBuilder()
            .setSolvingTimeMs(solvingTimeMs)
            .setSolverUsed("unknown")
            .build();

        return SolveResponse.newBuilder()
            .setSatisfiable(false)
            .setErrorMessage(errorMessage)
            .setMetadata(metadata)
            .build();
    }

    /**
     * Format A4Solution based on output format.
     */
    private static String formatSolution(A4Solution solution, OutputFormat outputFormat) {
        switch (outputFormat) {
            case OUTPUT_FORMAT_JSON:
            case OUTPUT_FORMAT_UNSPECIFIED:
                // For now, return basic JSON-like format
                return "{ \"satisfiable\": true, \"solution\": \"" + solution.toString().replace("\"", "\\\"") + "\" }";
            case OUTPUT_FORMAT_XML:
                try {
                    StringWriter stringWriter = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(stringWriter);
                    if (!solution.satisfiable()) {
                        printWriter.print("<instance satisfiable=\"false\"/>");
                    } else {
                        // Generate a simple XML representation
                        printWriter.print("<alloy builddate=\"" + edu.mit.csail.sdg.alloy4.Version.buildDate() + "\">\n");
                        printWriter.print("<instance bitwidth=\"4\" maxseq=\"4\" command=\"run\" filename=\"\">\n");
                        printWriter.print("<sig label=\"univ\" ID=\"0\" builtin=\"yes\" abstract=\"yes\"/>\n");
                        printWriter.print("<sig label=\"Int\" ID=\"1\" builtin=\"yes\"/>\n");
                        printWriter.print("<sig label=\"seq/Int\" ID=\"2\" builtin=\"yes\"/>\n");
                        printWriter.print("<sig label=\"String\" ID=\"3\" builtin=\"yes\"/>\n");
                        printWriter.print("</instance>\n");
                        printWriter.print("</alloy>\n");
                    }
                    printWriter.flush();
                    return stringWriter.toString();
                } catch (Exception e) {
                    return "<?xml version=\"1.0\"?>\n<error>Failed to generate XML: " + e.getMessage() + "</error>";
                }
            case OUTPUT_FORMAT_TEXT:
                return solution.toString();
            case OUTPUT_FORMAT_TABLE:
                return solution.format(); // Use the format() method for table output
            default:
                return solution.toString();
        }
    }

    /**
     * Create SolutionMetadata from A4Solution.
     */
    private static SolutionMetadata createSolutionMetadata(A4Solution solution, long solvingTimeMs,
                                                          String executedCommand) {
        return SolutionMetadata.newBuilder()
            .setSolvingTimeMs(solvingTimeMs)
            .setSolverUsed("sat4j") // Default solver name, will be improved later
            .setBitwidth(solution.getBitwidth())
            .setMaxSeq(solution.getMaxSeq())
            .setUnrolls(solution.unrolls())
            .setSkolemDepth(0) // Will be improved later when we have access to options
            .setSymmetryBreaking(false) // Will be improved later
            .setIncremental(solution.isIncremental())
            .setExecutedCommand(executedCommand != null ? executedCommand : "")
            .build();
    }

    /**
     * Find a solver by name, returning default if not found.
     */
    private static SATFactory findSolverByName(String name) {
        return SATFactory.getAllSolvers().stream()
            .filter(solver -> solver.id().toLowerCase().equals(name.toLowerCase()))
            .findFirst()
            .orElse(SATFactory.DEFAULT);
    }

    /**
     * Check if a solver is available on this system.
     */
    public static boolean isSolverAvailable(SolverType solverType) {
        if (solverType == SolverType.SOLVER_TYPE_SAT4J ||
            solverType == SolverType.SOLVER_TYPE_UNSPECIFIED) {
            return true; // SAT4J is always available
        }

        SATFactory factory = toSATFactory(solverType);
        return !factory.equals(SATFactory.DEFAULT) || solverType == SolverType.SOLVER_TYPE_SAT4J;
    }

    /**
     * Convert string to OutputFormat enum.
     */
    public static OutputFormat getOutputFormat(String format) {
        if (format == null) {
            return OutputFormat.OUTPUT_FORMAT_UNSPECIFIED;
        }

        switch (format.toLowerCase()) {
            case "json":
                return OutputFormat.OUTPUT_FORMAT_JSON;
            case "xml":
                return OutputFormat.OUTPUT_FORMAT_XML;
            case "text":
                return OutputFormat.OUTPUT_FORMAT_TEXT;
            case "table":
                return OutputFormat.OUTPUT_FORMAT_TABLE;
            default:
                return OutputFormat.OUTPUT_FORMAT_UNSPECIFIED;
        }
    }

    /**
     * Convert string to SolverType enum.
     */
    public static SolverType getSolverType(String solver) {
        if (solver == null) {
            return SolverType.SOLVER_TYPE_UNSPECIFIED;
        }

        switch (solver.toLowerCase()) {
            case "sat4j":
                return SolverType.SOLVER_TYPE_SAT4J;
            case "minisat":
                return SolverType.SOLVER_TYPE_MINISAT;
            case "glucose":
                return SolverType.SOLVER_TYPE_GLUCOSE;
            case "lingeling":
                return SolverType.SOLVER_TYPE_LINGELING;
            case "plingeling":
                return SolverType.SOLVER_TYPE_PLINGELING;
            case "cryptominisat":
                return SolverType.SOLVER_TYPE_CRYPTOMINISAT;
            default:
                return SolverType.SOLVER_TYPE_UNSPECIFIED;
        }
    }
}
