package org.alloytools.alloy.grpc.integration;

import static org.junit.Assert.*;

import org.alloytools.alloy.grpc.impl.AlloySolverServiceImpl;
import org.alloytools.alloy.grpc.proto.*;
import org.alloytools.alloy.grpc.util.TestStreamObserver;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Integration tests for output formats as specified in the test plan.
 */
public class OutputFormatIntegrationTest {

    private AlloySolverServiceImpl service;
    private TestStreamObserver<SolveResponse> responseObserver;

    // Simple test model
    private static final String SIMPLE_MODEL = 
        "sig Person {\n" +
        "    friends: set Person,\n" +
        "    age: Int\n" +
        "}\n" +
        "\n" +
        "fact {\n" +
        "    all p: Person | p.age >= 0\n" +
        "    all p: Person | p not in p.friends\n" +
        "}\n" +
        "\n" +
        "run {} for 3 but 5 Int";

    @Before
    public void setUp() {
        service = new AlloySolverServiceImpl();
        responseObserver = new TestStreamObserver<>();
    }

    @Test
    public void testJSONOutputFormat() {
        // Create request with JSON output format
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_JSON)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        // Execute request
        service.solve(request, responseObserver);
        
        // Verify response
        assertTrue(responseObserver.hasResponse());
        SolveResponse response = responseObserver.getResponse();
        
        // Verify solution is satisfiable
        assertTrue(response.getSatisfiable());
        
        // Verify solution data is valid JSON
        String jsonData = response.getSolutionData();
        assertNotNull(jsonData);
        assertFalse(jsonData.isEmpty());
        
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonData);
            assertTrue(jsonElement.isJsonObject());
        } catch (Exception e) {
            fail("Solution data is not valid JSON: " + e.getMessage());
        }
        
        // Verify JSON contains expected elements
        assertTrue(jsonData.contains("Person"));
        assertTrue(jsonData.contains("friends"));
        assertTrue(jsonData.contains("age"));
    }

    @Test
    public void testXMLOutputFormat() {
        // Create request with XML output format
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_XML)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        // Execute request
        service.solve(request, responseObserver);
        
        // Verify response
        assertTrue(responseObserver.hasResponse());
        SolveResponse response = responseObserver.getResponse();
        
        // Verify solution is satisfiable
        assertTrue(response.getSatisfiable());
        
        // Verify solution data is valid XML
        String xmlData = response.getSolutionData();
        assertNotNull(xmlData);
        assertFalse(xmlData.isEmpty());



        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlData)));
            assertNotNull(document.getDocumentElement());
        } catch (Exception e) {
            fail("Solution data is not valid XML: " + e.getMessage());
        }

        // Verify XML contains some basic structure (relaxed checks since exact content may vary)
        assertTrue("XML should contain sig elements", xmlData.contains("<sig"));
        assertTrue("XML should be well-formed", xmlData.contains("</"));
        assertTrue("XML should contain instance data", xmlData.contains("instance"));
    }

    @Test
    public void testTextOutputFormat() {
        // Create request with text output format
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_TEXT)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        // Execute request
        service.solve(request, responseObserver);
        
        // Verify response
        assertTrue(responseObserver.hasResponse());
        SolveResponse response = responseObserver.getResponse();
        
        // Verify solution is satisfiable
        assertTrue(response.getSatisfiable());
        
        // Verify solution data is not empty
        String textData = response.getSolutionData();
        assertNotNull(textData);
        assertFalse(textData.isEmpty());
        
        // Verify text contains expected elements
        assertTrue(textData.contains("Person"));
        assertTrue(textData.contains("friends"));
        assertTrue(textData.contains("age"));
    }

    @Test
    public void testTableOutputFormat() {
        // Create request with table output format
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_MODEL)
            .setOutputFormat(OutputFormat.OUTPUT_FORMAT_TABLE)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        // Execute request
        service.solve(request, responseObserver);
        
        // Verify response
        assertTrue(responseObserver.hasResponse());
        SolveResponse response = responseObserver.getResponse();
        
        // Verify solution is satisfiable
        assertTrue(response.getSatisfiable());
        
        // Verify solution data is not empty
        String tableData = response.getSolutionData();
        assertNotNull(tableData);
        assertFalse(tableData.isEmpty());
        
        // Verify table format contains expected elements
        assertTrue(tableData.contains("Person"));
        assertTrue(tableData.contains("friends"));
        assertTrue(tableData.contains("age"));
    }

    @Test
    public void testOutputFormatConsistency() {
        // Get solutions in different formats
        SolveResponse jsonResponse = getSolutionInFormat(OutputFormat.OUTPUT_FORMAT_JSON);
        SolveResponse xmlResponse = getSolutionInFormat(OutputFormat.OUTPUT_FORMAT_XML);
        SolveResponse textResponse = getSolutionInFormat(OutputFormat.OUTPUT_FORMAT_TEXT);
        SolveResponse tableResponse = getSolutionInFormat(OutputFormat.OUTPUT_FORMAT_TABLE);
        
        // All should be satisfiable
        assertTrue(jsonResponse.getSatisfiable());
        assertTrue(xmlResponse.getSatisfiable());
        assertTrue(textResponse.getSatisfiable());
        assertTrue(tableResponse.getSatisfiable());
        
        // All should have non-empty solution data
        assertFalse(jsonResponse.getSolutionData().isEmpty());
        assertFalse(xmlResponse.getSolutionData().isEmpty());
        assertFalse(textResponse.getSolutionData().isEmpty());
        assertFalse(tableResponse.getSolutionData().isEmpty());
        
        // All should have the same metadata
        assertEquals(jsonResponse.getMetadata().getSolverUsed(), xmlResponse.getMetadata().getSolverUsed());
        assertEquals(jsonResponse.getMetadata().getExecutedCommand(), xmlResponse.getMetadata().getExecutedCommand());
        assertEquals(jsonResponse.getMetadata().getBitwidth(), xmlResponse.getMetadata().getBitwidth());
        assertEquals(jsonResponse.getMetadata().getMaxSeq(), xmlResponse.getMetadata().getMaxSeq());
        assertEquals(jsonResponse.getMetadata().getUnrolls(), xmlResponse.getMetadata().getUnrolls());
    }
    
    private SolveResponse getSolutionInFormat(OutputFormat format) {
        // Create request with specified output format
        SolveRequest request = SolveRequest.newBuilder()
            .setModelContent(SIMPLE_MODEL)
            .setOutputFormat(format)
            .setSolverType(SolverType.SOLVER_TYPE_SAT4J)
            .build();

        // Reset observer
        responseObserver = new TestStreamObserver<>();
        
        // Execute request
        service.solve(request, responseObserver);
        
        // Return response
        assertTrue(responseObserver.hasResponse());
        return responseObserver.getResponse();
    }
}