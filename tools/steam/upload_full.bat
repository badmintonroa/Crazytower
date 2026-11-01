@echo off
rem Laedt die Vollversion zu Steam hoch, wie upload_demo.bat. Aufruf: upload_full.bat <Steam-Benutzername>
cd /d "%~dp0"
steamcmd +login %1 +run_app_build "%~dp0app_build_full.vdf" +quit
