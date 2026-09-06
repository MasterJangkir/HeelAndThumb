@echo off
setlocal enabledelayedexpansion
echo ===================================================
echo   Touch Racer Enhanced Controller - Build Script
echo ===================================================

set "PROJECT_DIR=%~dp0"
if exist "%PROJECT_DIR%..\..\tools\android-sdk" (
    set "TOOLS_DIR=%PROJECT_DIR%..\..\tools"
) else (
    set "TOOLS_DIR=%PROJECT_DIR%..\tools"
)
set "JAVA_HOME=%TOOLS_DIR%\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "SDK_ROOT=%TOOLS_DIR%\android-sdk"
set "BUILD_TOOLS=%SDK_ROOT%\build-tools\34.0.0"
set "AAPT2=%BUILD_TOOLS%\aapt2.exe"
set "ZIPALIGN=%BUILD_TOOLS%\zipalign.exe"
set "D8_JAR=%BUILD_TOOLS%\lib\d8.jar"
set "APKSIGNER_JAR=%BUILD_TOOLS%\lib\apksigner.jar"
set "ANDROID_JAR=%SDK_ROOT%\platforms\android-34\android.jar"

set "BUILD_DIR=%PROJECT_DIR%build"
set "RES_DIR=%PROJECT_DIR%app\src\main\res"
set "MANIFEST=%PROJECT_DIR%app\src\main\AndroidManifest.xml"
set "SRC_DIR=%PROJECT_DIR%app\src\main\java"

echo [1/6] Cleaning build directory...
if exist "%BUILD_DIR%" rd /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\compiled_res"
mkdir "%BUILD_DIR%\gen"
mkdir "%BUILD_DIR%\classes"

echo [2/6] Compiling resources with aapt2...
"%AAPT2%" compile --dir "%RES_DIR%" -o "%BUILD_DIR%\compiled_res"
if errorlevel 1 goto error

echo [3/6] Linking resources...
set "FLAT_FILES="
for %%f in ("%BUILD_DIR%\compiled_res\*.flat") do (
    set "FLAT_FILES=!FLAT_FILES! "%%f""
)
"%AAPT2%" link -I "%ANDROID_JAR%" --min-sdk-version 21 --target-sdk-version 34 --manifest "%MANIFEST%" !FLAT_FILES! -o "%BUILD_DIR%\unsigned.apk" --java "%BUILD_DIR%\gen" --auto-add-overlay
if errorlevel 1 goto error

echo [4/6] Compiling Java code...
set "SOURCES_FILE=%BUILD_DIR%\sources.txt"
if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"

set "R_PATH=%BUILD_DIR%\gen\com\masterjangkir\touchracer\R.java"
set "R_PATH=!R_PATH:\=/!"
echo "!R_PATH!" >> "%SOURCES_FILE%"

for /f "delims=" %%i in ('dir /s /b /a-d "%SRC_DIR%\*.java"') do (
    set "LINE=%%i"
    set "LINE=!LINE:\=/!"
    echo "!LINE!" >> "%SOURCES_FILE%"
)

"%JAVA_HOME%\bin\javac.exe" -cp "%ANDROID_JAR%" -d "%BUILD_DIR%\classes" -source 1.8 -target 1.8 @"%SOURCES_FILE%"
if errorlevel 1 goto error

echo [5/6] Dexing with d8...
set "CLASSES_FILE=%BUILD_DIR%\classes.txt"
if exist "%CLASSES_FILE%" del "%CLASSES_FILE%"

for /f "delims=" %%i in ('dir /s /b /a-d "%BUILD_DIR%\classes\*.class"') do (
    set "LINE=%%i"
    set "LINE=!LINE:\=/!"
    echo !LINE! >> "%CLASSES_FILE%"
)

"%JAVA_HOME%\bin\java.exe" -cp "%D8_JAR%" com.android.tools.r8.D8 --lib "%ANDROID_JAR%" --output "%BUILD_DIR%" @"%CLASSES_FILE%"
if errorlevel 1 goto error

"%JAVA_HOME%\bin\jar.exe" uf "%BUILD_DIR%\unsigned.apk" -C "%BUILD_DIR%" classes.dex
if errorlevel 1 goto error

echo [6/6] Aligning and signing APK...
"%ZIPALIGN%" -p -f 4 "%BUILD_DIR%\unsigned.apk" "%BUILD_DIR%\aligned.apk"
if errorlevel 1 goto error

if not exist "%BUILD_DIR%\debug.keystore" (
    "%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore "%BUILD_DIR%\debug.keystore" -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
)

if exist "%PROJECT_DIR%HeelAndThumb.apk" del "%PROJECT_DIR%HeelAndThumb.apk"
"%JAVA_HOME%\bin\java.exe" -jar "%APKSIGNER_JAR%" sign --ks "%BUILD_DIR%\debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "%PROJECT_DIR%HeelAndThumb.apk" "%BUILD_DIR%\aligned.apk"
if errorlevel 1 goto error
copy /y "%PROJECT_DIR%HeelAndThumb.apk" "%PROJECT_DIR%TouchRacerEnhanced.apk" >nul

echo.
echo ===================================================
echo   SUCCESS! APK BUILT AT:
echo   %PROJECT_DIR%HeelAndThumb.apk
echo ===================================================
goto end

:error
echo.
echo [ERROR] Build failed. Please check output messages above.
exit /b 1

:end
endlocal
