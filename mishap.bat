@echo off
setlocal

rem Load local settings (JAVA_HOME) if the file exists
if exist "%~dp0mishap.env.bat" call "%~dp0mishap.env.bat"

rem Use JAVA_HOME if set, otherwise java from PATH
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

"%JAVA_CMD%" -jar "%~dp0mishap-cli\target\mishap.jar" %*
exit /b %ERRORLEVEL%