@echo off
echo ?? NDK ? CMake ????...
echo.

REM ?? ANDROID_HOME ????
if defined ANDROID_HOME (
    echo ANDROID_HOME: %ANDROID_HOME%
) else (
    echo ANDROID_HOME ???
)

REM ?? NDK ??
if exist "%ANDROID_HOME%\ndk" (
    echo.
    echo NDK ????: %ANDROID_HOME%\ndk
    dir /AD "%ANDROID_HOME%\ndk"
) else if exist "%LOCALAPPDATA%\Android\Sdk\ndk" (
    echo.
    echo NDK ????: %LOCALAPPDATA%\Android\Sdk\ndk
    dir /AD "%LOCALAPPDATA%\Android\Sdk\ndk"
) else (
    echo.
    echo NDK ???
    echo ?? Android Studio ??? NDK
)

REM ?? CMake ??
if exist "%ANDROID_HOME%\cmake" (
    echo.
    echo CMake ????: %ANDROID_HOME%\cmake
    dir "%ANDROID_HOME%\cmake"
) else if exist "%LOCALAPPDATA%\Android\Sdk\cmake" (
    echo.
    echo CMake ????: %LOCALAPPDATA%\Android\Sdk\cmake
    dir "%LOCALAPPDATA%\Android\Sdk\cmake"
) else (
    echo.
    echo CMake ???
    echo ?? Android Studio ??? CMake
)

echo.
echo ????
pause
