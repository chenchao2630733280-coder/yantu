from pathlib import Path
from PIL import Image
import json
import plistlib

root = Path(__file__).resolve().parent.parent
icon = root / "ShiLv/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
atlas = root / "ShiLv/Resources/Assets.xcassets/KansaiAtlas.imageset/kansai-atlas.png"
errors = []

with Image.open(icon) as image:
    if image.size != (1024, 1024):
        errors.append(f"AppIcon must be 1024x1024, got {image.size}")
    if image.mode not in ("RGB", "RGBA"):
        errors.append(f"AppIcon color mode is {image.mode}")
with Image.open(atlas) as image:
    if image.width < 1000 or image.height < 500:
        errors.append(f"Kansai atlas is too small: {image.size}")

for plist in [root / "ShiLv/Resources/Info.plist", root / "ShiLv/Resources/PrivacyInfo.xcprivacy"]:
    with plist.open("rb") as handle:
        plistlib.load(handle)
for manifest in root.glob("ShiLv/Resources/Assets.xcassets/**/Contents.json"):
    json.loads(manifest.read_text(encoding="utf-8"))

if errors:
    raise SystemExit("\n".join(errors))
print("iOS assets/plists validation ok: icon 1024x1024, atlas valid, manifests parse")
