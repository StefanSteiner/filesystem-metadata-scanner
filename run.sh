#!/bin/bash

# Filesystem Metadata Scanner Launcher
# This script attempts to find Java and run the scanner

echo "Filesystem Metadata Scanner"
echo "=============================="

# Check if HAPI_JAVA_PACKAGE is set
if [ -z "$HAPI_JAVA_PACKAGE" ]; then
    echo "WARNING: HAPI_JAVA_PACKAGE environment variable is not set!"
    echo ""
    echo "Please set HAPI_JAVA_PACKAGE to point to your Hyper API installation:"
    echo "Example: export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java"
    echo ""
    echo "Continuing with build, but runtime may fail without proper Hyper API setup..."
    echo ""
else
    echo "Using HAPI_JAVA_PACKAGE: $HAPI_JAVA_PACKAGE"
    if [ -d "$HAPI_JAVA_PACKAGE/lib" ]; then
        echo "Found Hyper API libraries in: $HAPI_JAVA_PACKAGE/lib"
    else
        echo "WARNING: $HAPI_JAVA_PACKAGE/lib directory not found!"
    fi
    echo ""
fi

# Check if JAVA_HOME is set
if [ -n "$JAVA_HOME" ]; then
    echo "Using JAVA_HOME: $JAVA_HOME"
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "JAVA_HOME is not set, trying to find Java in PATH..."
    if command -v java &> /dev/null; then
        JAVA_CMD="java"
        echo "Found Java in PATH"
    else
        echo "ERROR: Java not found!"
        echo ""
        echo "Please install Java 11 or higher and either:"
        echo "1. Set JAVA_HOME environment variable, or"
        echo "2. Add Java to your PATH"
        echo ""
        echo "See SETUP.md for detailed instructions."
        exit 1
    fi
fi

# Verify Java version
echo ""
echo "Checking Java version..."
if ! $JAVA_CMD -version; then
    echo "ERROR: Failed to run Java"
    exit 1
fi

echo ""
echo "Building project..."
if ! ./gradlew build; then
    echo "ERROR: Build failed"
    exit 1
fi

echo ""
echo "Build successful!"
echo ""

# If no arguments provided, show usage
if [ $# -eq 0 ]; then
    echo "Usage: ./run.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --root PATH          Directory to scan (default: home directory)"
    echo "  --depth NUMBER       Maximum scan depth 1-20 (default: 3)"
    echo "  --skip-hidden        Skip hidden files"
    echo "  --verbose            Show detailed results"
    echo "  --query-existing FILE Query existing .hyper file"
    echo "  --direct             Use direct Java execution (better Ctrl-C handling)"
    echo ""
    echo "Examples:"
    echo "  ./run.sh --root . --depth 2"
    echo "  ./run.sh --root \"/home/\$USER/Documents\" --verbose"
    echo "  ./run.sh --query-existing my_metadata.hyper --verbose"
    echo "  ./run.sh --root . --depth 8 --direct"
    echo ""
    echo "Running with default settings..."
    echo ""
    ./gradlew run
else
    # Check if --direct flag is present
    if [[ " $* " == *" --direct "* ]]; then
        # Remove --direct from arguments
        ARGS=$(echo "$*" | sed 's/--direct//g' | sed 's/  / /g' | sed 's/^ *//g' | sed 's/ *$//g')

        echo "Running directly with Java (better Ctrl-C handling)..."
        echo "Arguments: $ARGS"
        echo ""

        # Set up classpath with all JARs from build and HAPI_JAVA_PACKAGE
        CLASSPATH="build/classes/java/main"
        if [ -d "$HAPI_JAVA_PACKAGE/lib" ]; then
            for jar in "$HAPI_JAVA_PACKAGE/lib"/*.jar; do
                if [ -f "$jar" ]; then
                    CLASSPATH="$CLASSPATH:$jar"
                fi
            done
        fi

        # Set up Hyper native library path
        if [ -d "$HAPI_JAVA_PACKAGE/lib/hyper" ]; then
            HYPER_PATH="$HAPI_JAVA_PACKAGE/lib/hyper"
        elif [ -d "$HAPI_JAVA_PACKAGE/hyper" ]; then
            HYPER_PATH="$HAPI_JAVA_PACKAGE/hyper"
        elif [ -d "$HAPI_JAVA_PACKAGE/bin" ]; then
            HYPER_PATH="$HAPI_JAVA_PACKAGE/bin"
        fi

        if [ -n "$HYPER_PATH" ]; then
            echo "Using Hyper native libraries from: $HYPER_PATH"
            exec $JAVA_CMD -cp "$CLASSPATH" \
                -Dtableau.hyper.libpath="$HYPER_PATH" \
                -Djava.library.path="$HYPER_PATH" \
                com.example.filesystem.LoadFilesystemMetadata $ARGS
        else
            echo "Warning: Could not find Hyper native libraries"
            exec $JAVA_CMD -cp "$CLASSPATH" \
                com.example.filesystem.LoadFilesystemMetadata $ARGS
        fi
    else
        echo "Running with arguments: $*"
        echo ""
        ./gradlew run --args="$*"
    fi
fi

echo ""
echo "Done!"
