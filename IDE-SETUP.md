# IDE Setup for Filesystem Metadata Scanner

## Problem
IDEs may show import errors for Tableau Hyper API classes because the JARs are loaded dynamically from `HAPI_JAVA_PACKAGE` environment variable.

## Solution

### For VS Code
1. Set environment variable: `export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java`
2. Run: `./gradlew cleanEclipse eclipse cleanIdea idea`
3. Reload VS Code window: `Cmd+Shift+P` → "Developer: Reload Window"
4. If you see package errors: `Cmd+Shift+P` → "Java: Reload Projects"
5. Ensure `src/main/java` is recognized as source root in the Java Project view

### For IntelliJ IDEA
1. Set environment variable: `export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java`
2. Run: `./gradlew cleanIdea idea`
3. Open the generated `.ipr` file or import the project
4. Refresh Gradle project: `Gradle` tool window → refresh button

### For Eclipse
1. Set environment variable: `export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java`  
2. Run: `./gradlew cleanEclipse eclipse`
3. Import project using the generated `.project` file
4. Refresh project: `F5`

### For any IDE
Run the helper task:
```bash
export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java
./gradlew refreshIDE
```

## Verification
The code compiles and runs successfully regardless of IDE warnings:
```bash
export HAPI_JAVA_PACKAGE=/path/to/hyper-api-java
./gradlew clean build
./run.sh --root . --depth 2
```

## Common Issues

### Package Declaration Errors
If you see errors like "package does not match expected package src.main.java.com.example.filesystem":
1. This means the IDE doesn't recognize `src/main/java` as the source root
2. Run: `./gradlew cleanEclipse eclipse cleanIdea idea` 
3. In VS Code: `Cmd+Shift+P` → "Java: Reload Projects"
4. Check the Java Project view to ensure proper source folder recognition

### Import Resolution Errors  
If you see "import cannot be resolved" for Hyper API classes:
1. This means the IDE doesn't see the dynamically loaded JARs
2. Use the solutions above to generate proper IDE project files
3. The code compiles and runs correctly regardless of these warnings

## Note
These are false positive IDE warnings. The application builds and runs correctly because:
1. Gradle uses `implementation fileTree()` to load the JARs at runtime
2. The actual Hyper API classes are available during compilation and execution
3. IDEs sometimes can't resolve dynamically loaded dependencies until properly configured

The project works perfectly - the IDE warnings are just cosmetic issues that can be resolved with proper IDE project file generation.
