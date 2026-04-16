@ECHO OFF

IF "%DEBUG%" == "" @ECHO OFF
@REM ##########################################################################
@REM
@REM  Gradle startup script for Windows
@REM
@REM ##########################################################################

@REM Set local scope for the variables with windows NT shell
IF "%OS%"=="Windows_NT" SETLOCAL

set DIRNAME=%~dp0
IF "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@REM Resolve any "." and ".." in APP_HOME to make it shorter.
FOR %%i IN ("%APP_HOME%") DO SET APP_HOME=%%~fi

@REM Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@REM Find java.exe
IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
IF "%ERRORLEVEL%" == "0" GOTO execute

ECHO.
ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
ECHO.
ECHO Please set the JAVA_HOME variable in your environment to match the
ECHO location of your Java installation.

GOTO fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

IF EXIST "%JAVA_EXE%" GOTO execute

ECHO.
ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
ECHO.
ECHO Please set the JAVA_HOME variable in your environment to match the
ECHO location of your Java installation.

GOTO fail

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@REM End local scope for the variables with windows NT shell
IF "%ERRORLEVEL%"=="0" GOTO mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
IF "%OS%"=="Windows_NT" ENDLOCAL

:omega