package org.alloytools.alloy.grpc.util;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.A4Reporter;

/**
 * Unit tests for multi-file model loading functionality.
 */
public class MultiFileModelLoaderTest {

    @Test
    public void testBasicMultiFileLoading() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "module util\nsig Util {}\npred hasUtil { some Util }");
        fileMap.put("main.als", "module main\nopen util\nrun { hasUtil } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertTrue("Model should load successfully", result.isSuccess());
        assertNotNull("Module should not be null", result.getModule());
        assertFalse("Commands should not be empty", result.getCommands().isEmpty());
    }

    @Test
    public void testParameterizedModules() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("lib.als", 
            "module lib[T]\n" +
            "sig Container { items: set T }\n" +
            "pred hasItems[c: Container] { some c.items }");
        fileMap.put("app.als", 
            "module app\n" +
            "open lib[Int] as library\n" +
            "run { some c: library/Container | library/hasItems[c] } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "app.als", A4Reporter.NOP);
        
        assertTrue("Parameterized module should load successfully", result.isSuccess());
        assertNotNull("Module should not be null", result.getModule());
        assertFalse("Commands should not be empty", result.getCommands().isEmpty());
    }

    @Test
    public void testNestedDirectoryStructure() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util/base.als", "module util/base\nsig Base {}");
        fileMap.put("util/derived.als", "module util/derived\nopen util/base\nsig Derived extends Base {}");
        fileMap.put("main.als", "module main\nopen util/derived\nrun { some Derived } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertTrue("Nested directory structure should work", result.isSuccess());
        assertNotNull("Module should not be null", result.getModule());
    }

    @Test
    public void testThreeLevelImportChain() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("base.als", "module base\nsig Element {}");
        fileMap.put("middle.als", "module middle\nopen base\nsig Container { contents: set Element }");
        fileMap.put("top.als", "module top\nopen middle\nrun { some Container } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "top.als", A4Reporter.NOP);
        
        assertTrue("Three-level import chain should work", result.isSuccess());
        assertNotNull("Module should not be null", result.getModule());
    }

    @Test
    public void testErrorEmptyFileMap() {
        Map<String, String> fileMap = new HashMap<>();
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertFalse("Empty file map should fail", result.isSuccess());
        assertTrue("Error message should mention empty", 
            result.getErrorMessage().toLowerCase().contains("empty"));
    }

    @Test
    public void testErrorNullFileMap() {
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(null, "main.als", A4Reporter.NOP);
        
        assertFalse("Null file map should fail", result.isSuccess());
        assertTrue("Error message should mention null", 
            result.getErrorMessage().toLowerCase().contains("null"));
    }

    @Test
    public void testErrorEmptyMainFile() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "sig Util {}");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "", A4Reporter.NOP);
        
        assertFalse("Empty main file should fail", result.isSuccess());
        assertTrue("Error message should mention empty", 
            result.getErrorMessage().toLowerCase().contains("empty"));
    }

    @Test
    public void testErrorNullMainFile() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "sig Util {}");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, null, A4Reporter.NOP);
        
        assertFalse("Null main file should fail", result.isSuccess());
        assertTrue("Error message should mention null", 
            result.getErrorMessage().toLowerCase().contains("null"));
    }

    @Test
    public void testErrorMainFileNotFound() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "sig Util {}");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "missing.als", A4Reporter.NOP);
        
        assertFalse("Missing main file should fail", result.isSuccess());
        assertTrue("Error message should mention not found", 
            result.getErrorMessage().toLowerCase().contains("not found"));
    }

    @Test
    public void testErrorSyntaxError() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("main.als", "invalid syntax {{{");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertFalse("Syntax error should fail", result.isSuccess());
        assertTrue("Error message should mention parse", 
            result.getErrorMessage().toLowerCase().contains("parse"));
    }

    @Test
    public void testErrorMissingImport() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("main.als", "module main\nopen nonexistent\nsig Main {}");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertFalse("Missing import should fail", result.isSuccess());
        assertNotNull("Error message should be provided", result.getErrorMessage());
    }

    @Test
    public void testWithReporter() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "module util\nsig Util {}");
        fileMap.put("main.als", "module main\nopen util\nrun { some Util } for 3");
        
        ModelLoader.CollectingReporter reporter = new ModelLoader.CollectingReporter();
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", reporter);
        
        assertTrue("Model should load successfully with reporter", result.isSuccess());
        assertFalse("Should not have errors", reporter.hasErrors());
    }

    @Test
    public void testFilenameCaseSensitivity() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("Util.als", "module Util\nsig UtilSig {}");
        fileMap.put("main.als", "module main\nopen Util\nrun { some UtilSig } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertTrue("Case-sensitive filenames should work", result.isSuccess());
    }

    @Test
    public void testFileWithInvalidContent() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("invalid.als", "invalid alloy syntax {{{");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "invalid.als", A4Reporter.NOP);
        
        // This should fail because of invalid syntax
        assertFalse("Invalid syntax should cause parsing to fail", result.isSuccess());
        assertNotNull("Error message should be provided", result.getErrorMessage());
    }

    @Test
    public void testMultipleCommandsInMainFile() {
        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("util.als", "module util\nsig Util {}");
        fileMap.put("main.als", 
            "module main\n" +
            "open util\n" +
            "run { some Util } for 3\n" +
            "check { all u: Util | some u } for 3");
        
        ModelLoader.ModelLoadResult result = ModelLoader.loadModelFromFiles(fileMap, "main.als", A4Reporter.NOP);
        
        assertTrue("Model should load successfully", result.isSuccess());
        assertEquals("Should have 2 commands", 2, result.getCommands().size());
    }
}