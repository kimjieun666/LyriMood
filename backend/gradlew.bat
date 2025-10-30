@echo off
setlocal

set DEFAULT_JVM_OPTS=-Xmx64m -Xms64m
set SCRIPT_DIR=%~dp0
set CLASSPATH=%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_CMD=java"
)

if exist "%JAVA_CMD%" goto execute
call :findOnPath "%JAVA_CMD%"
if errorlevel 1 (
  echo JAVA 실행 파일을 찾을 수 없습니다. JAVA_HOME을 확인하세요. 1>&2
  exit /b 1
)

:execute
"%JAVA_CMD%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %errorlevel%

:findOnPath
for %%I in (%~1) do (
  if not "%%~$PATH:I"=="" (
    set "JAVA_CMD=%%~$PATH:I"
    exit /b 0
  )
)
exit /b 1
