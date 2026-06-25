@echo off
REM Double-click to compact the Docker WSL2 vhdx and reclaim C: space.
REM Launches the PowerShell script, which self-elevates (UAC prompt).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compact-docker.ps1"
