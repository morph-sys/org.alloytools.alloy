package org.alloytools.alloy.grpc.util;

import static org.junit.Assert.*;

import org.alloytools.alloy.grpc.proto.OutputFormat;
import org.alloytools.alloy.grpc.proto.SolverOptions;
import org.alloytools.alloy.grpc.proto.SolverType;
import org.junit.Test;

import edu.mit.csail.sdg.translator.A4Options;
import kodkod.engine.satlab.SATFactory;

/**
 * Unit tests for ProtocolBufferConverter.
 */
public class ProtocolBufferConverterTest {

    @Test
    public void testA4OptionsToSolverOptions() {
        // Create A4Options with specific values
        A4Options options = new A4Options();
        options.unrolls = 5;
        options.skolemDepth = 2;
        options.symmetry = 20;
        options.noOverflow = true;
        options.inferPartialInstance = true;
        options.coreMinimization = 1;
        options.coreGranularity = 3;
        options.decompose_mode = 2;
        options.decompose_threads = 8;

        // Convert to SolverOptions
        SolverOptions solverOptions = ProtocolBufferConverter.toSolverOptions(options);

        // Verify conversion
        assertEquals(5, solverOptions.getUnrolls());
        assertEquals(2, solverOptions.getSkolemDepth());
        assertTrue(solverOptions.getSymmetryBreaking());
        assertTrue(solverOptions.getNoOverflow());
        assertTrue(solverOptions.getInferPartialInstance());
        assertEquals(1, solverOptions.getCoreMinimization());
        assertEquals(3, solverOptions.getCoreGranularity());
        assertEquals(2, solverOptions.getDecomposeMode());
        assertEquals(8, solverOptions.getDecomposeThreads());
    }

    @Test
    public void testSolverOptionsToA4Options() {
        // Create SolverOptions with specific values
        SolverOptions solverOptions = SolverOptions.newBuilder()
            .setUnrolls(10)
            .setSkolemDepth(3)
            .setSymmetryBreaking(false)
            .setNoOverflow(true)
            .setInferPartialInstance(false)
            .setCoreMinimization(2)
            .setCoreGranularity(1)
            .setDecomposeMode(1)
            .setDecomposeThreads(4)
            .build();

        // Convert to A4Options
        A4Options options = ProtocolBufferConverter.toA4Options(solverOptions, SolverType.SOLVER_TYPE_SAT4J);

        // Verify conversion
        assertEquals(10, options.unrolls);
        assertEquals(3, options.skolemDepth);
        assertEquals(0, options.symmetry); // false -> 0
        assertTrue(options.noOverflow);
        assertFalse(options.inferPartialInstance);
        assertEquals(2, options.coreMinimization);
        assertEquals(1, options.coreGranularity);
        assertEquals(1, options.decompose_mode);
        assertEquals(4, options.decompose_threads);
        assertEquals(SATFactory.DEFAULT, options.solver);
    }

    @Test
    public void testSolverTypeConversion() {
        // Test SAT4J conversion
        SATFactory sat4j = ProtocolBufferConverter.toSATFactory(SolverType.SOLVER_TYPE_SAT4J);
        assertEquals(SATFactory.DEFAULT, sat4j);
        
        SolverType sat4jType = ProtocolBufferConverter.toSolverType(SATFactory.DEFAULT);
        assertEquals(SolverType.SOLVER_TYPE_SAT4J, sat4jType);

        // Test unspecified defaults to SAT4J
        SATFactory unspecified = ProtocolBufferConverter.toSATFactory(SolverType.SOLVER_TYPE_UNSPECIFIED);
        assertEquals(SATFactory.DEFAULT, unspecified);
    }

    @Test
    public void testSolverAvailability() {
        // SAT4J should always be available
        assertTrue(ProtocolBufferConverter.isSolverAvailable(SolverType.SOLVER_TYPE_SAT4J));
        assertTrue(ProtocolBufferConverter.isSolverAvailable(SolverType.SOLVER_TYPE_UNSPECIFIED));
        
        // Other solvers may or may not be available depending on the system
        // We just test that the method doesn't throw exceptions
        assertNotNull(ProtocolBufferConverter.isSolverAvailable(SolverType.SOLVER_TYPE_MINISAT));
        assertNotNull(ProtocolBufferConverter.isSolverAvailable(SolverType.SOLVER_TYPE_GLUCOSE));
    }

