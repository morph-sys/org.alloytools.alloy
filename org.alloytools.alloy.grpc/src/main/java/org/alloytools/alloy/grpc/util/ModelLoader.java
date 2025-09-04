package org.alloytools.alloy.grpc.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;

/**
 * Utility class for loading and validating Alloy models from string content.
 */
public class ModelLoader {

    /**
     * Result of loading an Alloy model.
     */
    public static class ModelLoadResult {
        private final CompModule module;
        private final List<Command> commands;
        private final String errorMessage;
        private final boolean success;

        private ModelLoadResult(CompModule module, List<Command> commands, String errorMessage, boolean success) {
            this.module = module;
            this.commands = commands;
            this.errorMessage = errorMessage;
            this.success = success;
        }

        public static ModelLoadResult success(CompModule module, List<Command> commands) {
            return new ModelLoadResult(module, commands, null, true);
        }

        public static ModelLoadResult error(String errorMessage) {
            return new ModelLoadResult(null, null, errorMessage, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public CompModule getModule() {
            return module;
        }

        public List<Command> getCommands() {
            return commands;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Load and parse an Alloy model from string content.
     * 
     * @param modelContent The Alloy model source code
     * @param reporter The reporter for diagnostics (can be null)
     * @return ModelLoadResult containing the parsed module or error information
     */
    public static ModelLoadResult loadModel(String modelContent, A4Reporter reporter) {
        if (modelContent == null || modelContent.trim().isEmpty()) {
            return ModelLoadResult.error("Model content cannot be null or empty");
        }

        if (reporter == null) {
            reporter = A4Reporter.NOP;
        }

        try {
            // Parse the model from string
            CompModule world = CompUtil.parseEverything_fromString(reporter, modelContent);
            
            // Get all commands from the module
            List<Command> commands = world.getAllCommands();
            
            return ModelLoadResult.success(world, commands);
            
        } catch (Err err) {
            return ModelLoadResult.error("Parse error: " + err.getMessage());
        } catch (Exception ex) {
            return ModelLoadResult.error("Unexpected error: " + ex.getMessage());
        }
    }

    /**
     * Load and parse an Alloy model from multiple files with cross-file references.
     * This method supports the 'open' directive by creating temporary files that
     * can be resolved by Alloy's module system.
     * 
     * @param fileMap Map of filename to file content
     * @param mainFile The main file to parse (must exist in fileMap)
     * @param reporter The reporter for diagnostics (can be null)
     * @return ModelLoadResult containing the parsed module or error information
     */
    public static ModelLoadResult loadModelFromFiles(Map<String, String> fileMap, String mainFile, A4Reporter reporter) {
        if (fileMap == null || fileMap.isEmpty()) {
            return ModelLoadResult.error("File map cannot be null or empty");
        }

        if (mainFile == null || mainFile.trim().isEmpty()) {
            return ModelLoadResult.error("Main file cannot be null or empty");
        }

        if (!fileMap.containsKey(mainFile)) {
            return ModelLoadResult.error("Main file '" + mainFile + "' not found in provided files");
        }

        if (reporter == null) {
            reporter = A4Reporter.NOP;
        }

        Path tempDir = null;
        try {
            // Create temporary directory structure
            tempDir = Files.createTempDirectory("alloy-grpc-");
            
            // Write all files to temporary directory, preserving relative paths
            Map<String, String> fileCache = new HashMap<>();
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String filename = entry.getKey();
                String content = entry.getValue();
                
                // Create parent directories if needed
                Path filePath = tempDir.resolve(filename);
                Files.createDirectories(filePath.getParent());
                
                // Write file content
                Files.write(filePath, content.getBytes("UTF-8"));
                
                // Add to file cache for CompUtil
                fileCache.put(filename, content);
            }
            
            // Get the absolute path of the main file
            Path mainFilePath = tempDir.resolve(mainFile);
            String mainFileAbsolutePath = mainFilePath.toAbsolutePath().toString();
            
            // Parse using CompUtil with file cache
            CompModule world = CompUtil.parseEverything_fromFile(reporter, fileCache, mainFileAbsolutePath);
            
            // Get all commands from the module
            List<Command> commands = world.getAllCommands();
            
            return ModelLoadResult.success(world, commands);
            
        } catch (Err err) {
            return ModelLoadResult.error("Parse error: " + err.getMessage());
        } catch (IOException ex) {
            return ModelLoadResult.error("IO error while creating temporary files: " + ex.getMessage());
        } catch (Exception ex) {
            return ModelLoadResult.error("Unexpected error: " + ex.getMessage());
        } finally {
            // Clean up temporary directory
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir.toFile());
                } catch (Exception e) {
                    // Log warning but don't fail the operation
                    System.err.println("Warning: Failed to delete temporary directory: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     * 
     * @param directory The directory to delete
     * @throws IOException if deletion fails
     */
    private static void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete " + directory.getAbsolutePath());
        }
    }

    /**
     * Find a specific command by name or index.
     * 
     * @param commands List of available commands
     * @param commandSpec Command specification (name or index)
     * @return Optional containing the found command
     */
    public static Optional<Command> findCommand(List<Command> commands, String commandSpec) {
        if (commandSpec == null || commandSpec.trim().isEmpty()) {
            // Return first command if no specific command requested
            return commands.isEmpty() ? Optional.empty() : Optional.of(commands.get(0));
        }

        String spec = commandSpec.trim();

        // Try to parse as index first
        try {
            int index = Integer.parseInt(spec);
            if (index >= 0 && index < commands.size()) {
                return Optional.of(commands.get(index));
            }
        } catch (NumberFormatException e) {
            // Not a number, try as command name
        }

        // Try to find by command label
        return commands.stream()
            .filter(cmd -> cmd.label.equals(spec))
            .findFirst();
    }

    /**
     * Validate that a model has at least one command.
     * 
     * @param commands List of commands from the model
     * @return Validation result
     */
    public static ValidationResult validateCommands(List<Command> commands) {
        if (commands == null || commands.isEmpty()) {
            return ValidationResult.error("Model must contain at least one command (run or check)");
        }
        return ValidationResult.success();
    }

    /**
     * Validate model content for basic requirements.
     * 
     * @param modelContent The model content to validate
     * @return Validation result
     */
    public static ValidationResult validateModelContent(String modelContent) {
        if (modelContent == null) {
            return ValidationResult.error("Model content cannot be null");
        }

        if (modelContent.trim().isEmpty()) {
            return ValidationResult.error("Model content cannot be empty");
        }

        // Check for basic Alloy syntax elements
        String content = modelContent.toLowerCase();
        if (!content.contains("sig") && !content.contains("run") && !content.contains("check")) {
            return ValidationResult.error("Model must contain at least one signature, run, or check command");
        }

        return ValidationResult.success();
    }

    /**
     * Result of validation operations.
     */
    public static class ValidationResult {
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

    /**
     * Create a simple reporter that collects error messages.
     */
    public static class CollectingReporter extends A4Reporter {
        private final StringBuilder errors = new StringBuilder();
        private final StringBuilder warnings = new StringBuilder();

        @Override
        public void warning(edu.mit.csail.sdg.alloy4.ErrorWarning msg) {
            if (warnings.length() > 0) {
                warnings.append("\n");
            }
            warnings.append("Warning: ").append(msg.toString());
        }

        public String getErrors() {
            return errors.toString();
        }

        public String getWarnings() {
            return warnings.toString();
        }

        public boolean hasErrors() {
            return errors.length() > 0;
        }

        public boolean hasWarnings() {
            return warnings.length() > 0;
        }

        public String getAllMessages() {
            StringBuilder all = new StringBuilder();
            if (hasErrors()) {
                all.append(errors.toString());
            }
            if (hasWarnings()) {
                if (all.length() > 0) {
                    all.append("\n");
                }
                all.append(warnings.toString());
            }
            return all.toString();
        }
    }
}
