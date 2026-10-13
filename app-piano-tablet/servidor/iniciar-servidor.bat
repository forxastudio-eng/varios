@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set AUDIVERIS_VERSION=5.11.0
if "%PORT%"=="" set PORT=8765
title Servidor de partituras - Piano Cascada

rem --- Python ---
set PY=
where py >nul 2>nul && set PY=py -3
if "%PY%"=="" where python >nul 2>nul && set PY=python
if "%PY%"=="" (
  echo.
  echo  Falta Python. Instalalo desde https://www.python.org/downloads/
  echo  y marca la casilla "Add python.exe to PATH". Despues vuelve a abrir este archivo.
  echo.
  pause
  exit /b 1
)

if not exist .venv (
  echo · Creando entorno de Python ^(solo la primera vez^)...
  %PY% -m venv .venv || goto :error
)
echo · Comprobando las librerias...
.venv\Scripts\python -m pip install -q --disable-pip-version-check -r requirements.txt || goto :error

rem --- Audiveris (se extrae del instalador sin necesitar permisos de administrador) ---
dir /s /b audiveris\Audiveris.exe >nul 2>nul
if errorlevel 1 (
  echo · Descargando Audiveris %AUDIVERIS_VERSION% ^(solo la primera vez, unos 80 MB^)...
  if not exist audiveris mkdir audiveris
  powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://github.com/Audiveris/audiveris/releases/download/%AUDIVERIS_VERSION%/Audiveris-%AUDIVERIS_VERSION%-windows-x86_64.msi' -OutFile 'audiveris\audiveris.msi'" || goto :error
  echo · Preparando Audiveris...
  msiexec /a "%CD%\audiveris\audiveris.msi" /qn TARGETDIR="%CD%\audiveris\app" || goto :error
  del audiveris\audiveris.msi
)

echo.
echo · Arrancando el servidor. Dejalo abierto mientras importas partituras ^(Ctrl+C para cerrarlo^).
echo · Si Windows pregunta por el Firewall, pulsa "Permitir acceso".
echo.
.venv\Scripts\python -m uvicorn app:app --host 0.0.0.0 --port %PORT%
pause
exit /b 0

:error
echo.
echo  Algo ha fallado. Copia el mensaje de arriba y mandamelo para arreglarlo.
pause
exit /b 1
