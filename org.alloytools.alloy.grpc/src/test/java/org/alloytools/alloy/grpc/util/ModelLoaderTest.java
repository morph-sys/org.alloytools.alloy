package org.alloytools.alloy.grpc.util;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.ast.Command;

/**
 * Unit tests for ModelLoader.
 */
public class ModelLoaderTest {

    private static final String VALID_SIMPLE_MODEL = 
        "sig Person {\n" +
        "    friends: set Person\n" +
        "}\n" +
        "fact NoSelfFriendship {\n" +
        "    no p: Person | p in p.friends\n" +
        "}\n" +
        "run {} for 3\n";

    private static final String VALID_MODEL_WITH_MULTIPLE_COMMANDS = 
        "sig Node {\n" +
        "    edges: set Node\n" +
        "}\n" +
        "pred Connected {\n" +
        "    all n: Node | some n.edges\n" +
        "}\n" +
        "run Connected for 3\n" +
        "run {} for 2\n" +
        "check { no n: Node | n in n.edges } for 5\n";

    private static final String INVALID_SYNTAX_MODEL = 
        "sig Person {\n" +
        "    friends: set Person\n" +
        // Missing closing brace
        "run {} for 3\n";

    private static final String EMPTY_MODEL = "";

    private static final String MODEL_WITHOUT_COMMANDS = 
        "sig Person {\n" +
        "    friends: set Person\n" +
        "}\n";

    @Test
    public void testValidModelParsing() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(VALID_SIMPLE_MODEL, A4Reporter.NOP);
        
        assertTrue(result.isSuccess());
        assertNotNull(result.getModule());
        assertNotNull(result.getCommands());
        assertFalse(result.getCommands().isEmpty());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testMultipleCommandsParsing() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(VALID_MODEL_WITH_MULTIPLE_COMMANDS, A4Reporter.NOP);
        
        assertTrue(result.isSuccess());
        assertNotNull(result.getModule());
        assertNotNull(result.getCommands());
        assertEquals(3, result.getCommands().size());
        
        List<Command> commands = result.getCommands();
        // First command should be "run Connected for 3"
        assertTrue(commands.get(0).toString().contains("Connected"));
        // Second command should be "run {} for 2"
        assertTrue(commands.get(1).toString().contains("for 2"));
        // Third command should be "check {...} for 5"
        assertTrue(commands.get(2).check); // This is a check command
    }

    @Test
    public void testInvalidSyntaxHandling() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(INVALID_SYNTAX_MODEL, A4Reporter.NOP);
        
        assertFalse(result.isSuccess());
        assertNull(result.getModule());
        assertNull(result.getCommands());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Parse error"));
    }

    @Test
    public void testEmptyModelHandling() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(EMPTY_MODEL, A4Reporter.NOP);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("empty"));
    }

    @Test
    public void testNullModelHandling() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(null, A4Reporter.NOP);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("null"));
    }

    @Test
    public void testCommandExtraction() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(VALID_MODEL_WITH_MULTIPLE_COMMANDS, A4Reporter.NOP);
        assertTrue(result.isSuccess());
        
        List<Command> commands = result.getCommands();
        
        // Test finding command by index
        Optional<Command> firstCommand = ModelLoader.findCommand(commands, "0");
        assertTrue(firstCommand.isPresent());
        assertEquals(commands.get(0), firstCommand.get());
        
        Optional<Command> secondCommand = ModelLoader.findCommand(commands, "1");
        assertTrue(secondCommand.isPresent());
        assertEquals(commands.get(1), secondCommand.get());
        
        // Test finding command by invalid index
        Optional<Command> invalidCommand = ModelLoader.findCommand(commands, "10");
        assertFalse(invalidCommand.isPresent());
        
        // Test finding command with no specification (should return first)
        Optional<Command> defaultCommand = ModelLoader.findCommand(commands, null);
        assertTrue(defaultCommand.isPresent());
        assertEquals(commands.get(0), defaultCommand.get());
        
        Optional<Command> emptyCommand = ModelLoader.findCommand(commands, "");
        assertTrue(emptyCommand.isPresent());
        assertEquals(commands.get(0), emptyCommand.get());
    }

    @Test
    public void testCommandValidation() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(VALID_SIMPLE_MODEL, A4Reporter.NOP);
        assertTrue(result.isSuccess());
        
        // Valid commands should pass validation
        ModelLoader.ValidationResult validation = ModelLoader.validateCommands(result.getCommands());
        assertTrue(validation.isValid());
        assertNull(validation.getErrorMessage());
        
        // Empty command list should fail validation
        ModelLoader.ValidationResult emptyValidation = ModelLoader.validateCommands(List.of());
        assertFalse(emptyValidation.isValid());
        assertNotNull(emptyValidation.getErrorMessage());
        
        // Null command list should fail validation
        ModelLoader.ValidationResult nullValidation = ModelLoader.validateCommands(null);
        assertFalse(nullValidation.isValid());
        assertNotNull(nullValidation.getErrorMessage());
    }

    @Test
    public void testModelContentValidation() {
        // Valid model content
        ModelLoader.ValidationResult validResult = ModelLoader.validateModelContent(VALID_SIMPLE_MODEL);
        assertTrue(validResult.isValid());
        
        // Empty content
        ModelLoader.ValidationResult emptyResult = ModelLoader.validateModelContent("");
        assertFalse(emptyResult.isValid());
        
        // Null content
        ModelLoader.ValidationResult nullResult = ModelLoader.validateModelContent(null);
        assertFalse(nullResult.isValid());
        
        // Content without basic Alloy elements
        ModelLoader.ValidationResult invalidResult = ModelLoader.validateModelContent("just some text");
        assertFalse(invalidResult.isValid());
    }

    @Test
    public void testCollectingReporter() {
        ModelLoader.CollectingReporter reporter = new ModelLoader.CollectingReporter();
        
        // Initially should have no errors or warnings
        assertFalse(reporter.hasErrors());
        assertFalse(reporter.hasWarnings());
        assertEquals("", reporter.getErrors());
        assertEquals("", reporter.getWarnings());
        assertEquals("", reporter.getAllMessages());
        
        // Add a warning
        reporter.warning(new edu.mit.csail.sdg.alloy4.ErrorWarning("Test warning message"));
        assertTrue(reporter.hasWarnings());
        assertFalse(reporter.hasErrors());
        assertTrue(reporter.getWarnings().contains("Test warning message"));
        assertTrue(reporter.getAllMessages().contains("Test warning message"));

        // Add another warning
        reporter.warning(new edu.mit.csail.sdg.alloy4.ErrorWarning("Second warning"));
        assertTrue(reporter.getWarnings().contains("Test warning message"));
        assertTrue(reporter.getWarnings().contains("Second warning"));
    }

    @Test
    public void testModelWithoutExplicitCommands() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModel(MODEL_WITHOUT_COMMANDS, A4Reporter.NOP);

        // The model should parse successfully
        assertTrue(result.isSuccess());
        assertNotNull(result.getModule());
        assertNotNull(result.getCommands());

        // Alloy automatically generates a default command when there are signatures but no explicit commands
        assertEquals(1, result.getCommands().size());
        assertEquals("Default", result.getCommands().get(0).label);

        // Command validation should succeed because we have the default command
        ModelLoader.ValidationResult validation = ModelLoader.validateCommands(result.getCommands());
        assertTrue(validation.isValid());
    }
}
