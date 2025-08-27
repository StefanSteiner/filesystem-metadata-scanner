# Setup Guide for Filesystem Metadata Scanner

## Java Setup

### Windows

1. **Download and Install Java 11 or higher**:
   - Download from [Amazon Corretto](https://aws.amazon.com/corretto/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
   - Install following the installer instructions

2. **Set JAVA_HOME Environment Variable**:
   ```powershell
   # Method 1: Using System Properties (Recommended)
   # 1. Right-click "This PC" -> Properties
   # 2. Click "Advanced system settings"
   # 3. Click "Environment Variables"
   # 4. Under "System Variables", click "New"
   # 5. Variable name: JAVA_HOME
   # 6. Variable value: C:\Program Files\Amazon Corretto\jdk11.x.x_x (adjust path)
   # 7. Click OK
   
   # Method 2: Using PowerShell (Current session only)
   $env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk11.0.17.8"
   $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
   ```

3. **Verify Java Installation**:
   ```powershell
   java -version
   javac -version
   ```

### macOS

1. **Install Java using Homebrew**:
   ```bash
   # Install Homebrew if not already installed
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   
   # Install Java
   brew install openjdk@11
   
   # Set JAVA_HOME
   echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@11"' >> ~/.zshrc
   echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
   source ~/.zshrc
   ```

2. **Alternative: Using SDKMAN**:
   ```bash
   # Install SDKMAN
   curl -s "https://get.sdkman.io" | bash
   source ~/.sdkman/bin/sdkman-init.sh
   
   # Install Java
   sdk install java 11.0.17-amzn
   sdk use java 11.0.17-amzn
   ```

### Linux (Ubuntu/Debian)

1. **Install OpenJDK**:
   ```bash
   sudo apt update
   sudo apt install openjdk-11-jdk
   
   # Set JAVA_HOME
   echo 'export JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"' >> ~/.bashrc
   echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
   source ~/.bashrc
   ```

### Linux (CentOS/RHEL/Fedora)

1. **Install OpenJDK**:
   ```bash
   # CentOS/RHEL
   sudo yum install java-11-openjdk-devel
   
   # Fedora
   sudo dnf install java-11-openjdk-devel
   
   # Set JAVA_HOME
   echo 'export JAVA_HOME="/usr/lib/jvm/java-11-openjdk"' >> ~/.bashrc
   echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
   source ~/.bashrc
   ```

## Building the Project

Once Java is properly installed and configured:

```bash
# Build the project
./gradlew build          # Linux/macOS
gradlew.bat build        # Windows

# Clean and rebuild
./gradlew clean build    # Linux/macOS
gradlew.bat clean build  # Windows
```

## Running the Application

```bash
# Run with default settings
./gradlew run            # Linux/macOS
gradlew.bat run          # Windows

# Run with arguments
./gradlew run --args="--root . --depth 2 --verbose"    # Linux/macOS
gradlew.bat run --args="--root . --depth 2 --verbose"  # Windows
```

## Troubleshooting

### Common Issues

1. **"JAVA_HOME is not set"**:
   - Follow the Java setup instructions above
   - Restart your terminal/command prompt after setting environment variables

2. **"java command not found"**:
   - Ensure Java is in your PATH
   - Verify installation with `java -version`

3. **"Could not find tools.jar"**:
   - Ensure you installed JDK, not just JRE
   - Point JAVA_HOME to JDK directory

4. **Permission denied on gradlew**:
   ```bash
   chmod +x gradlew  # Linux/macOS only
   ```

### Verifying Setup

Run these commands to verify your setup:

```bash
# Check Java version
java -version

# Check JAVA_HOME
echo $JAVA_HOME          # Linux/macOS
echo %JAVA_HOME%         # Windows CMD
echo $env:JAVA_HOME      # Windows PowerShell

# Test Gradle wrapper
./gradlew --version      # Linux/macOS
gradlew.bat --version    # Windows
```

## IDE Setup

### IntelliJ IDEA
1. Open the project directory
2. IDEA should auto-detect the Gradle project
3. Set Project SDK to Java 11+

### VS Code
1. Install "Extension Pack for Java"
2. Open the project directory
3. VS Code should auto-detect the Gradle project

### Eclipse
1. Import -> Existing Gradle Project
2. Select the project directory
3. Set Java Build Path to JDK 11+
