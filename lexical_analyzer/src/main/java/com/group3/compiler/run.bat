@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem This script lives in src\main\java\com\group3\compiler\, so go up to lexical_analyzer.
cd /d "%~dp0\..\..\..\..\..\.."

set "PROJECT_DIR=%cd%"
set "SRC_DIR=%PROJECT_DIR%\src\main\java"
set "OUT_DIR=%PROJECT_DIR%\target\classes"
set "MAIN_CLASS=com.group3.compiler.frontend.MainGUI"

set "JAVAC_CMD="
set "JAVA_CMD="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVAC_CMD (
    for /f "delims=" %%I in ('where javac.exe 2^>nul') do (
        set "JAVAC_CMD=%%I"
        goto :found_javac
    )
)

:found_javac
if not defined JAVAC_CMD (
    for /d %%D in ("%ProgramFiles%\Java\jdk*") do (
        if exist "%%~fD\bin\javac.exe" set "JAVAC_CMD=%%~fD\bin\javac.exe"
    )
)

if not defined JAVA_CMD (
    for /f "delims=" %%I in ('where java.exe 2^>nul') do (
        set "JAVA_CMD=%%I"
        goto :found_java
    )
)

:found_java
if not defined JAVA_CMD (
    for /d %%D in ("%ProgramFiles%\Java\jdk*") do (
        if exist "%%~fD\bin\java.exe" set "JAVA_CMD=%%~fD\bin\java.exe"
    )
)

if not defined JAVAC_CMD (
    echo [ERROR] javac was not found. Install JDK 21+ or set JAVA_HOME.
    exit /b 1
)

if not defined JAVA_CMD (
    echo [ERROR] java was not found. Install JDK 21+ or set JAVA_HOME.
    exit /b 1
)

if not exist "%SRC_DIR%" (
    echo [ERROR] Source directory not found: "%SRC_DIR%"
    exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set "TMP_SOURCES=%TEMP%\tokenizer_sources_%RANDOM%%RANDOM%.txt"
if exist "%TMP_SOURCES%" del /q "%TMP_SOURCES%"

for /r "%SRC_DIR%" %%F in (*.java) do (
    set "SRC_FILE=%%F"
    set "SRC_FILE=!SRC_FILE:\=/!"
    echo "!SRC_FILE!">>"%TMP_SOURCES%"
)

if not exist "%TMP_SOURCES%" (
    echo [ERROR] No .java files were found in "%SRC_DIR%".
    exit /b 1
)

echo [INFO] Compiling Java sources...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%OUT_DIR%" -d "%OUT_DIR%" @"%TMP_SOURCES%"
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    del /q "%TMP_SOURCES%" >nul 2>&1
    exit /b 1
)

del /q "%TMP_SOURCES%" >nul 2>&1

echo [INFO] Launching %MAIN_CLASS%...
"%JAVA_CMD%" -cp "%OUT_DIR%" %MAIN_CLASS%
exit /b %ERRORLEVEL%
