#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
node ios/scripts/generate-project.js
node ios/scripts/validate-project.js
python3 ios/scripts/validate-assets.py
python3 ios/scripts/validate-swift-structure.py
node ios/scripts/test-detector-contract.js
node ios/scripts/validate-requirements.js
