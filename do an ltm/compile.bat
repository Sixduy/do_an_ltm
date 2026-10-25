@echo off
chcp 65001 > nul
echo =======================================================
echo          BIEN DICH DO AN LAP TRINH MANG (LTM)
echo =======================================================

if not exist "target\classes" mkdir "target\classes"
if not exist "data\uploads" mkdir "data\uploads"

echo Dang bien dich cac tap tin Java...
dir /s /b src\main\java\*.java > sources_tmp.txt
powershell -NoProfile -ExecutionPolicy Bypass -Command "$lines = Get-Content sources_tmp.txt | ForEach-Object { '\"' + $_.Replace('\', '/') + '\"' }; Set-Content -Path sources.txt -Value $lines -Encoding ASCII"
if exist sources_tmp.txt del sources_tmp.txt
javac -encoding UTF-8 -d target\classes @sources.txt
set COMPILE_RES=%ERRORLEVEL%
if exist sources.txt del sources.txt

if %COMPILE_RES% EQU 0 (
    echo [THANH CONG] Da bien dich xong toan bo du an vao target/classes!
) else (
    echo [THAT BAI] Co loi trong qua trinh bien dich.
)
pause
