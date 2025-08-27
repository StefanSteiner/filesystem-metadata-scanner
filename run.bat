@echo off
REM Filesystem Metadata Scanner Launcher
REM This script attempts to find Java and run the scanner

echo Filesystem Metadata Scanner
echo ==============================

REM Check if JAVA_HOME is set
if defined JAVA_HOME (
    echo Using JAVA_HOME: %JAVA_HOME%
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    echo JAVA_HOME is not set, trying to find Java in PATH...
    where java >nul 2>nul
    if %errorlevel% == 0 (
        set "JAVA_CMD=java"
        echo Found Java in PATH
    ) else (
        echo ERROR: Java not found!
        echo.
        echo Please install Java 11 or higher and either:
        echo 1. Set JAVA_HOME environment variable, or
        echo 2. Add Java to your PATH
        echo.
        echo See SETUP.md for detailed instructions.
        pause
        exit /b 1
    )
)

REM Verify Java version
echo.
echo Checking Java version...
%JAVA_CMD% -version
if %errorlevel% neq 0 (
    echo ERROR: Failed to run Java
    pause
    exit /b 1
)

echo.
echo Building project...
call gradlew.bat build
if %errorlevel% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo.
echo Build successful! 
echo.

REM If no arguments provided, show usage
if "%~1"=="" (
    echo Usage: run.bat [OPTIONS]
    echo.
    echo Options:
    echo   --root PATH          Directory to scan (default: home directory)
    echo   --depth NUMBER       Maximum scan depth 1-20 (default: 3)
    echo   --skip-hidden        Skip hidden files
    echo   --verbose            Show detailed results
    echo   --query-existing FILE Query existing .hyper file
    echo.
    echo Examples:
    echo   run.bat --root . --depth 2
    echo   run.bat --root "C:\Users\%USERNAME%\Documents" --verbose
    echo   run.bat --query-existing my_metadata.hyper --verbose
    echo.
    echo Running with default settings...
    echo.
    call gradlew.bat run
) else (
    echo Running with arguments: %*
    echo.
    call gradlew.bat run --args="%*"
)

echo.
echo Done!
pause
