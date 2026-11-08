@echo off
setlocal
cd /d %~dp0
where py >nul 2>nul || (echo Python 3.10+ is required. & pause & exit /b 1)
echo Windows 방화벽의 개인 네트워크 접근을 확인합니다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -Verb RunAs -Wait -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File ''%~dp0setup_firewall.ps1'''"
if errorlevel 1 (echo 방화벽 설정이 취소되었습니다. 같은 Wi-Fi에서 접속하려면 허용이 필요합니다.)
if not exist .venv\Scripts\python.exe py -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe server.py
pause
