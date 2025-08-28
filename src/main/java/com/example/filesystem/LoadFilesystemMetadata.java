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
     * Flag to indicate if the application should stop due to user interruption (Ctrl-C)
     */
    private static volatile boolean shouldStop = false;
    private static volatile boolean processingComplete = false;

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
            metadataDatabasePath = Paths.get(getWorkingDirectory(), FilenameGenerator.generateDatabaseFilename(directoryPath));
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
                        ResultsAnalyzer analyzer = new ResultsAnalyzer(connection, FILESYSTEM_METADATA_TABLE);
                        analyzer.displaySampleResults();
                        analyzer.demonstratePathColumns();
                    } else {
                        System.out.println("Database queried successfully. Use --verbose to see detailed analysis results.");
                    }
                }
                System.out.println("The connection to the Hyper file has been closed");
            }
            System.out.println("The Hyper process has been shut down");
            return; // Exit early, don't do filesystem scanning
        }

        // Install signal handler for graceful Ctrl-C handling
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shouldStop && !processingComplete) {
                shouldStop = true;
                System.out.println();
                System.out.println("=== INTERRUPTED BY USER (Ctrl-C) ===");
                System.out.println("Stopping filesystem scan gracefully...");
                System.out.println("Please wait for current operations to complete and results to be displayed.");
                System.out.println();

                // Interrupt the main thread to speed up the process
                mainThread.interrupt();

                // Wait for processing to complete, but with a timeout
                long startWait = System.currentTimeMillis();
                while (!processingComplete && (System.currentTimeMillis() - startWait) < 15000) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                if (!processingComplete) {
                    System.out.println("Timeout waiting for graceful shutdown, partial results may be incomplete.");
                }
            }
        }));

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

                // Walk through the directory and insert metadata
                // Performance metrics and analysis results are displayed within walkDirectoryAndInsertMetadata method
                walkDirectoryAndInsertMetadata(connection, directoryToScan, maxDepth, skipHidden, verbose);
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

        // Create log writer for access errors (using try-with-resources pattern)
        AccessLogWriter logWriter = null;
        try {
            logWriter = new AccessLogWriter(directoryPath, maxDepth, skipHidden);
        } catch (IOException e) {
            // Fallback to console logging
            logWriter = new AccessLogWriter();
        }
        final AccessLogWriter accessLogWriter = logWriter;

        // Setup progress reporting (always enabled during scanning)
        ScheduledExecutorService progressReporter = Executors.newScheduledThreadPool(1);
        progressReporter.scheduleAtFixedRate(() -> {
            if (shouldStop) {
                progressReporter.shutdown();
                return;
            }
            long totalItems = fileCount.get();
            long totalDirs = directoryCount.get();
            long totalFiles = totalItems - totalDirs;
            System.out.println("Progress: " + totalItems + " items processed (" + totalDirs + " dirs, " + totalFiles + " files)...");

            // Check for interruption more frequently during progress updates
            if (Thread.currentThread().isInterrupted()) {
                shouldStop = true;
                progressReporter.shutdown();
            }
        }, 5, 5, TimeUnit.SECONDS);

        Inserter inserter = new Inserter(connection, FILESYSTEM_METADATA_TABLE);
        long scanStartTime = System.nanoTime();

        try {
            // Use a custom FileVisitor that can handle permission errors gracefully
            Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
                    private int depth = -1; // Start at -1 so root directory is depth 0

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        // Check for user interruption (Ctrl-C) or thread interruption
                        if (shouldStop || Thread.currentThread().isInterrupted()) {
                            if (!shouldStop) {
                                shouldStop = true;
                                System.out.println();
                                System.out.println("=== INTERRUPTED BY USER (Ctrl-C) ===");
                                System.out.println("Stopping filesystem scan gracefully...");
                                System.out.println("Please wait for current operations to complete and results to be displayed.");
                                System.out.println();
                            }
                            return FileVisitResult.TERMINATE;
                        }

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

                                accessLogWriter.logError(skipMessage);

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
                            accessLogWriter.logError(errorMessage);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        // Check for user interruption (Ctrl-C) or thread interruption
                        if (shouldStop || Thread.currentThread().isInterrupted()) {
                            if (!shouldStop) {
                                shouldStop = true;
                                System.out.println();
                                System.out.println("=== INTERRUPTED BY USER (Ctrl-C) ===");
                                System.out.println("Stopping filesystem scan gracefully...");
                                System.out.println("Please wait for current operations to complete and results to be displayed.");
                                System.out.println();
                            }
                            return FileVisitResult.TERMINATE;
                        }

                        try {
                            boolean isHidden = isHiddenFile(file);

                            // Skip hidden files if requested
                            if (skipHidden && isHidden) {
                                return FileVisitResult.CONTINUE;
                            }

                            // Check if it's a symbolic link (files can be symlinks too)
                            if (isSymbolicLink(file)) {
                                String skipMessage = "Skipping symbolic link file: " + file;
                                accessLogWriter.logError(skipMessage);
                                // Still record the symlink in the database
                                insertFileMetadata(inserter, file, depth);
                                fileCount.incrementAndGet();
                                return FileVisitResult.CONTINUE;
                            }

                            insertFileMetadata(inserter, file, depth);
                            fileCount.incrementAndGet();
                        } catch (IOException | SecurityException e) {
                            String errorMessage = "Skipping file due to access restrictions: " + file.getFileName() + " - " + e.getMessage();
                            accessLogWriter.logError(errorMessage);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        String errorMessage = "Access denied to: " + file.getFileName() + " - " + exc.getMessage();
                        accessLogWriter.logError(errorMessage);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        depth--;
                        return FileVisitResult.CONTINUE;
                    }
                });

        } catch (IOException e) {
            if (shouldStop) {
                System.out.println("Filesystem scan interrupted during traversal");
            } else {
                System.err.println("Error walking directory: " + e.getMessage());
            }
        }

        // Immediately stop progress reporting when file scanning ends
        progressReporter.shutdown();

        // Always perform database insertion and show results, even if interrupted
        long scanEndTime = System.nanoTime();
        double scanDurationSeconds = (scanEndTime - scanStartTime) / 1_000_000_000.0;

        try {
            if (shouldStop) {
                System.out.println("Filesystem scan interrupted by user, inserting partial data into database...");
            } else {
                System.out.println("Filesystem scan completed, inserting data into database...");
            }

            long insertStartTime = System.nanoTime();
            inserter.execute();
            long insertEndTime = System.nanoTime();
            double insertDurationSeconds = (insertEndTime - insertStartTime) / 1_000_000_000.0;

            System.out.printf("Filesystem scan time: %.2f seconds (%.1f items/sec)%n",
                            scanDurationSeconds, fileCount.get() / scanDurationSeconds);
            System.out.printf("Database insertion time: %.2f seconds%n", insertDurationSeconds);
            System.out.printf("Found %d directories and %d files%s%n",
                            directoryCount.get(), fileCount.get() - directoryCount.get(),
                            shouldStop ? " (partial scan due to interruption)" : "");

            // Show performance metrics
            long totalFiles = fileCount.get();
            long endTime = System.nanoTime();
            long startTime = scanStartTime;
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double filesPerSecond = totalFiles / durationSeconds;

            System.out.println("=== SCANNING PERFORMANCE ===");
            System.out.println("Total files and directories processed: " + totalFiles +
                             (shouldStop ? " (partial scan due to interruption)" : ""));
            System.out.printf("Scanning completed in: %.2f seconds%s%n", durationSeconds,
                             shouldStop ? " (interrupted)" : "");
            System.out.printf("Processing rate: %.1f items/second%n", filesPerSecond);
            System.out.println();

            // Query and display some results only if verbose
            if (verbose) {
                ResultsAnalyzer analyzer = new ResultsAnalyzer(connection, FILESYSTEM_METADATA_TABLE);
                analyzer.displaySampleResults();
                analyzer.demonstratePathColumns();
            } else {
                if (shouldStop) {
                    System.out.println("Filesystem scanning interrupted but partial results saved successfully. Use --verbose to see detailed analysis results.");
                } else {
                    System.out.println("Filesystem scanning completed successfully. Use --verbose to see detailed analysis results.");
                }
            }

        } catch (Exception e) {
            System.err.println("Error during database insertion: " + e.getMessage());
        } finally {
            // Always close the inserter
            try {
                inserter.close();
            } catch (Exception e) {
                System.err.println("Error closing inserter: " + e.getMessage());
            }

            // Mark processing as complete
            processingComplete = true;

            // Close the access log writer
            try {
                accessLogWriter.logError("===============================================");
                if (shouldStop) {
                    accessLogWriter.logError("Scan interrupted by user (Ctrl-C) at: " + java.time.LocalDateTime.now());
                } else {
                    accessLogWriter.logError("Scan completed at: " + java.time.LocalDateTime.now());
                }
                accessLogWriter.close();
            } catch (Exception e) {
                System.err.println("Warning: Error closing access log writer: " + e.getMessage());
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
     * Returns the current working directory
     *
     * @return The inferred working directory
     */
    private static String getWorkingDirectory() {
        return System.getProperty("user.dir");
    }
}