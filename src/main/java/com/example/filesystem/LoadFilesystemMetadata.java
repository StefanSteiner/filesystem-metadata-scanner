package com.example.filesystem;

import com.tableau.hyperapi.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.tableau.hyperapi.Nullability.*;
import static com.tableau.hyperapi.SqlType.*;
import static java.util.Arrays.asList;

/**
 * An example demonstrating loading filesystem metadata into a new Hyper file
 * This example walks through a directory structure and captures file metadata
 * including name, path, size, owner, and whether it's a directory
 */
public class LoadFilesystemMetadata {

    /**
     * Flag to indicate if we're running on Windows (initialized once for performance)
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    /**
     * The filesystem metadata table
     */
    private static TableDefinition FILESYSTEM_METADATA_TABLE = new TableDefinition(
            new TableName("FilesystemMetadata"),
            asList(
                    new TableDefinition.Column("File Name", text(), NOT_NULLABLE),
                    new TableDefinition.Column("Path", text(), NOT_NULLABLE),
                    new TableDefinition.Column("Full Path", text(), NOT_NULLABLE),
                    new TableDefinition.Column("File Size", bigInt(), NOT_NULLABLE),
                    new TableDefinition.Column("File Owner", text(), NULLABLE),
                    new TableDefinition.Column("File Extension", text(), NULLABLE),
                    new TableDefinition.Column("Is Directory", bool(), NOT_NULLABLE),
                    new TableDefinition.Column("Is Hidden", bool(), NOT_NULLABLE),
                    new TableDefinition.Column("Depth", bigInt(), NOT_NULLABLE),
                    new TableDefinition.Column("File ID", text(), NULLABLE),
                    new TableDefinition.Column("Link Type", text(), NOT_NULLABLE),
                    new TableDefinition.Column("Link Target", text(), NULLABLE),
                    new TableDefinition.Column("Creation Time", timestamp(), NULLABLE),
                    new TableDefinition.Column("Last Access Time", timestamp(), NULLABLE),
                    new TableDefinition.Column("Last Modified Time", timestamp(), NULLABLE)
            )
    );

