@echo off
chcp 65001 > nul
cd /d "%~dp0..\scr\backend"
if errorlevel 1 (
    echo [LOI] Khong tim thay thu muc ma nguon scr\backend!
    if "%1"=="" pause
    exit /b 1
)

REM Bien dich toan bo ma nguon Backend Java voi co kiem tra nghiem ngat -Xlint:all
echo [1/2] Dang bien dich ma nguon Backend Java tai scr\backend ...
javac -Xlint:all -encoding UTF-8 -cp "lib/*;src" src/config/*.java src/model/*.java src/dao/*.java src/api/*.java
if errorlevel 1 (
    echo [LOI] Bien dich ma nguon Java that bai! Vui long kiem tra lai JDK.
    if "%1"=="" pause
    exit /b 1
)

if /i "%1"=="--compile-only" (
    del /s /q src\*.class > nul 2>&1
    echo [OK] Bien dich thanh cong 100%% voi 0 loi va 0 canh bao -Xlint:all!
    exit /b 0
)

if not exist "..\config\database.properties" (
    if exist "..\config\database.properties.example" (
        copy /y "..\config\database.properties.example" "..\config\database.properties" > nul
        echo [THONG BAO] Da khoi tao tep ..\config\database.properties tu mau mac dinh.
    )
)

echo [2/2] Dang khoi dong May chu API tai http://localhost:8080 ...
java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "lib/*;src" api.MayChuAPI
if "%1"=="" pause