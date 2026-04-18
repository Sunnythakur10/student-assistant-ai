@echo off
title StudyBot Stopper
color 0C

echo.
echo ========================================
echo    Stopping StudyBot Student Assistant
echo ========================================
echo.

echo [1/2] Stopping PostgreSQL container...
docker-compose down
echo       PostgreSQL stopped.

echo.
echo [2/2] Stopping Ollama...
taskkill /f /im ollama.exe >nul 2>&1
echo       Ollama stopped.

echo.
echo ========================================
echo    StudyBot stopped successfully.
echo ========================================
echo.
pause
