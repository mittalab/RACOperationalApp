## Clean OLD Build
rmdir /s /q dist 2>nul

rmdir /s /q RACOperationalApp 2>nul


## Build Latest JAR
mvn clean package

-- It should generate the output ``target\RACOperationalApp-1.0.0.jar``

## Make destination directory
mkdir dist

copy target\RACOperationalApp-1.0.0.jar dist\app.jar

xcopy "C:\Users\29abh\AppData\Local\ms-playwright" "dist\ms-playwright" /E /I /Y

copy src\main\resources\app_icon.ico dist\app_icon.ico

## Build Executable
`jpackage --name RACOperationalApp --input dist --main-jar app.jar --main-class org.rac.NewMain --type app-image --icon dist\app_icon.ico --app-version 1.0 --vendor "Rank Achievers Classes"`


copy input_student_data.xlsx RACOperationalApp\input_student_data.xlsx