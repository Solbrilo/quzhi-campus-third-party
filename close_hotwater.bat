@echo off
setlocal

set "SCRIPT=%~dp0qzxy_hotwater.py"
set "CAPTURE=%~dp01.chls"

if exist "%CAPTURE%" (
  set "USE_CAPTURE=1"
) else (
  set "USE_CAPTURE=0"
)

where py >nul 2>nul
if %errorlevel%==0 (
  if "%USE_CAPTURE%"=="1" (
    py -3 "%SCRIPT%" --capture "%CAPTURE%" close
  ) else (
    py -3 "%SCRIPT%" close
  )
) else (
  if "%USE_CAPTURE%"=="1" (
    python "%SCRIPT%" --capture "%CAPTURE%" close
  ) else (
    python "%SCRIPT%" close
  )
)

echo.
pause
