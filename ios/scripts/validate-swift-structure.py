from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
files = sorted((root / "ShiLv").rglob("*.swift")) + sorted((root / "ShiLvTests").rglob("*.swift"))
errors = []

def strip_literals_and_comments(text: str) -> str:
    result = []
    i = 0
    state = "code"
    block_depth = 0
    while i < len(text):
        pair = text[i:i+2]
        if state == "code":
            if pair == "//": state = "line"; result.extend("  "); i += 2; continue
            if pair == "/*": state = "block"; block_depth = 1; result.extend("  "); i += 2; continue
            if text[i] == '"': state = "string"; result.append(' '); i += 1; continue
            result.append(text[i]); i += 1
        elif state == "line":
            if text[i] == "\n": state = "code"; result.append("\n")
            else: result.append(" ")
            i += 1
        elif state == "block":
            if pair == "/*": block_depth += 1; result.extend("  "); i += 2
            elif pair == "*/":
                block_depth -= 1; result.extend("  "); i += 2
                if block_depth == 0: state = "code"
            else: result.append("\n" if text[i] == "\n" else " "); i += 1
        elif state == "string":
            if text[i] == "\\" and i + 1 < len(text): result.extend("  "); i += 2
            elif text[i] == '"': state = "code"; result.append(" "); i += 1
            else: result.append("\n" if text[i] == "\n" else " "); i += 1
    if state in {"string", "block"}: errors.append(f"{current}: unterminated {state}")
    return "".join(result)

for file in files:
    current = file.relative_to(root)
    text = file.read_text(encoding="utf-8")
    clean = strip_literals_and_comments(text)
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    for offset, char in enumerate(clean):
        if char in "([{": stack.append((char, offset))
        elif char in pairs:
            if not stack or stack[-1][0] != pairs[char]:
                errors.append(f"{current}: unmatched {char} at offset {offset}"); break
            stack.pop()
    if stack: errors.append(f"{current}: unclosed {stack[-1][0]} at offset {stack[-1][1]}")

    imports = set(re.findall(r"^(?:@preconcurrency\s+)?import\s+(\w+)", text, re.MULTILINE))
    requirements = {
        "PHPhotoLibrary": "Photos", "PHAsset": "Photos", "PHImage": "Photos",
        "CLLocation": "CoreLocation", "CLGeocoder": "CoreLocation",
        "MapCameraPosition": "MapKit", "MapPolyline": "MapKit",
        "VNClassifyImageRequest": "Vision", "UIImage": "UIKit",
        "@Published": "Combine", "ObservableObject": "Combine"
    }
    for symbol, module in requirements.items():
        if symbol in text and module not in imports and "SwiftUI" not in imports:
            errors.append(f"{current}: uses {symbol} without importing {module}")

    if re.search(r"\btry!\b|\bas!\b", clean): errors.append(f"{current}: contains forced operation")
    if re.search(r"\bURLSession\b", clean): errors.append(f"{current}: unexpected network client")
    if "struct MemoryEvent" in text:
        event_body = text.split("struct MemoryEvent", 1)[1].split("struct TravelDay", 1)[0]
        if "visibleEvents" in event_body: errors.append(f"{current}: MemoryEvent must not reference trip/day visibleEvents")

if errors:
    raise SystemExit("\n".join(errors))
print(f"Swift structural validation ok: {len(files)} files, balanced tokens, framework imports checked")
