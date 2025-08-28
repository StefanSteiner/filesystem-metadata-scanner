@echo off
setlocal enabledelayedexpansion
REM Filesystem Metadata Scanner Launcher
REM This script attempts to find Java and run the scanner

echo Filesystem Metadata Scanner
echo ==============================

REM Check if HAPI_JAVA_PACKAGE is set
if not defined HAPI_JAVA_PACKAGE (
    echo WARNING: HAPI_JAVA_PACKAGE environment variable is not set!
    echo.
    echo Please set HAPI_JAVA_PACKAGE to point to your Hyper API Java package:
    echo Example: set HAPI_JAVA_PACKAGE=C:\path\to\hyper-api-java
    echo.
    echo The application may fail to start without proper Hyper API setup...
    echo.
) else (
    echo Using HAPI_JAVA_PACKAGE: %HAPI_JAVA_PACKAGE%
    if exist "%HAPI_JAVA_PACKAGE%\lib" (
        echo Found Hyper API libraries in: %HAPI_JAVA_PACKAGE%\lib
    ) else (
        echo WARNING: %HAPI_JAVA_PACKAGE%\lib directory not found!
    )
    echo.
)

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

REM If no arguments provided, show usage and run with defaults
if "%~1"=="" (
    echo Usage: run.bat [OPTIONS]
    echo.
    echo IMPORTANT: Set HAPI_JAVA_PACKAGE environment variable first:
    echo   set HAPI_JAVA_PACKAGE=C:\path\to\hyper-api-java
    echo.
    echo Options:
    echo   --root PATH          Directory to scan (default: home directory)
    echo   --depth NUMBER       Maximum scan depth 1-20 (default: 3)
    echo   --skip-hidden        Skip hidden files
    echo   --verbose            Show detailed results
    echo   --query-existing FILE Query existing .hyper file
    echo   --direct             Use direct Java execution (better Ctrl-C handling)
    echo.
    echo Examples:
    echo   run.bat --root . --depth 2
    echo   run.bat --root "C:\Users\%USERNAME%\Documents" --verbose
    echo   run.bat --query-existing my_metadata.hyper --verbose
    echo   run.bat --root . --depth 8 --direct
    echo.
    echo Running with default settings...
    echo.
    call gradlew.bat run
) else (
    REM Check if --direct flag is present
    echo %* | find /i "--direct" >nul
    if %errorlevel% == 0 (
        REM Remove --direct from arguments
        set "ARGS=%*"
        call :remove_direct_flag
        
        echo Running directly with Java (better Ctrl-C handling)...
        echo Arguments: !CLEAN_ARGS!
        echo.
        
        REM Set up classpath with all JARs from build and HAPI_JAVA_PACKAGE
        set "CLASSPATH=build\classes\java\main"
        if defined HAPI_JAVA_PACKAGE (
            if exist "%HAPI_JAVA_PACKAGE%\lib" (
                for %%f in ("%HAPI_JAVA_PACKAGE%\lib\*.jar") do (
                    set "CLASSPATH=!CLASSPATH!;%%f"
                )
            )
        )
        
        REM Set up Hyper native library path
        set "HYPER_PATH="
        if defined HAPI_JAVA_PACKAGE (
            if exist "%HAPI_JAVA_PACKAGE%\lib\hyper" (
                set "HYPER_PATH=%HAPI_JAVA_PACKAGE%\lib\hyper"
            ) else if exist "%HAPI_JAVA_PACKAGE%\hyper" (
                set "HYPER_PATH=%HAPI_JAVA_PACKAGE%\hyper"
            ) else if exist "%HAPI_JAVA_PACKAGE%\bin" (
                set "HYPER_PATH=%HAPI_JAVA_PACKAGE%\bin"
            )
        )
        
        if defined HYPER_PATH (
            echo Using Hyper native libraries from: !HYPER_PATH!
            "%JAVA_CMD%" -cp "!CLASSPATH!" -Dtableau.hyper.libpath="!HYPER_PATH!" -Djava.library.path="!HYPER_PATH!" com.example.filesystem.LoadFilesystemMetadata !CLEAN_ARGS!
        ) else (
            echo Warning: Could not find Hyper native libraries
            "%JAVA_CMD%" -cp "!CLASSPATH!" com.example.filesystem.LoadFilesystemMetadata !CLEAN_ARGS!
        )
    ) else (
        echo Running with arguments: %*
        echo.
        if "%*"=="" (
            call gradlew.bat run
        ) else (
            call gradlew.bat run --args="%*"
        )
    )
)

echo.
echo Done!
pause
goto :eof

:remove_direct_flag
REM Function to remove --direct flag from arguments
set "CLEAN_ARGS="
for %%i in (%ARGS%) do (
    if /i not "%%i"=="--direct" (
        if defined CLEAN_ARGS (
            set "CLEAN_ARGS=!CLEAN_ARGS! %%i"
        ) else (
            set "CLEAN_ARGS=%%i"
        )
    )
)
goto :eof
