package com.example.filesystem;

import com.tableau.hyperapi.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles analysis and display of filesystem scan results from a Hyper database.
 * This class provides detailed analysis including file statistics, largest files,
 * directory samples, file extensions, and more.
 */
public class ResultsAnalyzer {
    
    private final Connection connection;
    private final TableDefinition tableDefinition;
    
    public ResultsAnalyzer(Connection connection, TableDefinition tableDefinition) {
        this.connection = connection;
        this.tableDefinition = tableDefinition;
    }
    
    /**
     * Displays comprehensive analysis results from the database
     */
    public void displaySampleResults() {
        System.out.println("\n=== SAMPLE RESULTS ===");

        displayBasicStatistics();
        displayLargestFiles();
        displaySampleDirectories();
        displayRecentlyModifiedFiles();
        displaySampleHiddenFiles();
        displayDepthDistribution();
        displayDeepestFiles();
        displayLinkAnalysis();
        displayFileIdAnalysis();
        displayFileExtensionAnalysis();
    }
    
    /**
     * Displays basic statistics about the scan results
     */
    private void displayBasicStatistics() {
        // Get total count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + tableDefinition.getTableName())) {
            if (result.nextRow()) {
                System.out.println("Total entries in database: " + result.getLong(0));
            }
        }

        // Get directory count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + tableDefinition.getTableName() + " WHERE \"Is Directory\" = true")) {
            if (result.nextRow()) {
                System.out.println("Total directories: " + result.getLong(0));
            }
        }

        // Get file count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + tableDefinition.getTableName() + " WHERE \"Is Directory\" = false")) {
            if (result.nextRow()) {
                System.out.println("Total files: " + result.getLong(0));
            }
        }

        // Get hidden file count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + tableDefinition.getTableName() + " WHERE \"Is Hidden\" = true")) {
            if (result.nextRow()) {
                System.out.println("Hidden files/directories: " + result.getLong(0));
            }
        }
    }
    
    /**
     * Displays the largest files found during the scan
     */
    private void displayLargestFiles() {
        System.out.println("\nLargest files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Size\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false ORDER BY \"File Size\" DESC LIMIT 5")) {
            while (result.nextRow()) {
                System.out.println("  " + result.getString(0) + " (" + formatFileSize(result.getLong(1)) + ") - " + result.getString(2));
            }
        }
    }
    
    /**
     * Displays a sample of directories
     */
    private void displaySampleDirectories() {
        System.out.println("\nSample directories:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Owner\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = true ORDER BY \"File Name\" LIMIT 5")) {
            while (result.nextRow()) {
                System.out.println("  " + result.getString(0) + " (Owner: " + result.getString(1) + ") - " + result.getString(2));
            }
        }
    }
    
    /**
     * Displays recently modified files
     */
    private void displayRecentlyModifiedFiles() {
        System.out.println("\nRecently modified files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Last Modified Time\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false AND \"Last Modified Time\" IS NOT NULL ORDER BY \"Last Modified Time\" DESC LIMIT 5")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            while (result.nextRow()) {
                String fileName = result.getString(0);
                // Get timestamp as object and convert to LocalDateTime
                Object timestampObj = result.getObject(1);
                LocalDateTime lastModified = null;
                if (timestampObj instanceof LocalDateTime) {
                    lastModified = (LocalDateTime) timestampObj;
                }
                String filePath = result.getString(2);
                String formattedTime = lastModified != null ? lastModified.format(formatter) : "Unknown";
                System.out.println("  " + fileName + " (Modified: " + formattedTime + ") - " + filePath);
            }
        }
    }
    
    /**
     * Displays sample hidden files if they exist
     */
    private void displaySampleHiddenFiles() {
        System.out.println("\nSample hidden files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Size\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Hidden\" = true AND \"Is Directory\" = false ORDER BY \"File Size\" DESC LIMIT 5")) {
            boolean hasHiddenFiles = false;
            while (result.nextRow()) {
                hasHiddenFiles = true;
                String fileName = result.getString(0);
                long fileSize = result.getLong(1);
                String filePath = result.getString(2);
                System.out.println("  " + fileName + " (" + formatFileSize(fileSize) + ") - " + filePath);
            }
            if (!hasHiddenFiles) {
                System.out.println("  No hidden files found (or hidden files were skipped)");
            }
        }
    }
    
    /**
     * Displays depth distribution of files and directories
     */
    private void displayDepthDistribution() {
        System.out.println("\nDepth distribution:");
        try (Result result = connection.executeQuery(
                "SELECT \"Depth\", COUNT(*) FROM " + tableDefinition.getTableName() +
                " GROUP BY \"Depth\" ORDER BY \"Depth\"")) {
            while (result.nextRow()) {
                long depth = result.getLong(0);
                long count = result.getLong(1);
                System.out.println("  Depth " + depth + ": " + count + " items");
            }
        }
    }
    
    /**
     * Displays the deepest files in the directory structure
     */
    private void displayDeepestFiles() {
        System.out.println("\nDeepest files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Depth\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false ORDER BY \"Depth\" DESC LIMIT 5")) {
            while (result.nextRow()) {
                String fileName = result.getString(0);
                long depth = result.getLong(1);
                String filePath = result.getString(2);
                System.out.println("  " + fileName + " (Depth: " + depth + ") - " + filePath);
            }
        }
    }
    
    /**
     * Displays link analysis (symlinks, mount points, etc.)
     */
    private void displayLinkAnalysis() {
        System.out.println("\nLink analysis:");
        try (Result result = connection.executeQuery(
                "SELECT \"Link Type\", COUNT(*) FROM " + tableDefinition.getTableName() +
                " GROUP BY \"Link Type\" ORDER BY COUNT(*) DESC")) {
            while (result.nextRow()) {
                String linkType = result.getString(0);
                long count = result.getLong(1);
                System.out.println("  " + linkType + ": " + count + " items");
            }
        }

        // Get sample links (symlinks, mount points, junctions)
        System.out.println("\nSample links:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Link Type\", \"Link Target\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Link Type\" != 'NONE' ORDER BY \"Link Type\", \"File Name\" LIMIT 10")) {
            boolean hasLinks = false;
            while (result.nextRow()) {
                hasLinks = true;
                String fileName = result.getString(0);
                String linkType = result.getString(1);
                String linkTarget = result.getString(2);
                String filePath = result.getString(3);
                System.out.println("  " + fileName + " (" + linkType + ") -> " +
                    (linkTarget != null ? linkTarget : "Unknown target") + " - " + filePath);
            }
            if (!hasLinks) {
                System.out.println("  No symbolic links, mount points, or junctions found");
            }
        }
    }
    
    /**
     * Displays file ID analysis
     */
    private void displayFileIdAnalysis() {
        System.out.println("\nFile ID analysis:");
        try (Result result = connection.executeQuery(
                "SELECT COUNT(*) FROM " + tableDefinition.getTableName() +
                " WHERE \"File ID\" IS NOT NULL AND \"File ID\" != ''")) {
            if (result.nextRow()) {
                long countWithId = result.getLong(0);
                System.out.println("  Files with unique ID: " + countWithId);
            }
        }

        // Get sample file IDs
        System.out.println("\nSample file IDs:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File ID\", \"Is Directory\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"File ID\" IS NOT NULL AND \"File ID\" != '' ORDER BY \"File Name\" LIMIT 5")) {
            boolean hasFileIds = false;
            while (result.nextRow()) {
                hasFileIds = true;
                String fileName = result.getString(0);
                String fileId = result.getString(1);
                Object isDirectoryObj = result.getObject(2);
                boolean isDirectory = (isDirectoryObj instanceof Boolean) ? (Boolean) isDirectoryObj : false;
                String filePath = result.getString(3);
                String type = isDirectory ? "DIR" : "FILE";
                System.out.println("  " + fileName + " [" + type + "] (ID: " + truncate(fileId, 40) + ") - " + filePath);
            }
            if (!hasFileIds) {
                System.out.println("  No file IDs available on this filesystem");
            }
        }
    }
    
    /**
     * Displays file extension analysis
     */
    private void displayFileExtensionAnalysis() {
        System.out.println("\nFile extension analysis:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Extension\", COUNT(*) as ext_count FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false AND \"File Extension\" IS NOT NULL AND \"File Extension\" != '' " +
                "GROUP BY \"File Extension\" ORDER BY ext_count DESC LIMIT 10")) {
            boolean hasExtensions = false;
            while (result.nextRow()) {
                hasExtensions = true;
                String extension = result.getString(0);
                long count = result.getLong(1);
                System.out.println("  ." + extension + ": " + count + " files");
            }
            if (!hasExtensions) {
                System.out.println("  No file extensions found");
            }
        }

        // Get sample files by extension
        System.out.println("\nSample files by extension:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Extension\", \"File Size\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false AND \"File Extension\" IS NOT NULL AND \"File Extension\" != '' " +
                "ORDER BY \"File Extension\", \"File Size\" DESC LIMIT 10")) {
            boolean hasFiles = false;
            while (result.nextRow()) {
                hasFiles = true;
                String fileName = result.getString(0);
                String extension = result.getString(1);
                long fileSize = result.getLong(2);
                String filePath = result.getString(3);
                System.out.println("  " + fileName + " (." + extension + ", " + formatFileSize(fileSize) + ") - " + filePath);
            }
            if (!hasFiles) {
                System.out.println("  No files with extensions found");
            }
        }
    }
    
    /**
     * Demonstrates the difference between Path and Full Path columns
     */
    public void demonstratePathColumns() {
        System.out.println("\n=== PATH COLUMN DEMONSTRATION ===");

        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Path\", \"Full Path\" FROM " + tableDefinition.getTableName() +
                " WHERE \"Is Directory\" = false ORDER BY \"File Name\" LIMIT 5")) {

            while (result.nextRow()) {
                String fileName = result.getString(0);
                String path = result.getString(1);
                String fullPath = result.getString(2);

                System.out.println("File: " + fileName);
                System.out.println("  Directory Path: " + path);
                System.out.println("  Full Path: " + fullPath);
                System.out.println();
            }
        }

        System.out.println("Note: 'Path' contains the directory without the filename,");
        System.out.println("      'Full Path' contains the complete path including filename.");
    }
    
    /**
     * Formats file size in human-readable format
     *
     * @param bytes The file size in bytes
     * @return Formatted string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Truncates a string to the specified length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
