# Filesystem Metadata Scanner

A Java application that scans filesystem directories and stores comprehensive metadata in Tableau Hyper files using the Hyper API. This tool is perfect for filesystem analysis, auditing, and creating searchable databases of file information.

## Features

- **Comprehensive Metadata Capture**: File names, paths, sizes, ownership, timestamps, extensions
- **Advanced File Type Detection**: Symbolic links, mount points, junctions, hidden files
- **Configurable Scanning**: Adjustable depth limits, hidden file filtering
- **Cross-Platform Support**: Windows, macOS, Linux with OS-specific optimizations
- **Error Handling**: Graceful handling of permission errors with detailed logging
- **Performance Monitoring**: Real-time progress reporting and performance metrics
- **Database Integration**: Stores data in Tableau Hyper format for analysis

## Prerequisites

- **Java 11 or higher**
- **Tableau Hyper API JARs** (included in `lib/` directory)

## Quick Start

### Build and Run

```bash
# Build the project
./gradlew build

# Run with default settings (scans home directory)
./gradlew run

# Show usage information
./gradlew usage
```

### Basic Usage Examples

```bash
# Scan current directory with depth 2
./gradlew run --args="--root . --depth 2"

# Scan specific directory, skip hidden files, show verbose output
./gradlew run --args="--root C:\\Users\\username\\Documents --depth 4 --skip-hidden --verbose"

# Query existing database
./gradlew run --args="--query-existing my_scan_metadata.hyper --verbose"
```

## Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--root <path>` | Directory to start scanning from | User home directory |
| `--depth <number>` | Maximum directory depth to scan (1-20) | 3 |
| `--skip-hidden` | Skip hidden and system files | Include hidden files |
| `--verbose` | Show detailed analysis results | Summary only |
| `--query-existing <file>` | Query existing database instead of scanning | Scan filesystem |

## Output Files

- **`{directory_name}_metadata.hyper`**: Hyper database containing all filesystem metadata
- **`{directory_name}_access_errors.log`**: Detailed log of access errors and skipped files

## Database Schema

The Hyper database contains a single table `FilesystemMetadata` with the following columns:

| Column | Type | Description |
|--------|------|-------------|
| File Name | TEXT | Name of the file or directory |
| Path | TEXT | Parent directory path |
| Full Path | TEXT | Complete path including filename |
| File Size | BIGINT | Size in bytes (0 for directories) |
| File Owner | TEXT | Owner username |
| File Extension | TEXT | File extension without dot |
| Is Directory | BOOLEAN | Whether the item is a directory |
| Is Hidden | BOOLEAN | Whether the item is hidden |
| Depth | BIGINT | Directory depth from scan root |
| File ID | TEXT | Unique file identifier (inode/file index) |
| Link Type | TEXT | Type of link (NONE, SYMLINK, MOUNTPOINT, JUNCTION) |
| Link Target | TEXT | Target path for links |
| Creation Time | TIMESTAMP | File creation time |
| Last Access Time | TIMESTAMP | Last access time |
| Last Modified Time | TIMESTAMP | Last modification time |

## Advanced Usage

### Analyzing Results

The verbose mode (`--verbose`) provides detailed analysis including:
- File size distribution and largest files
- File extension statistics
- Directory depth analysis
- Hidden file counts
- Symbolic link and mount point detection
- File ownership patterns
- Recent modification analysis

### Querying Existing Data

```bash
# Open existing database for analysis
./gradlew run --args="--query-existing Documents_metadata.hyper --verbose"
```

### Performance Tuning

- **Depth Limit**: Lower depth values scan faster but capture less data
- **Hidden Files**: Use `--skip-hidden` to improve performance on systems with many hidden files
- **Large Directories**: The scanner automatically handles large directories with progress reporting

## Platform-Specific Notes

### Windows
- Detects NTFS junctions and reparse points
- Handles Windows file ownership and permissions
- Supports UNC paths and network drives

### macOS/Linux
- Detects symbolic links and mount points
- POSIX file ownership and permissions
- Handles case-sensitive filesystems

## Troubleshooting

### Common Issues

1. **Permission Errors**: Check the access log file for detailed information about inaccessible files
2. **Large Scans**: Use lower depth values or enable `--skip-hidden` for better performance
3. **Memory Issues**: For very large directories, consider breaking the scan into smaller chunks

### Error Logs

Access errors and skipped files are logged to `{directory_name}_access_errors.log` with timestamps and detailed error messages.

## Building from Source

```bash
# Clean and build
./gradlew clean build

# Run tests (if available)
./gradlew test

# Generate distribution
./gradlew distTar
```

## Dependencies

This project uses the following libraries:
- **Tableau Hyper API**: Core database functionality
- **JNA (Java Native Access)**: Platform-specific file operations

## License

[Specify your license here]

## Contributing

[Add contribution guidelines here]

## Support

For issues and questions:
1. Check the error log files for detailed error information
2. Verify Java version compatibility (Java 11+)
3. Ensure proper file permissions for target directories

## Version History

- **1.0.0**: Initial release with comprehensive filesystem scanning capabilities
