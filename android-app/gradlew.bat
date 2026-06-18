@rem Android Gradle wrapper script
@if "%DEBUG%"=="" @echo off
setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set GRADLE_OPTS=

set JAVA_EXE=java.exe
"%JAVA_EXE%" -version >NUL 2>&1
if not "%ERRORLEVEL%"=="0" (
    echo ERROR: JAVA_HOME is not set and no 'java' command could be found.
    echo Please set the JAVA_HOME variable in your environment.
    exit /b 1
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
