@echo off
cd /d "%~dp0"

:: Clean OLD Build
rmdir /s /q dist 2>nul
rmdir /s /q RACOperationalApp 2>nul

:: Build Latest JAR (generates target\RACOperationalApp-1.0.0.jar)
call mvn clean package
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

:: Make destination directory
mkdir dist

copy target\RACOperationalApp-1.0.0.jar dist\app.jar

xcopy "C:\Users\29abh\AppData\Local\ms-playwright" "dist\ms-playwright" /E /I /Y

copy src\main\resources\app_icon.ico dist\app_icon.ico

:: Build Executable
jpackage --name RACOperationalApp --input dist --main-jar app.jar --main-class org.rac.NewMain --type app-image --icon dist\app_icon.ico --app-version 1.0 --vendor "Rank Achievers Classes"
if errorlevel 1 (
    echo JPACKAGE FAILED
    pause
    exit /b 1
)

:: Copy Google Drive Credentials
copy client_secrets.json RACOperationalApp\client_secrets.json
xcopy tokens RACOperationalApp\tokens /i /e

:: Copy student data files
copy file_IX_Monday_student_data.xlsx RACOperationalApp\file_IX_Monday_student_data.xlsx
copy file_IX_Tuesday_student_data.xlsx RACOperationalApp\file_IX_Tuesday_student_data.xlsx
copy file_X_Monday_student_data.xlsx RACOperationalApp\file_X_Monday_student_data.xlsx
copy file_X_Tuesday_6_7_student_data.xlsx RACOperationalApp\file_X_Tuesday_6_7_student_data.xlsx
copy file_X_Tuesday_7_8_student_data.xlsx RACOperationalApp\file_X_Tuesday_7_8_student_data.xlsx

echo.
echo BUILD COMPLETE
pause
