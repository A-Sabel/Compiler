@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   Compiling and Running Compiler GUI 2
echo ========================================
echo.

:: Check if bin directory exists, if not create it
if not exist "bin" mkdir bin

echo [1/3] Gathering Java source files...
if exist sources.txt del sources.txt

:: Loop through all .java files in src/main/java directory
for /r src\main\java %%f in (*.java) do (
    set "filepath=%%f"
    set "filepath=!filepath:\=/!"
    echo "!filepath!" >> sources.txt
)

echo [2/3] Compiling Java files...
javac -d bin @sources.txt

:: Check if compilation succeeded
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed!
    echo Please fix the Java errors above.
    del sources.txt
    pause
    exit /b %errorlevel%
)

echo [3/3] Launching GUI 2...
del sources.txt
echo.

:: Run the GUI
java -cp bin compiler.ui.MainGUI2

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to run the application!
    pause
    exit /b %errorlevel%
)
