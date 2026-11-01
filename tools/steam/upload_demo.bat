@echo off
rem Laedt die Demo zu Steam hoch. Vorher: steamcmd.exe in diesen Ordner legen (oder in den PATH),
rem DEMO_APP_ID und DEMO_DEPOT_ID in app_build_demo.vdf eintragen und den Demo-Ordner
rem nach content\CrazyTowerDemo\ entpacken. Aufruf: upload_demo.bat <Steam-Benutzername>
cd /d "%~dp0"
steamcmd +login %1 +run_app_build "%~dp0app_build_demo.vdf" +quit
