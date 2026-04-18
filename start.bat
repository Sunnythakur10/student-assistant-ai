@echo off
setlocal enabledelayedexpansion
title StudyBot Launcher
color 0A

echo.
echo ========================================
echo    Starting StudyBot Student Assistant
echo ========================================
echo.

:: Step 1 - Check if Docker is running
echo [1/6] Checking Docker...
docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not running.
    echo Please start Docker Desktop and try again.
    pause
    exit /b 1
)
echo       Docker is running.

:: Step 2 - Start PostgreSQL container
echo.
echo [2/6] Starting PostgreSQL + pgvector...
docker-compose up -d
if errorlevel 1 (
    echo ERROR: Failed to start Docker containers.
    pause
    exit /b 1
)
echo       PostgreSQL started.

:: Step 3 - Wait for PostgreSQL to be ready (quick poll)
echo.
echo [3/6] Waiting for PostgreSQL to be ready...
set DB_READY=0
for /l %%i in (1,1,10) do (
    docker exec student_assistant_db pg_isready -U postgres -d student_assistant >nul 2>&1
    if !errorlevel! == 0 (
        set DB_READY=1
        goto :db_ready
    )
    timeout /t 1 /nobreak >nul
)
:db_ready
if "!DB_READY!"=="1" (
    echo       PostgreSQL is ready.
) else (
    echo       PostgreSQL health check timed out; continuing...
)

:: Step 4 - Clear old embeddings
echo [4/6] Clearing old embeddings...

docker exec student_assistant_db psql -U postgres -d student_assistant -c "DELETE FROM document_embeddings;" >nul 2>&1

if %errorlevel% neq 0 (
    echo       WARNING: Could not clean database (container may not be ready yet)
) else (
    echo       Database cleaned successfully.
)



:: Step 5 - Check if Ollama is running, start if not
echo.
echo [5/6] Checking Ollama...
set OLLAMA_READY=0
curl -s --max-time 1 http://localhost:11434 >nul 2>&1
if !errorlevel! == 0 (
    set OLLAMA_READY=1
    echo       Ollama is already running.
) else (
    echo       Ollama not running. Starting Ollama...
    start "Ollama Server" /min cmd /c "ollama serve"
    for /l %%i in (1,1,12) do (
        curl -s --max-time 1 http://localhost:11434 >nul 2>&1
        if !errorlevel! == 0 (
            set OLLAMA_READY=1
            goto :ollama_ready
        )
        timeout /t 1 /nobreak >nul
    )
)
:ollama_ready
if "!OLLAMA_READY!" NEQ "1" (
    echo ERROR: Ollama not responding on http://localhost:11434
    pause
    exit /b 1
)
echo       Ollama is ready.

:: Check if nomic-embed-text model is available
echo       Checking nomic-embed-text model...
ollama list > "%TEMP%\ollama_models.txt" 2>&1
findstr /i "nomic-embed-text" "%TEMP%\ollama_models.txt" >nul 2>&1
if errorlevel 1 (
    echo       Model not found. Pulling...
    ollama pull nomic-embed-text
) else (
    echo       Model already present.
)
del "%TEMP%\ollama_models.txt" >nul 2>&1
echo       Embedding model ready.

:: Step 6 - Check GROQ API key
echo.
echo [6/6] Checking GROQ API key...
if "!GROQ_API_KEY!"=="" (
    for /f "delims=" %%K in ('powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('GROQ_API_KEY','User')"') do set "GROQ_API_KEY=%%K"
)
if "!GROQ_API_KEY!"=="" (
    echo.
    echo  GROQ_API_KEY is not set.
    set /p GROQ_API_KEY="  Enter your Groq API key (gsk_...): "
    if "!GROQ_API_KEY!"=="" (
        echo ERROR: No API key entered. Exiting.
        pause
        exit /b 1
    )
)
echo       GROQ API key is set.

:: All checks passed - Start the application
echo.
echo ========================================
echo    All services ready. Launching app...
echo ========================================
echo.
echo    URL: http://localhost:8080
echo    Press Ctrl+C to stop the application
echo ========================================
echo.

:: Run Maven
mvn spring-boot:run

:: If Maven exits
echo.
echo Application stopped.
pause