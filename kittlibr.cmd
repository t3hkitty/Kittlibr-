@echo off
setlocal

:: Get the directory where this batch script lives
set "SCRIPT_DIR=%~dp0"

:: Check if Python exists
where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    python "%SCRIPT_DIR%start_kittlibr.py" %*
    goto :eof
)

where py >nul 2>nul
if %ERRORLEVEL% equ 0 (
    py "%SCRIPT_DIR%start_kittlibr.py" %*
    goto :eof
)

echo [ERROR] Python not found on PATH. Please install Python or ensure it is added to your environment variables.
exit /b 1
