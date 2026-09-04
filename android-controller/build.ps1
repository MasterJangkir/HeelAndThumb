$ErrorActionPreference = "Stop"
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Touch Racer Enhanced Controller - Build Script" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

$ProjectDir = $PSScriptRoot
$ToolsDir = Join-Path $ProjectDir "..\tools"
$JavaHome = Join-Path $ToolsDir "jdk-17"
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;" + $env:PATH

$SdkRoot = Join-Path $ToolsDir "android-sdk"
$Aapt2 = Join-Path $SdkRoot "build-tools\34.0.0\aapt2.exe"
$D8 = Join-Path $SdkRoot "build-tools\34.0.0\d8.bat"
$Zipalign = Join-Path $SdkRoot "build-tools\34.0.0\zipalign.exe"
$Apksigner = Join-Path $SdkRoot "build-tools\34.0.0\apksigner.bat"
$AndroidJar = Join-Path $SdkRoot "platforms\android-34\android.jar"

$BuildDir = Join-Path $ProjectDir "build"
$ResDir = Join-Path $ProjectDir "app\src\main\res"
$Manifest = Join-Path $ProjectDir "app\src\main\AndroidManifest.xml"
$SrcDir = Join-Path $ProjectDir "app\src\main\java"

Write-Host "[1/6] Cleaning build directory..." -ForegroundColor Yellow
if (Test-Path $BuildDir) { Remove-Item -Recurse -Force $BuildDir }
New-Item -ItemType Directory -Force (Join-Path $BuildDir "compiled_res") | Out-Null
New-Item -ItemType Directory -Force (Join-Path $BuildDir "gen") | Out-Null
New-Item -ItemType Directory -Force (Join-Path $BuildDir "classes") | Out-Null

Write-Host "[2/6] Compiling resources with aapt2..." -ForegroundColor Yellow
& $Aapt2 compile --dir $ResDir -o (Join-Path $BuildDir "compiled_res")

Write-Host "[3/6] Linking resources..." -ForegroundColor Yellow
$FlatFiles = Get-ChildItem (Join-Path $BuildDir "compiled_res\*.flat") | ForEach-Object { $_.FullName }
$UnsignedApk = Join-Path $BuildDir "unsigned.apk"
& $Aapt2 link -I $AndroidJar --manifest $Manifest $FlatFiles -o $UnsignedApk --java (Join-Path $BuildDir "gen") --auto-add-overlay

Write-Host "[4/6] Compiling Java code..." -ForegroundColor Yellow
$Sources = @(Get-ChildItem -Recurse (Join-Path $BuildDir "gen\*.java") | ForEach-Object { $_.FullName })
$Sources += @(Get-ChildItem -Recurse (Join-Path $SrcDir "\*.java") | ForEach-Object { $_.FullName })
$SourcesFile = Join-Path $BuildDir "sources.txt"
$Sources | ForEach-Object { $_.Replace('\', '/') } | Set-Content $SourcesFile -Encoding ASCII

& "$JavaHome\bin\javac.exe" -cp $AndroidJar -d (Join-Path $BuildDir "classes") -source 1.8 -target 1.8 "@$SourcesFile"

Write-Host "[5/6] Dexing with d8..." -ForegroundColor Yellow
$Classes = Get-ChildItem -Recurse (Join-Path $BuildDir "classes\*.class") | ForEach-Object { $_.FullName.Replace('\', '/') }
$ClassesFile = Join-Path $BuildDir "classes.txt"
$Classes | Set-Content $ClassesFile -Encoding ASCII

& $D8 --lib $AndroidJar --output $BuildDir "@$ClassesFile"

& "$JavaHome\bin\jar.exe" uf $UnsignedApk -C $BuildDir classes.dex

Write-Host "[6/6] Aligning and signing APK..." -ForegroundColor Yellow
$AlignedApk = Join-Path $BuildDir "aligned.apk"
& $Zipalign -p -f 4 $UnsignedApk $AlignedApk

$Keystore = Join-Path $BuildDir "debug.keystore"
if (-not (Test-Path $Keystore)) {
    & "$JavaHome\bin\keytool.exe" -genkeypair -v -keystore $Keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}

$FinalApk = Join-Path $ProjectDir "TouchRacerEnhanced.apk"
if (Test-Path $FinalApk) { Remove-Item -Force $FinalApk }
& $Apksigner sign --ks $Keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out $FinalApk $AlignedApk

Write-Host "`n===================================================" -ForegroundColor Green
Write-Host "  SUCCESS! APK BUILT AT:" -ForegroundColor Green
Write-Host "  $FinalApk" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green
