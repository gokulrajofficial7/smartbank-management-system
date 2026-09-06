@echo off
SETLOCAL Enabledelayedexpansion

:: Check if mvn is installed on the system
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [SmartBank] Maven detected on system PATH. Running application...
    mvn spring-boot:run
    exit /b %ERRORLEVEL%
)

:: Check if local portable Maven is already downloaded
set "MVN_DIR=%~dp0.maven"
set "MVN_BIN=%MVN_DIR%\apache-maven-3.9.6\bin\mvn.cmd"

if exist "%MVN_BIN%" (
    echo [SmartBank] Using local Maven...
    call "%MVN_BIN%" spring-boot:run
    exit /b %ERRORLEVEL%
)

echo [SmartBank] Maven is not installed on your system.
echo [SmartBank] Downloading a portable version of Maven (Apache Maven 3.9.6)...

:: Create .maven directory if it doesn't exist
if not exist "%MVN_DIR%" mkdir "%MVN_DIR%"

:: Download Maven zip using PowerShell
set "ZIP_PATH=%MVN_DIR%\maven.zip"
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%ZIP_PATH%'"

if not %ERRORLEVEL% EQU 0 (
    echo [SmartBank] Error: Failed to download Maven. Please check your internet connection.
    exit /b 1
)

echo [SmartBank] Extracting Maven...
powershell -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%MVN_DIR%' -Force"

if not %ERRORLEVEL% EQU 0 (
    echo [SmartBank] Error: Failed to extract Maven.
    exit /b 1
)

:: Delete zip file
del "%ZIP_PATH%"

echo [SmartBank] Maven set up successfully! Starting application...
call "%MVN_BIN%" spring-boot:run