    /**
     * The main function
     *
     * @param args The args: [directory_path] [max_depth] [--skip-hidden]
     */
    public static void main(String[] args) {
        System.out.println("EXAMPLE - Load filesystem metadata into a new Hyper file");

        // Parse command line arguments
        String directoryPath = System.getProperty("user.home"); // Default to home directory
        String hyperFilePath = null; // For --query-existing mode
        int maxDepth = 3; // Default depth
        boolean skipHidden = false; // Default to include hidden files
        boolean verbose = false; // Default to non-verbose mode
        boolean queryExisting = false; // Default to scan filesystem, not query existing

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.equals("--root")) {
                if (i + 1 < args.length) {
                    directoryPath = args[++i];
                } else {
                    System.err.println("Error: --root requires a directory path argument.");
                    System.exit(1);
                }
            } else if (arg.equals("--depth")) {
                if (i + 1 < args.length) {
                    try {
                        maxDepth = Integer.parseInt(args[++i]);
                        if (maxDepth < 1 || maxDepth > 20) {
                            System.err.println("Warning: Max depth should be between 1 and 20. Using default value of 3.");
                            maxDepth = 3;
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid max depth '" + args[i] + "'. Using default value of 3.");
                        maxDepth = 3;
                    }
                } else {
                    System.err.println("Error: --depth requires a numeric argument.");
                    System.exit(1);
                }
            } else if (arg.equals("--skip-hidden")) {
                skipHidden = true;
            } else if (arg.equals("--verbose")) {
                verbose = true;
            } else if (arg.equals("--query-existing")) {
                queryExisting = true;
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    hyperFilePath = args[++i];
                } else {
                    System.err.println("Error: --query-existing requires a hyper file path argument.");
                    System.exit(1);
                }
            } else if (arg.startsWith("--")) {
                System.err.println("Warning: Unknown option '" + arg + "' ignored.");
            } else {
                System.err.println("Warning: Unexpected argument '" + arg + "' ignored. Use --root to specify directory path.");
            }
        }

        // Display usage information
        if (queryExisting) {
            System.out.println("Usage: java LoadFilesystemMetadata --query-existing <hyper_file_path> [--verbose]");
            System.out.println("  --query-existing <hyper_file_path>: Query existing database instead of scanning filesystem");
            System.out.println("  --verbose: Show detailed analysis results (default: no detailed analysis)");
        } else {
            System.out.println("Usage: java LoadFilesystemMetadata [--root <directory_path>] [--depth <max_depth>] [--skip-hidden] [--verbose]");
            System.out.println("  --root <directory_path>: Path to start scanning from (default: user home directory)");
            System.out.println("  --depth <max_depth>: Maximum directory depth to scan (default: 3, range: 1-20)");
            System.out.println("  --skip-hidden: Skip hidden and system files (default: include hidden files)");
            System.out.println("  --verbose: Show detailed analysis results (default: no detailed analysis, progress always shown)");
            System.out.println("  --query-existing <hyper_file_path>: Query existing database instead of scanning filesystem");
        }
        System.out.println();

        Path metadataDatabasePath;
        if (queryExisting) {
            if (hyperFilePath == null) {
                System.err.println("Error: --query-existing requires a hyper file path to be specified.");
                System.err.println("Usage: java LoadFilesystemMetadata --query-existing <hyper_file_path> [--verbose]");
                System.exit(1);
            }
            metadataDatabasePath = Paths.get(hyperFilePath);
        } else {
            metadataDatabasePath = Paths.get(getWorkingDirectory(), generateDatabaseFilename(directoryPath));
        }

        // Optional process parameters
        Map<String, String> processParameters = new HashMap<>();
        processParameters.put("log_file_max_count", "2");
        processParameters.put("log_file_size_limit", "100M");

        // Handle query-existing mode
        if (queryExisting) {
            if (!Files.exists(metadataDatabasePath)) {
                System.err.println("Error: Database file '" + metadataDatabasePath + "' does not exist.");
                System.err.println("Please check the file path and try again.");
                System.exit(1);
            }
            
            System.out.println("Querying existing database: " + metadataDatabasePath.toAbsolutePath());
            System.out.println("Verbose mode: " + verbose);
            System.out.println();

            // Starts the Hyper Process with telemetry enabled
            try (HyperProcess process = new HyperProcess(Telemetry.SEND_USAGE_DATA_TO_TABLEAU, "filesystem_metadata_example", processParameters)) {
                // Optional connection parameters
                Map<String, String> connectionParameters = new HashMap<>();
                connectionParameters.put("lc_time", "en_US");

                // Open existing Hyper file for querying
                try (Connection connection = new Connection(process.getEndpoint(),
                        metadataDatabasePath.toString(),
                        CreateMode.NONE,
                        connectionParameters)) {

                    // Query and display results from existing database only if verbose
                    if (verbose) {
                        displaySampleResults(connection);
                        
                        // Show the difference between Path and Full File Path columns
                        demonstratePathColumns(connection);
                    } else {
                        System.out.println("Database queried successfully. Use --verbose to see detailed analysis results.");
                    }
                }
                System.out.println("The connection to the Hyper file has been closed");
            }
            System.out.println("The Hyper process has been shut down");
            return; // Exit early, don't do filesystem scanning
        }

        // Starts the Hyper Process with telemetry enabled
        try (HyperProcess process = new HyperProcess(Telemetry.SEND_USAGE_DATA_TO_TABLEAU, "filesystem_metadata_example", processParameters)) {
            // Optional connection parameters
            Map<String, String> connectionParameters = new HashMap<>();
            connectionParameters.put("lc_time", "en_US");

            // Creates new Hyper file "filesystem_metadata.hyper"
            try (Connection connection = new Connection(process.getEndpoint(),
                    metadataDatabasePath.toString(),
                    CreateMode.CREATE_AND_REPLACE,
                    connectionParameters)) {

                // Create the table
                connection.getCatalog().createTable(FILESYSTEM_METADATA_TABLE);

                // Choose a directory to scan
                Path directoryToScan = Paths.get(directoryPath);
                if (!Files.exists(directoryToScan)) {
                    System.err.println("Error: Directory '" + directoryPath + "' does not exist.");
                    System.exit(1);
                }
                if (!Files.isDirectory(directoryToScan)) {
                    System.err.println("Error: '" + directoryPath + "' is not a directory.");
                    System.exit(1);
                }

                System.out.println("Scanning directory: " + directoryToScan.toAbsolutePath());
                System.out.println("Maximum depth: " + maxDepth);
                System.out.println("Skip hidden files: " + skipHidden);
                System.out.println("Verbose mode: " + verbose);
                System.out.println();

                // Walk through the directory and insert metadata with timing
                long startTime = System.nanoTime();
                long totalFiles = walkDirectoryAndInsertMetadata(connection, directoryToScan, maxDepth, skipHidden, verbose);
                long endTime = System.nanoTime();
                
                // Calculate timing metrics
                double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
                double filesPerSecond = totalFiles / durationSeconds;
                
                System.out.println("=== SCANNING PERFORMANCE ===");
                System.out.println("Total files and directories processed: " + totalFiles);
                System.out.printf("Scanning completed in: %.2f seconds%n", durationSeconds);
                System.out.printf("Processing rate: %.1f items/second%n", filesPerSecond);
                System.out.println();

                // Query and display some results only if verbose
                if (verbose) {
                    displaySampleResults(connection);
                    
                    // Show the difference between Path and Full File Path columns
                    demonstratePathColumns(connection);
                } else {
                    System.out.println("Filesystem scanning completed successfully. Use --verbose to see detailed analysis results.");
                }
            }
            System.out.println("The connection to the Hyper file has been closed");
        }
        System.out.println("The Hyper process has been shut down");
    }

    /**
     * Walks through a directory and inserts file metadata into the database
     *
     * @param connection The database connection
     * @param directoryPath The directory to scan
     * @param maxDepth The maximum depth to scan
     * @param skipHidden Whether to skip hidden and system files
     * @param verbose Whether to show detailed analysis results (progress is always shown)
     * @return The number of files processed
     */
    private static long walkDirectoryAndInsertMetadata(Connection connection, Path directoryPath, int maxDepth, boolean skipHidden, boolean verbose) {
        AtomicLong fileCount = new AtomicLong(0);
        AtomicLong directoryCount = new AtomicLong(0);

        System.out.println("Starting filesystem scan...");
        
        // Create log file for access errors
        String logFilename = generateLogFilename(directoryPath.toString());
        Path logFilePath = Paths.get(getWorkingDirectory(), logFilename);
        final PrintWriter[] accessLogWriterArray = new PrintWriter[1]; // Use array to work around effectively final requirement
        
        try {
            // Create log file (truncate if exists)
            accessLogWriterArray[0] = new PrintWriter(new FileWriter(logFilePath.toFile(), false));
            accessLogWriterArray[0].println("Filesystem scan access errors and skipped files log");
            accessLogWriterArray[0].println("Scan started at: " + java.time.LocalDateTime.now());
            accessLogWriterArray[0].println("Directory: " + directoryPath.toAbsolutePath());
            accessLogWriterArray[0].println("Max depth: " + maxDepth);
            accessLogWriterArray[0].println("Skip hidden: " + skipHidden);
            accessLogWriterArray[0].println("===============================================");
            accessLogWriterArray[0].flush();
            
            System.out.println("Access errors and skipped files will be logged to: " + logFilePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Warning: Could not create access log file: " + e.getMessage());
            System.err.println("Access errors and skipped files will be printed to console instead.");
        }
        
        // Setup progress reporting (always enabled during scanning)
        ScheduledExecutorService progressReporter = Executors.newScheduledThreadPool(1);
        progressReporter.scheduleAtFixedRate(() -> {
            long totalItems = fileCount.get();
            long totalDirs = directoryCount.get();
            long totalFiles = totalItems - totalDirs;
            System.out.println("Progress: " + totalItems + " items processed (" + totalDirs + " dirs, " + totalFiles + " files)...");
        }, 5, 5, TimeUnit.SECONDS);
        
        try (Inserter inserter = new Inserter(connection, FILESYSTEM_METADATA_TABLE)) {
            long scanStartTime = System.nanoTime();
            
            // Use a custom FileVisitor that can handle permission errors gracefully
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
                private int depth = -1; // Start at -1 so root directory is depth 0

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    depth++;
                    
                    if (depth >= maxDepth) {
                        depth--; // Decrement since we're not processing this directory
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    try {
                        boolean isHidden = isHiddenFile(dir);
                        
                        // Skip hidden directories if requested
                        if (skipHidden && isHidden) {
                            depth--; // Decrement since we're not processing this directory
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        
                        // Check if we should traverse this directory (not a symlink, mount point, or junction)
                        if (!shouldTraverseDirectory(dir)) {
                            // Log the reason for skipping
                            String skipMessage;
                            if (isSymbolicLink(dir)) {
                                skipMessage = "Skipping symbolic link: " + dir;
                            } else if (isMountPointOrJunction(dir)) {
                                skipMessage = "Skipping mount point/junction: " + dir;
                            } else {
                                skipMessage = "Skipping directory: " + dir;
                            }
                            
                            if (accessLogWriterArray[0] != null) {
                                accessLogWriterArray[0].println(java.time.LocalDateTime.now() + " - " + skipMessage);
                                accessLogWriterArray[0].flush();
                            } else {
                                System.out.println("WARNING: " + skipMessage);
                            }
                            
                            // Still record the directory in the database, but don't traverse into it
                            insertFileMetadata(inserter, dir, depth);
                            fileCount.incrementAndGet();
                            directoryCount.incrementAndGet();
                            depth--; // Decrement since we're not processing this directory's contents
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        
                        insertFileMetadata(inserter, dir, depth);
                        fileCount.incrementAndGet();
                        directoryCount.incrementAndGet();
                    } catch (IOException | SecurityException e) {
                        String errorMessage = "Skipping directory due to access restrictions: " + dir.getFileName() + " - " + e.getMessage();
                        if (accessLogWriterArray[0] != null) {
                            accessLogWriterArray[0].println(java.time.LocalDateTime.now() + " - " + errorMessage);
                            accessLogWriterArray[0].flush();
                        } else {
                            System.err.println(errorMessage);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        boolean isHidden = isHiddenFile(file);
                        
                        // Skip hidden files if requested
                        if (skipHidden && isHidden) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        // Check if it's a symbolic link (files can be symlinks too)
                        if (isSymbolicLink(file)) {
                            String skipMessage = "Skipping symbolic link file: " + file;
                            if (accessLogWriterArray[0] != null) {
                                accessLogWriterArray[0].println(java.time.LocalDateTime.now() + " - " + skipMessage);
                                accessLogWriterArray[0].flush();
                            } else {
                                System.out.println("WARNING: " + skipMessage);
                            }
                            // Still record the symlink in the database
                            insertFileMetadata(inserter, file, depth);
                            fileCount.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }
                        
                        insertFileMetadata(inserter, file, depth);
                        fileCount.incrementAndGet();
                    } catch (IOException | SecurityException e) {
                        String errorMessage = "Skipping file due to access restrictions: " + file.getFileName() + " - " + e.getMessage();
                        if (accessLogWriterArray[0] != null) {
                            accessLogWriterArray[0].println(java.time.LocalDateTime.now() + " - " + errorMessage);
                            accessLogWriterArray[0].flush();
                        } else {
                            System.err.println(errorMessage);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    String errorMessage = "Access denied to: " + file.getFileName() + " - " + exc.getMessage();
                    if (accessLogWriterArray[0] != null) {
                        accessLogWriterArray[0].println(java.time.LocalDateTime.now() + " - " + errorMessage);
                        accessLogWriterArray[0].flush();
                    } else {
                        System.err.println(errorMessage);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }
            });
            
            long scanEndTime = System.nanoTime();
            double scanDurationSeconds = (scanEndTime - scanStartTime) / 1_000_000_000.0;
            
            System.out.println("Filesystem scan completed, inserting data into database...");
            
            long insertStartTime = System.nanoTime();
            inserter.execute();
            long insertEndTime = System.nanoTime();
            double insertDurationSeconds = (insertEndTime - insertStartTime) / 1_000_000_000.0;
            
            System.out.printf("Filesystem scan time: %.2f seconds (%.1f items/sec)%n", 
                            scanDurationSeconds, fileCount.get() / scanDurationSeconds);
            System.out.printf("Database insertion time: %.2f seconds%n", insertDurationSeconds);
            System.out.printf("Found %d directories and %d files%n", 
                            directoryCount.get(), fileCount.get() - directoryCount.get());
            
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        } finally {
            // Close the access log writer
            if (accessLogWriterArray[0] != null) {
                accessLogWriterArray[0].println("===============================================");
                accessLogWriterArray[0].println("Scan completed at: " + java.time.LocalDateTime.now());
                accessLogWriterArray[0].close();
            }
            
            // Shutdown progress reporter
            progressReporter.shutdown();
            try {
                if (!progressReporter.awaitTermination(1, TimeUnit.SECONDS)) {
                    progressReporter.shutdownNow();
                }
            } catch (InterruptedException e) {
                progressReporter.shutdownNow();
            }
        }

        return fileCount.get();
    }

    /**
     * Inserts metadata for a single file into the database
     *
     * @param inserter The database inserter
     * @param filePath The file path
     * @param depth The depth of the file/directory in the tree
     * @throws IOException If there's an error reading file attributes
     */
    private static void insertFileMetadata(Inserter inserter, Path filePath, int depth) throws IOException {
        Path fileNamePath = filePath.getFileName();
        String fileName = fileNamePath != null ? fileNamePath.toString() : filePath.toString(); // Handle root directory case
        String directoryPath = filePath.getParent() != null ? filePath.getParent().toAbsolutePath().toString() : "";
        String fullFilePath = filePath.toAbsolutePath().toString();
        long fileSize = Files.isDirectory(filePath) ? 0 : Files.size(filePath);
        boolean isDirectory = Files.isDirectory(filePath);
        String fileOwner = getFileOwner(filePath);
        String fileExtension = getFileExtension(filePath);
        boolean isHidden = isHiddenFile(filePath);
        
        // Get file ID
        String fileId = getFileId(filePath);
        
        // Get link information
        String linkType = getLinkType(filePath);
        String linkTarget = getLinkTarget(filePath);
        
        // Get file timestamps
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        LocalDateTime creationTime = convertFileTimeToLocalDateTime(attrs.creationTime());
        LocalDateTime lastAccessTime = convertFileTimeToLocalDateTime(attrs.lastAccessTime());
        LocalDateTime lastModifiedTime = convertFileTimeToLocalDateTime(attrs.lastModifiedTime());

        inserter.add(fileName)
                .add(directoryPath)
                .add(fullFilePath)
                .add(fileSize)
                .add(fileOwner)
                .add(fileExtension)
                .add(isDirectory)
                .add(isHidden)
                .add(depth)
                .add(fileId != null ? fileId : "") // Handle null by using empty string
                .add(linkType)
                .add(linkTarget != null ? linkTarget : "") // Handle null by using empty string
                .add(creationTime)
                .add(lastAccessTime)
                .add(lastModifiedTime)
                .endRow();
    }

    /**
     * Gets the file owner, handling different operating systems
     *
     * @param filePath The file path
     * @return The file owner name, or "Unknown" if not available
     */
    private static String getFileOwner(Path filePath) {
        try {
            // Try to get POSIX attributes (Unix/Linux/Mac)
            PosixFileAttributeView posixView = Files.getFileAttributeView(filePath, PosixFileAttributeView.class);
            if (posixView != null) {
                PosixFileAttributes posixAttrs = posixView.readAttributes();
                UserPrincipal owner = posixAttrs.owner();
                return owner.getName();
            }

            // Fallback to generic owner
            UserPrincipal owner = Files.getOwner(filePath);
            return owner.getName();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    /**
     * Gets the unique file ID for the given file path
     *
     * @param filePath The file path
     * @return The file ID as a string, or null if not available
     */
    private static String getFileId(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            Object fileKey = attrs.fileKey();
            
            if (fileKey != null) {
                return fileKey.toString();
            }
            
            // Try to get more specific file ID based on OS
            if (IS_WINDOWS) {
                // On Windows, try to get the file index
                try {
                    DosFileAttributes dosAttrs = Files.readAttributes(filePath, DosFileAttributes.class);
                    // File key should contain the file index information
                    if (dosAttrs != null && fileKey != null) {
                        return fileKey.toString();
                    }
                } catch (Exception e) {
                    // Fall through to other methods
                }
            } else {
                // On Unix/Linux/Mac, try to get inode number
                try {
                    PosixFileAttributeView posixView = Files.getFileAttributeView(filePath, PosixFileAttributeView.class);
                    if (posixView != null) {
                        PosixFileAttributes posixAttrs = posixView.readAttributes();
                        // The file key often contains the inode information
                        Object posixFileKey = posixAttrs.fileKey();
                        if (posixFileKey != null) {
                            return posixFileKey.toString();
                        }
                    }
                } catch (Exception e) {
                    // Fall through to return null
                }
            }
            
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Determines if a file or directory is hidden
     *
     * @param filePath The file path to check
     * @return True if the file is hidden, false otherwise
     */
    private static boolean isHiddenFile(Path filePath) {
        try {
            Path fileName = filePath.getFileName();
            
            // Handle special case where getFileName() returns null (e.g., root directory "/")
            if (fileName == null) {
                return false; // Root directory and similar paths are not considered hidden
            }
            
            String fileNameStr = fileName.toString();
            
            // Check if file starts with dot (Unix/Linux/Mac hidden files)
            if (fileNameStr.startsWith(".")) {
                return true;
            }
            
            // Check using Files.isHidden() (handles Windows hidden attribute and other OS-specific rules)
            return Files.isHidden(filePath);
        } catch (IOException e) {
            // If we can't determine, assume not hidden
            return false;
        }
    }

    /**
     * Extracts the file extension from a filename (without the leading dot)
     *
     * @param filePath The file path
     * @return The file extension without the leading dot, or empty string if no extension or if it's a directory
     */
    private static String getFileExtension(Path filePath) {
        // Return empty string for directories
        if (Files.isDirectory(filePath)) {
            return "";
        }
        
        Path fileName = filePath.getFileName();
        
        // Handle special case where getFileName() returns null (e.g., root directory "/")
        if (fileName == null) {
            return ""; // No extension for paths without filename
        }
        
        String fileNameStr = fileName.toString();
        
        // Find the last dot in the filename
        int lastDotIndex = fileNameStr.lastIndexOf('.');
        
        // If no dot found, or dot is at the beginning (hidden file), or dot is at the end, return empty string
        if (lastDotIndex == -1 || lastDotIndex == 0 || lastDotIndex == fileNameStr.length() - 1) {
            return "";
        }
        
        // Return the extension without the leading dot
        return fileNameStr.substring(lastDotIndex + 1);
    }

    /**
     * Determines if a path is a symbolic link
     *
     * @param filePath The file path to check
     * @return True if the path is a symbolic link, false otherwise
     */
    private static boolean isSymbolicLink(Path filePath) {
        return Files.isSymbolicLink(filePath);
    }

    /**
     * Determines if a path is a mount point (Unix/Linux/Mac) or junction (Windows)
     *
     * @param filePath The file path to check
     * @return True if the path is a mount point or junction, false otherwise
     */
    private static boolean isMountPointOrJunction(Path filePath) {
        try {
            // Check if it's a directory first
            if (!Files.isDirectory(filePath)) {
                return false;
            }
            
            // On Windows, junctions are directories that are also symbolic links
            if (IS_WINDOWS) {
                // A junction on Windows appears as both a directory and a symbolic link
                if (Files.isSymbolicLink(filePath) && Files.isDirectory(filePath)) {
                    return true;
                }
                // Also check for other types of reparse points by looking at DOS attributes
                try {
                    DosFileAttributes dosAttrs = Files.readAttributes(filePath, DosFileAttributes.class);
                    // Check if it's a special directory (system or hidden) that might be a junction
                    if (dosAttrs.isOther()) {
                        return true;
                    }
                } catch (Exception e) {
                    // DosFileAttributes not available, continue with other checks
                }
            }
            
            // For Unix/Linux/Mac, check if parent device differs from current device
            // This is a heuristic that works for most mount points
            Path parent = filePath.getParent();
            if (parent != null) {
                try {
                    // Compare file system of directory with its parent
                    FileStore currentStore = Files.getFileStore(filePath);
                    FileStore parentStore = Files.getFileStore(parent);
                    
                    // If they're different file stores, it's likely a mount point
                    return !currentStore.equals(parentStore);
                } catch (IOException e) {
                    // If we can't determine, assume not a mount point
                    return false;
                }
            }
            
            return false;
        } catch (Exception e) {
            // If we can't determine, assume not a mount point
            return false;
        }
    }

    /**
     * Checks if a directory should be traversed (not a symlink, mount point, or junction)
     *
     * @param dirPath The directory path to check
     * @return True if the directory should be traversed, false otherwise
     */
    private static boolean shouldTraverseDirectory(Path dirPath) {
        if (isSymbolicLink(dirPath)) {
            return false;
        }
        
        if (isMountPointOrJunction(dirPath)) {
            return false;
        }
        
        return true;
    }

    /**
     * Determines the link type of a file or directory
     *
     * @param filePath The file path to check
     * @return String representing the link type: "NONE", "SYMLINK", "MOUNTPOINT", or "JUNCTION"
     */
    private static String getLinkType(Path filePath) {
        if (isSymbolicLink(filePath)) {
            return "SYMLINK";
        }
        
        if (Files.isDirectory(filePath) && isMountPointOrJunction(filePath)) {
            // Try to determine if it's a mount point or junction
            if (IS_WINDOWS) {
                return "JUNCTION";
            } else {
                return "MOUNTPOINT";
            }
        }
        
        return "NONE";
    }

    /**
     * Gets the target of a link (what it points to)
     *
     * @param filePath The file path to check
     * @return String representing the target path, or null if not a link or target cannot be determined
     */
    private static String getLinkTarget(Path filePath) {
        try {
            if (isSymbolicLink(filePath)) {
                // For symbolic links, we can read the target directly
                Path target = Files.readSymbolicLink(filePath);
                return target.toString();
            }
            
            if (Files.isDirectory(filePath) && isMountPointOrJunction(filePath)) {
                // For mount points and junctions, try to get useful information
                if (IS_WINDOWS) {
                    // For Windows junctions, try to resolve the target
                    try {
                        Path resolved = filePath.toRealPath();
                        if (!resolved.equals(filePath)) {
                            return resolved.toString();
                        }
                    } catch (IOException e) {
                        // If we can't resolve, try to get file store information
                        try {
                            FileStore store = Files.getFileStore(filePath);
                            return store.name() + " (" + store.type() + ")";
                        } catch (IOException ex) {
                            return "Unknown junction target";
                        }
                    }
                } else {
                    // For Unix/Linux/Mac mount points, get file store information
                    try {
                        FileStore store = Files.getFileStore(filePath);
                        return store.name() + " (" + store.type() + ")";
                    } catch (IOException e) {
                        return "Unknown mount point";
                    }
                }
            }
        } catch (IOException e) {
            // If we can't determine the target, return null for links that we can't resolve
            if (isSymbolicLink(filePath) || isMountPointOrJunction(filePath)) {
                return "Unknown target";
            }
            return null;
        }
        
        // Return null for regular files (not links)
        return null;
    }

    /**
     * Converts a FileTime to LocalDateTime
     *
     * @param fileTime The file time to convert
     * @return LocalDateTime object, or null if conversion fails
     */
    private static LocalDateTime convertFileTimeToLocalDateTime(FileTime fileTime) {
        if (fileTime == null) {
            return null;
        }
        try {
            Instant instant = fileTime.toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Displays sample results from the database
     *
     * @param connection The database connection
     */
    private static void displaySampleResults(Connection connection) {
        System.out.println("\n=== SAMPLE RESULTS ===");

        // Get total count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName())) {
            if (result.nextRow()) {
                System.out.println("Total entries in database: " + result.getLong(0));
            }
        }

        // Get directory count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + " WHERE \"Is Directory\" = true")) {
            if (result.nextRow()) {
                System.out.println("Total directories: " + result.getLong(0));
            }
        }

        // Get file count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + " WHERE \"Is Directory\" = false")) {
            if (result.nextRow()) {
                System.out.println("Total files: " + result.getLong(0));
            }
        }

        // Get hidden file count
        try (Result result = connection.executeQuery("SELECT COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + " WHERE \"Is Hidden\" = true")) {
            if (result.nextRow()) {
                System.out.println("Hidden files/directories: " + result.getLong(0));
            }
        }

        // Get largest files
        System.out.println("\nLargest files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Size\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
                " WHERE \"Is Directory\" = false ORDER BY \"File Size\" DESC LIMIT 5")) {
            while (result.nextRow()) {
                System.out.println("  " + result.getString(0) + " (" + formatFileSize(result.getLong(1)) + ") - " + result.getString(2));
            }
        }

        // Get sample of directories
        System.out.println("\nSample directories:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Owner\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
                " WHERE \"Is Directory\" = true ORDER BY \"File Name\" LIMIT 5")) {
            while (result.nextRow()) {
                System.out.println("  " + result.getString(0) + " (Owner: " + result.getString(1) + ") - " + result.getString(2));
            }
        }

        // Get recently modified files
        System.out.println("\nRecently modified files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Last Modified Time\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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

        // Get sample hidden files if they exist
        System.out.println("\nSample hidden files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File Size\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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

        // Get depth distribution
        System.out.println("\nDepth distribution:");
        try (Result result = connection.executeQuery(
                "SELECT \"Depth\", COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
                " GROUP BY \"Depth\" ORDER BY \"Depth\"")) {
            while (result.nextRow()) {
                long depth = result.getLong(0);
                long count = result.getLong(1);
                System.out.println("  Depth " + depth + ": " + count + " items");
            }
        }

        // Get deepest files
        System.out.println("\nDeepest files:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Depth\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
                " WHERE \"Is Directory\" = false ORDER BY \"Depth\" DESC LIMIT 5")) {
            while (result.nextRow()) {
                String fileName = result.getString(0);
                long depth = result.getLong(1);
                String filePath = result.getString(2);
                System.out.println("  " + fileName + " (Depth: " + depth + ") - " + filePath);
            }
        }

        // Get link information
        System.out.println("\nLink analysis:");
        try (Result result = connection.executeQuery(
                "SELECT \"Link Type\", COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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
                "SELECT \"File Name\", \"Link Type\", \"Link Target\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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

        // Get file ID statistics
        System.out.println("\nFile ID analysis:");
        try (Result result = connection.executeQuery(
                "SELECT COUNT(*) FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
                " WHERE \"File ID\" IS NOT NULL AND \"File ID\" != ''")) {
            if (result.nextRow()) {
                long countWithId = result.getLong(0);
                System.out.println("  Files with unique ID: " + countWithId);
            }
        }

        // Get sample file IDs
        System.out.println("\nSample file IDs:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"File ID\", \"Is Directory\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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

        // Get file extension analysis
        System.out.println("\nFile extension analysis:");
        try (Result result = connection.executeQuery(
                "SELECT \"File Extension\", COUNT(*) as ext_count FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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
                "SELECT \"File Name\", \"File Extension\", \"File Size\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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
     * Formats file size in human-readable format
     *
     * @param bytes The file size in bytes
     * @return Formatted string
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Demonstrates the difference between Path and Full Path columns
     */
    private static void demonstratePathColumns(Connection connection) {
        System.out.println("\n=== PATH COLUMN DEMONSTRATION ===");
        
        try (Result result = connection.executeQuery(
                "SELECT \"File Name\", \"Path\", \"Full Path\" FROM " + FILESYSTEM_METADATA_TABLE.getTableName() + 
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
     * Truncates a string to the specified length
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Returns the current working directory
     *
     * @return The inferred working directory
     */
    private static String getWorkingDirectory() {
        return System.getProperty("user.dir");
    }

    /**
     * Generates a filename for the Hyper file based on the directory path.
     * Handles special cases like root directory, special characters, and long paths.
     *
     * @param directoryPath The path of the directory to scan.
     * @return A filename string that's safe for the filesystem.
     */
    private static String generateDatabaseFilename(String directoryPath) {
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
        
        return fileName + "_metadata.hyper";
    }

    /**
     * Generates a log filename for access errors and skipped files based on the directory path.
     * Uses the same naming logic as the database filename but with .log extension.
     *
     * @param directoryPath The path of the directory being scanned.
     * @return A log filename string that's safe for the filesystem.
     */
    private static String generateLogFilename(String directoryPath) {
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
        
        return fileName + "_access_errors.log";
    }
} 