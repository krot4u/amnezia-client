#!/usr/bin/env bash
set -euo pipefail

# Run from the prepared upstream checkout. Signing credentials come only from the environment.
: "${QT_ROOT_PATH:?Set QT_ROOT_PATH to Qt 6.10.3 directory}"
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${QT_ANDROID_KEYSTORE_PATH:?Set release keystore path}"
: "${QT_ANDROID_KEYSTORE_ALIAS:?Set release key alias}"
: "${QT_ANDROID_KEYSTORE_STORE_PASS:?Set release keystore password}"
export APP_ANDROID_MIN_SDK=31
export APP_ANDROID_VERSION_CODE_OFFSET=0
export CLIENT_ENABLE_APP_UPDATES=0
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-4}"
export JOBS="${JOBS:-4}"

# The fork uses its own update channel; never offer an incompatible upstream APK.
cmake -S . -B deploy/build/home-wifi -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$QT_ROOT_PATH/android_arm64_v8a/lib/cmake/Qt6/qt.toolchain.cmake" \
    -DQT_HOST_PATH="${QT_HOST_PATH:-$QT_ROOT_PATH/gcc_64}" \
    -DANDROID_SDK_ROOT="$ANDROID_HOME" \
    -DANDROID_NDK_ROOT="$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION:-27.0.11718014}" \
    -DAPP_ANDROID_MIN_SDK=31 -DAPP_ANDROID_VERSION_CODE_OFFSET=0 \
    -DCLIENT_ENABLE_APP_UPDATES=0 -DCLIENT_APPLICATION_NAME="Amnezia Home" \
    -DCLIENT_ANDROID_PACKAGE=org.amnezia.vpn.home \
    -DQT_ANDROID_ABIS=arm64-v8a -DQT_ANDROID_SIGN_APK=TRUE
cmake --build deploy/build/home-wifi --config Release

# Verify an actual signed APK exists; never publish a stale file from another build.
apk=deploy/build/home-wifi/client/android-build/AmneziaVPN.apk
test -s "$apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify "$apk"
