# sir-android developer recipes

# Default flavor for local development
flavor := "foss"

# Build debug APK
build:
    ./gradlew assemble{{capitalize(flavor)}}Debug --no-daemon --console=rich --warning-mode=none

# Run unit tests
test:
    ./gradlew test{{capitalize(flavor)}}DebugUnitTest --no-daemon

# Install debug build on connected device.
# If android.device.serial is set in local.properties, targets that device;
# otherwise lets AGP pick (works when only one device is connected).
install:
    #!/usr/bin/env bash
    set -euo pipefail
    serial=$(grep -m1 '^android.device.serial=' local.properties 2>/dev/null \
        | cut -d= -f2 | tr -d '[:space:]' || true)
    if [[ -n "$serial" ]]; then
        ANDROID_SERIAL="$serial" ./gradlew :app:install{{capitalize(flavor)}}Debug --no-daemon
    else
        ./gradlew :app:install{{capitalize(flavor)}}Debug --no-daemon
    fi

# Build + install in one step
deploy: build install

# Build FOSS variant
build-foss:
    ./gradlew assembleFossDebug --no-daemon --console=rich --warning-mode=none

# Build full variant (with Firebase)
build-full:
    ./gradlew assemblePlayDebug --no-daemon

# Refactor safety baseline (matches CI's core local checks)
verify:
    ./gradlew assembleFossDebug testPlayDebugUnitTest :wear:testDebugUnitTest :core:model:testDebugUnitTest :core:directory:testDebugUnitTest :core:playback:testDebugUnitTest :core:persistence:testDebugUnitTest lintPlayDebug --no-daemon --parallel --build-cache
