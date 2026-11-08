@echo off
setlocal
cd /d %~dp0
where py >nul 2>nul || (echo Python 3.10+ is required. & pause & exit /b 1)
if not exist .venv\Scripts\python.exe py -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe server.py
pause
