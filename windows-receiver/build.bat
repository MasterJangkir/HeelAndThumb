@echo off
setlocal
echo ===================================================
echo   HeelAndThumb Rx (Windows) - Build Script
echo ===================================================

set "RECEIVER_DIR=%~dp0"
set "CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "OUT_EXE=%RECEIVER_DIR%HeelAndThumbRx.exe"
set "SRC=%RECEIVER_DIR%ReceiverForm.cs"
set "VJOY_WRAP=%RECEIVER_DIR%vJoyInterfaceWrap.dll"

echo Compiling HeelAndThumbRx.exe...
"%CSC%" /target:winexe /platform:x64 /win32icon:"%RECEIVER_DIR%app.ico" /r:"%VJOY_WRAP%",System.dll,System.Drawing.dll,System.Windows.Forms.dll,System.Core.dll /out:"%OUT_EXE%" "%SRC%"
if errorlevel 1 goto error
copy /y "%OUT_EXE%" "%RECEIVER_DIR%TouchRacerReceiver.exe" >nul

echo.
echo ===================================================
echo   SUCCESS! Receiver compiled at:
echo   %OUT_EXE%
echo ===================================================
goto end
:error
echo [ERROR] Compilation failed!
exit /b 1
:end
endlocal
