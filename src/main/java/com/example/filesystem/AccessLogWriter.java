package com.example.filesystem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Handles writing access errors and skipped files to a log file.
 * Provides proper resource management and fallback to console output.
 */
public class AccessLogWriter implements AutoCloseable {
    
    private PrintWriter writer;
    private boolean useConsole;
    private Path logFilePath;
    
    /**
     * Creates a new AccessLogWriter that writes to a file.
     * 
     * @param directoryPath The directory being scanned
     * @param maxDepth The maximum scan depth
     * @param skipHidden Whether hidden files are being skipped
     * @throws IOException if the log file cannot be created
     */
    public AccessLogWriter(Path directoryPath, int maxDepth, boolean skipHidden) throws IOException {
        String logFilename = FilenameGenerator.generateLogFilename(directoryPath.toString());
        this.logFilePath = Paths.get(getWorkingDirectory(), logFilename);
        
        try {
            // Create log file (truncate if exists)
            this.writer = new PrintWriter(new FileWriter(logFilePath.toFile(), false));
            this.useConsole = false;
            
            // Write header information
            writer.println("Filesystem scan access errors and skipped files log");
            writer.println("Scan started at: " + LocalDateTime.now());
            writer.println("Directory: " + directoryPath.toAbsolutePath());
            writer.println("Max depth: " + maxDepth);
            writer.println("Skip hidden: " + skipHidden);
            writer.println("===============================================");
            writer.flush();
            
            System.out.println("Access errors and skipped files will be logged to: " + logFilePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Warning: Could not create access log file: " + e.getMessage());
            System.err.println("Access errors and skipped files will be printed to console instead.");
            this.writer = null;
            this.useConsole = true;
            throw e; // Re-throw to let caller handle fallback
        }
    }
    
    /**
     * Creates a console-only AccessLogWriter as fallback.
     */
    public AccessLogWriter() {
        this.writer = null;
        this.useConsole = true;
        this.logFilePath = null;
        System.err.println("Access errors and skipped files will be printed to console.");
    }
    
    /**
     * Logs an error message with timestamp.
     * 
     * @param message The error message to log
     */
    public void logError(String message) {
        String timestampedMessage = LocalDateTime.now() + " - " + message;
        
        if (useConsole) {
            System.err.println(timestampedMessage);
        } else if (writer != null) {
            writer.println(timestampedMessage);
            writer.flush();
        }
    }
    
    /**
     * Gets the path to the log file if one was created.
     * 
     * @return The log file path, or null if using console output
     */
    public Path getLogFilePath() {
        return logFilePath;
    }
    
    /**
     * Returns true if this writer is using console output instead of a file.
     */
    public boolean isUsingConsole() {
        return useConsole;
    }
    
    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
    
    /**
     * Returns the current working directory
     */
    private static String getWorkingDirectory() {
        return System.getProperty("user.dir");
    }
}
