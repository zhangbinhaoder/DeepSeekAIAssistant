# Rust Android Build Script (PowerShell)
# Build agent_core library for Android targets

$ErrorActionPreference = "Stop"

Write-Host "============================================"
Write-Host "Building Rust Agent Core for Android"
Write-Host "============================================"
Write-Host ""

# Check if cargo is available
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: Rust/Cargo not found. Please install Rust first."
    Write-Host "Visit: https://rustup.rs/"
    exit 1
}

# Check cargo-ndk
$cargoNdk = cargo install --list | Select-String "cargo-ndk"
if (-not $cargoNdk) {
    Write-Host "Installing cargo-ndk..."
    cargo install cargo-ndk
}

# Add Android targets
Write-Host "Adding Android targets..."
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android

# Build directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ScriptDir "..\app\src\main\jniLibs"

# Create directories
@("arm64-v8a", "armeabi-v7a", "x86_64", "x86") | ForEach-Object {
    $dir = Join-Path $BuildDir $_
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# Build for each architecture
Write-Host ""
Write-Host "Building for arm64-v8a..."
Set-Location $ScriptDir
cargo ndk -t arm64-v8a -o $BuildDir build --release

Write-Host ""
Write-Host "Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a -o $BuildDir build --release

Write-Host ""
Write-Host "Building for x86_64..."
cargo ndk -t x86_64 -o $BuildDir build --release

Write-Host ""
Write-Host "Building for x86..."
cargo ndk -t x86 -o $BuildDir build --release

# Verify builds
Write-Host ""
Write-Host "============================================"
Write-Host "Build complete! Checking output files..."
Write-Host "============================================"

@("arm64-v8a", "armeabi-v7a", "x86_64", "x86") | ForEach-Object {
    $libPath = Join-Path $BuildDir "$_\libagent_core.so"
    if (Test-Path $libPath) {
        $size = (Get-Item $libPath).Length / 1KB
        Write-Host ("OK " + $_ + ": " + [math]::Round($size, 1) + " KB") -ForegroundColor Green
    } else {
        Write-Host ("MISSING " + $_ + ": NOT FOUND") -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Libraries are in: $BuildDir"
