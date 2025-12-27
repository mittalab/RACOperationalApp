# RAC Operational App

This is a JavaFX application designed to automate operational activities for a coaching center.

## Features

-   **Send Results to Parents:** This is the first activity supported by the application. It allows you to send student results to their parents via WhatsApp.

## How to Build and Run

### Prerequisites

-   **Java JDK 21 or higher:** You need to have the Java Development Kit installed. This application uses modern JavaFX features that require JDK 21 or higher. You can download a recommended version from [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21).
-   **Maven:** This project uses Maven for dependency management and building. You can download it from [here](https://maven.apache.org/download.cgi).
-   **Google Chrome:** You need to have Google Chrome installed.
-   **ChromeDriver:** You need to download the ChromeDriver that matches your version of Google Chrome. You can download it from [here](https://chromedriver.chromium.org/downloads).

### Configuration

Before running the application, you need to configure the `WhatsAppService` with the paths to your `chromedriver.exe` and your Chrome user profile.

1.  Open the file `src/main/java/org/rac/services/WhatsAppService.java`.
2.  Update the following lines with the correct paths:

    ```java
    System.setProperty("webdriver.chrome.driver", "C:/path/to/your/chromedriver.exe");
    // ...
    options.addArguments("user-data-dir=C:/path/to/your/chrome/profile");
    ```

    -   To find your Chrome profile path, navigate to `chrome://version` in your Chrome browser and look for "Profile Path".

### Building the Application

1.  Open a terminal or command prompt and navigate to the root directory of the project.
2.  Run the following Maven command to compile the project and create a JAR file:

    ```bash
    mvn clean package
    ```

    This will create a `RACOperationalApp-1.0-SNAPSHOT.jar` file in the `target` directory.

### Running the Application

You can run the application directly from the JAR file using the following command:

```bash
java -jar target/RACOperationalApp-1.0-SNAPSHOT.jar
```

### Creating a Windows Executable (Application Image)

You can use `jpackage` to create a self-contained application image (a directory containing your application and its own private Java runtime). This image will include the executable directly, without creating an installer. `jpackage` is included in JDK 14 and later, but it's recommended to use **JDK 21 or later** for this project.

1.  Make sure you have **JDK 21 or later** installed and that it is your active JDK when running the command.
2.  Run the following `jpackage` command from the root directory of the project. This command will create an application image directory. Make sure to replace `--input target` with the actual path to your `target` directory and `--main-jar RACOperationalApp-1.0-SNAPSHOT.jar` with the actual name of your JAR file.

    ```bash
    jpackage --name RACOperationalApp --input target --main-jar RACOperationalApp-1.0-SNAPSHOT.jar --main-class org.rac.Main
    ```

3.  After successful execution, `jpackage` will create a new directory (e.g., `RACOperationalApp`) in the current directory (or a specified output directory). Inside this directory, you will find the executable (`RACOperationalApp.exe`) which you can run directly.

## How to Use the Application

1.  Launch the application.
2.  From the main menu, select the "Send Results to Parents" activity.
3.  Fill in the required details:
    -   **Test Conducted Date:** The date the test was conducted.
    -   **Class:** The class for which the results are being sent.
    -   **Topic:** The topic of the test.
    -   **Result Heading:** The heading for the result image.
    -   **Total Marks:** The total marks for the test.
    -   **Excel File:** The Excel file containing the student data. The file should have four columns: Name, Phone Number, Marks Obtained, and Additional Details.
    -   **HTML Template:** The HTML template file for the result image. The template should contain placeholders like `<NAME_INPUT>`, `<MARKS_INPUT>`, etc.
4.  Click "Proceed".
5.  A summary will be shown. Click "OK" to start sending the messages.
6.  The application will open WhatsApp Web in a new Chrome window. You may need to scan the QR code on the first run.
7.  The application will then start sending the result images to the phone numbers in the Excel file.
8.  You can click "Abort" to stop the process at any time. If you abort, you will be prompted to save a report of the messages that have already been sent.
9.  When the process is complete, you can go back to the home screen or close the application.
