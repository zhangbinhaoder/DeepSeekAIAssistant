@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║       DeepSeek AI Assistant - llama.cpp 安装脚本             ║
echo ║                  Version 2.0                                 ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

:: 检查 Git 是否安装
where git >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未检测到 Git
    echo.
    echo 请先安装 Git:
    echo   https://git-scm.com/downloads
    echo.
    pause
    exit /b 1
)
echo ✓ Git 已安装

:: 设置路径
set "SCRIPT_DIR=%~dp0"
set "CPP_DIR=%SCRIPT_DIR%app\src\main\cpp"
set "LLAMA_DIR=%CPP_DIR%\llama.cpp"

echo.
echo 📁 工作目录: %CPP_DIR%
echo.

:: 检查目标目录
if not exist "%CPP_DIR%" (
    echo ❌ 错误: 找不到 cpp 目录
    echo    预期路径: %CPP_DIR%
    echo.
    pause
    exit /b 1
)

cd /d "%CPP_DIR%"

:: 检查是否已存在 llama.cpp
if exist "%LLAMA_DIR%" (
    echo.
    echo ⚠️  检测到已存在的 llama.cpp 目录
    echo.
    choice /C YN /M "是否删除并重新下载? (Y=是, N=否)"
    if !errorlevel! equ 1 (
        echo.
        echo 正在删除旧版本...
        rd /s /q "%LLAMA_DIR%"
        if exist "%LLAMA_DIR%" (
            echo ❌ 删除失败，请手动删除目录后重试
            pause
            exit /b 1
        )
        echo ✓ 旧版本已删除
    ) else (
        echo.
        echo ℹ️  保留现有版本，跳过下载
        goto :check_result
    )
)

:: 下载 llama.cpp
echo.
echo ════════════════════════════════════════════════════════════════
echo 📥 正在下载 llama.cpp 源码...
echo    这可能需要几分钟，请耐心等待...
echo ════════════════════════════════════════════════════════════════
echo.

:: 使用浅克隆加速下载
git clone --depth 1 https://github.com/ggerganov/llama.cpp.git

if %errorlevel% neq 0 (
    echo.
    echo ❌ 下载失败！
    echo.
    echo 可能的原因:
    echo   1. 网络连接问题
    echo   2. GitHub 访问受限
    echo.
    echo 解决方案:
    echo   1. 检查网络连接
    echo   2. 使用代理或 VPN
    echo   3. 手动下载: https://github.com/ggerganov/llama.cpp/archive/refs/heads/master.zip
    echo      解压到: %CPP_DIR%\llama.cpp
    echo.
    pause
    exit /b 1
)

:check_result
echo.
echo ════════════════════════════════════════════════════════════════

:: 验证下载结果
if exist "%LLAMA_DIR%\CMakeLists.txt" (
    echo.
    echo ✅ llama.cpp 安装成功！
    echo.
    echo 下一步操作:
    echo   1. 在 Android Studio 中点击 "Sync Project with Gradle Files"
    echo   2. 执行 Build ^> Rebuild Project
    echo   3. 首次编译可能需要 5-10 分钟
    echo.
    echo 注意事项:
    echo   • 项目路径必须是纯英文，不能包含中文或空格
    echo   • 确保 NDK 已正确安装（通过 SDK Manager）
    echo   • 编译产物会显著增加 APK 大小（约 600MB+）
    echo.
) else (
    echo.
    echo ❌ 安装验证失败！
    echo    未找到 CMakeLists.txt
    echo.
    echo 请检查 %LLAMA_DIR% 目录内容
    echo.
)

echo ════════════════════════════════════════════════════════════════
echo.
pause