    @Test
    public void testDefaultValues() {
        // Test with null/empty SolverOptions
        A4Options options = ProtocolBufferConverter.toA4Options(null, SolverType.SOLVER_TYPE_SAT4J);
        assertNotNull(options);
        assertEquals(SATFactory.DEFAULT, options.solver);

        // Test with empty SolverOptions
        SolverOptions emptySolverOptions = SolverOptions.newBuilder().build();
        A4Options optionsFromEmpty = ProtocolBufferConverter.toA4Options(emptySolverOptions, SolverType.SOLVER_TYPE_SAT4J);
        assertNotNull(optionsFromEmpty);
        assertEquals(SATFactory.DEFAULT, optionsFromEmpty.solver);
    }

    @Test
    public void testSymmetryBreakingConversion() {
        // Test symmetry breaking true
        SolverOptions withSymmetry = SolverOptions.newBuilder()
            .setSymmetryBreaking(true)
            .build();
        A4Options optionsWithSymmetry = ProtocolBufferConverter.toA4Options(withSymmetry, SolverType.SOLVER_TYPE_SAT4J);
        assertTrue(optionsWithSymmetry.symmetry > 0);

        // Test symmetry breaking false
        SolverOptions withoutSymmetry = SolverOptions.newBuilder()
            .setSymmetryBreaking(false)
            .build();
        A4Options optionsWithoutSymmetry = ProtocolBufferConverter.toA4Options(withoutSymmetry, SolverType.SOLVER_TYPE_SAT4J);
        assertEquals(0, optionsWithoutSymmetry.symmetry);
    }

    @Test
    public void testErrorResponseCreation() {
        String errorMessage = "Test error message";
        long solvingTime = 1000L;

        var errorResponse = ProtocolBufferConverter.createErrorResponse(errorMessage, solvingTime);

        assertFalse(errorResponse.getSatisfiable());
        assertEquals(errorMessage, errorResponse.getErrorMessage());
        assertEquals(solvingTime, errorResponse.getMetadata().getSolvingTimeMs());
        assertEquals("unknown", errorResponse.getMetadata().getSolverUsed());
        assertTrue(errorResponse.getSolutionData().isEmpty());
    }

    @Test
    public void testOutputFormatConversion() {
        // Test all output format enum conversions
        assertEquals(OutputFormat.OUTPUT_FORMAT_JSON,
                    ProtocolBufferConverter.getOutputFormat("json"));
        assertEquals(OutputFormat.OUTPUT_FORMAT_XML,
                    ProtocolBufferConverter.getOutputFormat("xml"));
        assertEquals(OutputFormat.OUTPUT_FORMAT_TEXT,
                    ProtocolBufferConverter.getOutputFormat("text"));
        assertEquals(OutputFormat.OUTPUT_FORMAT_TABLE,
                    ProtocolBufferConverter.getOutputFormat("table"));
        assertEquals(OutputFormat.OUTPUT_FORMAT_UNSPECIFIED,
                    ProtocolBufferConverter.getOutputFormat("invalid"));
        assertEquals(OutputFormat.OUTPUT_FORMAT_UNSPECIFIED,
                    ProtocolBufferConverter.getOutputFormat(null));
    }

    @Test
    public void testSolverTypeStringConversion() {
        // Test all solver type enum conversions
        assertEquals(SolverType.SOLVER_TYPE_SAT4J,
                    ProtocolBufferConverter.getSolverType("sat4j"));
        assertEquals(SolverType.SOLVER_TYPE_MINISAT,
                    ProtocolBufferConverter.getSolverType("minisat"));
        assertEquals(SolverType.SOLVER_TYPE_GLUCOSE,
                    ProtocolBufferConverter.getSolverType("glucose"));
        assertEquals(SolverType.SOLVER_TYPE_UNSPECIFIED,
                    ProtocolBufferConverter.getSolverType("invalid"));
        assertEquals(SolverType.SOLVER_TYPE_UNSPECIFIED,
                    ProtocolBufferConverter.getSolverType(null));
    }
}
