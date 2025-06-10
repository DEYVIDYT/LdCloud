@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Split up the JVM options only if DEFAULT_JVM_OPTS is not null
if not "%DEFAULT_JVM_OPTS%" == "" (
    call :parseJvmOpts %DEFAULT_JVM_OPTS%
)

set JAVA_OPTS=%JAVA_OPTS% %GRADLE_OPTS%
call :parseJvmOpts %JAVA_OPTS%

@rem Execute Gradle
"%JAVA_EXE%" %JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if not "" == "%GRADLE_EXIT_CONSOLE%" (
  exit 1
) else (
  exit /b 1
)

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
exit /b %ERRORLEVEL%

@rem Replaces all occurrences of a string with another string in a variable
@rem Inspired by http://www.dostips.com/DtTipsStringOperations.php#Function.strReplace
@rem Arguments: %1 variable to be updated, %2 string to be replaced, %3 replacement string
:strReplace
  setlocal ENABLEDELAYEDEXPANSION
  set "var=%~1"
  set "str=%~2"
  set "rep=%~3"
  set "result=!%var%:%str%=%rep%!"
  endlocal&set "%1=%result%"
goto :eof

@rem Parses the JVM options passed in through JAVA_OPTS and GRADLE_OPTS
@rem Arguments: %* JVM options
:parseJvmOpts
  setlocal ENABLEDELAYEDEXPANSION
  set "jvmOptions="
  :parseLoop
  if not "%1"=="" (
    set "opt=%~1"
    REM Separate leading '"'
    if "!opt:~0,1!" == "" (
      set "opt=!opt:~1!"
      set "jvmOptions=!jvmOptions! \""
    )
    REM Separate trailing '"'
    if "!opt:~-1!" == "" (
      set "opt=!opt:~0,-1!"
      set "jvmOptions=!jvmOptions!!opt!\" "
    ) else (
      set "jvmOptions=!jvmOptions!!opt! "
    )
    shift
    goto :parseLoop
  )
  endlocal&set "JVM_OPTS=%JVM_OPTS%%jvmOptions%"
goto :eof
