package com.example.filesystem;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for generating safe filesystem names for database and log files.
 * Handles cross-platform filename restrictions and special cases.
 */
public class FilenameGenerator {
    
    /**
     * Flag to indicate if we're running on Windows
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    
    /**
     * Generates a filename for the Hyper database file based on the directory path.
     * 
     * @param directoryPath The path of the directory to scan
     * @return A safe database filename with .hyper extension
     */
    public static String generateDatabaseFilename(String directoryPath) {
        return generateSafeFilename(directoryPath, "_metadata.hyper");
    }
    
    /**
     * Generates a filename for the access errors log file based on the directory path.
     * 
     * @param directoryPath The path of the directory being scanned
     * @return A safe log filename with .log extension
     */
    public static String generateLogFilename(String directoryPath) {
        return generateSafeFilename(directoryPath, "_access_errors.log");
    }
    
    /**
     * Generates a safe filename based on directory path and suffix.
     * Handles special cases like root directory, special characters, and long paths.
     *
     * @param directoryPath The path of the directory
     * @param suffix The suffix to append (e.g., "_metadata.hyper", "_access_errors.log")
     * @return A filename string that's safe for the filesystem
     */
    private static String generateSafeFilename(String directoryPath, String suffix) {
        Path path = Paths.get(directoryPath);
        String fileName;

        // Handle root directory case
        if (path.getNameCount() == 0 || directoryPath.equals("/")) {
            fileName = "root";
        } else {
            // Get the last component of the path (directory name)
            fileName = path.getFileName().toString();
        }

        // Handle empty or special directory names
        if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
            fileName = "directory";
        }

        // Replace invalid filename characters with underscores
        // This covers most common issues across Windows, macOS, and Linux
        fileName = fileName.replaceAll("[<>:\"/\\\\|?*]", "_");

        // Handle Windows reserved names
        if (IS_WINDOWS) {
            String upperName = fileName.toUpperCase();
            if (upperName.matches("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) {
                fileName = fileName + "_dir";
            }
        }

        // Truncate if too long (keeping some space for the suffix)
        if (fileName.length() > 100) {
            fileName = fileName.substring(0, 100);
        }

        // Remove leading/trailing dots and spaces
        fileName = fileName.replaceAll("^[.\\s]+|[.\\s]+$", "");

        // If somehow we end up with an empty string, use a default
        if (fileName.isEmpty()) {
            fileName = "filesystem_scan";
        }

        return fileName + suffix;
    }
}
