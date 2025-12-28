#!/bin/bash
# Rust Android Build Script
# Build agent_core library for Android targets

set -e

echo "============================================"
echo "Building Rust Agent Core for Android"
echo "============================================"

# Check cargo-ndk
if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

# Add Android targets
echo "Adding Android targets..."
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android

# Build directory
BUILD_DIR="../app/src/main/jniLibs"
mkdir -p "$BUILD_DIR/arm64-v8a"
mkdir -p "$BUILD_DIR/armeabi-v7a"
mkdir -p "$BUILD_DIR/x86_64"
mkdir -p "$BUILD_DIR/x86"

# Build for each architecture
echo ""
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a -o "$BUILD_DIR" build --release

echo ""
echo "Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a -o "$BUILD_DIR" build --release

echo ""
echo "Building for x86_64..."
cargo ndk -t x86_64 -o "$BUILD_DIR" build --release

echo ""
echo "Building for x86..."
cargo ndk -t x86 -o "$BUILD_DIR" build --release

# Verify builds
echo ""
echo "============================================"
echo "Build complete! Checking output files..."
echo "============================================"

for arch in arm64-v8a armeabi-v7a x86_64 x86; do
    lib_path="$BUILD_DIR/$arch/libagent_core.so"
    if [ -f "$lib_path" ]; then
        size=$(ls -lh "$lib_path" | awk '{print $5}')
        echo "✓ $arch: $size"
    else
        echo "✗ $arch: NOT FOUND"
    fi
done

echo ""
echo "Libraries are in: $BUILD_DIR"
