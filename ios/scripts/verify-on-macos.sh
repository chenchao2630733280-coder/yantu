#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[0/5] Validating generated project, privacy manifest and requirement contracts"
node scripts/generate-project.js
node scripts/validate-project.js
python3 scripts/validate-assets.py
python3 scripts/validate-swift-structure.py
node scripts/test-detector-contract.js
node scripts/validate-requirements.js

echo "[1/5] Listing Xcode project"
xcodebuild -project ShiLv.xcodeproj -list

echo "[2/5] Building for iOS Simulator"
xcodebuild \
  -project ShiLv.xcodeproj \
  -scheme ShiLv \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build

echo "[3/5] Running unit tests on an available iPhone simulator"
DEVICE_ID="$(xcrun simctl list devices available --json | /usr/bin/python3 -c 'import json,sys; d=json.load(sys.stdin)["devices"]; print(next(x["udid"] for runtime in d.values() for x in runtime if x["name"].startswith("iPhone")))')"
xcodebuild \
  -project ShiLv.xcodeproj \
  -scheme ShiLv \
  -destination "platform=iOS Simulator,id=${DEVICE_ID}" \
  test

echo "[4/5] Building unsigned Release for device SDK"
xcodebuild \
  -project ShiLv.xcodeproj \
  -scheme ShiLv \
  -configuration Release \
  -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO \
  build

echo "All macOS/Xcode verification gates passed."
