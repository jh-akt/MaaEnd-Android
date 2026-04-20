# Template Asset Workflow

Date: 2026-04-20

This note records the local workflow used in `MaaEnd-Android` when an upstream template image is close to correct, but the Android UI adds extra visual noise such as:

- a red/orange notification dot
- a theme inversion
- small Android-only decorations

The goal is to avoid editing upstream `MaaEnd` assets directly.  
All Android-only fixes should stay in this repo under `runtime/resource_adb/...`.

## 1. Where to put local template fixes

Use these locations:

- images:
  - `runtime/resource_adb/image/<Group>/...`
- pipeline overrides:
  - `runtime/resource_adb/pipeline/<Group>/...`

Example from this session:

- local images:
  - `runtime/resource_adb/image/SceneManager/ShoppingEntryBottomHalf.png`
  - `runtime/resource_adb/image/SceneManager/ShoppingEntryInvertedBottomHalf.png`
- local pipeline override:
  - `runtime/resource_adb/pipeline/SceneManager/SceneMenu.json`

Reason:

- this repo already packages `runtime/` into `assets/bundled_runtime/...`
- runtime prepare then overlays these into the device runtime directory
- this keeps Android-specific hacks out of upstream `MaaEnd`

## 2. When to use each strategy

### Use a lower-half template when

- the original icon is mostly correct
- the Android variant adds a dot or badge in the upper-right corner
- the lower half still keeps the icon silhouette

This was the preferred fix for `ShoppingEntry`.

### Use a left-half template when

- the right side is noisy
- the left side contains the stable part of the icon
- a lower-half crop would remove too much structure

This was tried first, but was later replaced by the lower-half approach.

### Use an inverted template when

- the Android icon shape is correct
- the color polarity differs from the desktop/original template
- there is already an upstream template in the opposite light/dark style that can be used as a color reference

This was used to generate `ShoppingEntryInverted.png` from:

- source shape: `assets/resource_adb/image/SceneManager/ShoppingEntry.png`
- color reference: `assets/resource/image/SceneManager/ShoppingEntry2.png`

## 3. Current naming convention

Use descriptive suffixes. Prefer:

- `BottomHalf`
- `LeftHalf`
- `Inverted`
- `InvertedBottomHalf`
- `RedDot`

Good examples:

- `ShoppingEntryBottomHalf.png`
- `ShoppingEntryInvertedBottomHalf.png`

Avoid generic names like:

- `new.png`
- `test.png`
- `copy.png`

## 4. Standard process

1. Identify the failing node from logs.
2. Find the template path in upstream JSON.
3. Check whether an `resource_adb` image already exists.
4. Decide whether the fix should be:
   - crop
   - recolor/invert
   - extra template variant
5. Generate the new image under `runtime/resource_adb/image/...`
6. Add or update a local JSON override under `runtime/resource_adb/pipeline/...`
7. Rebuild the APK.
8. If a device is connected, install and retest.

## 5. Example: bottom-half crop

Used when the upper part contains a notification dot.

Source:

- `assets/resource_adb/image/SceneManager/ShoppingEntry.png`

Output:

- `runtime/resource_adb/image/SceneManager/ShoppingEntryBottomHalf.png`

Reference command:

```bash
python3 - <<'PY'
from pathlib import Path
from PIL import Image

src = Path('/workspace/MaaEnd/assets/resource_adb/image/SceneManager/ShoppingEntry.png')
out = Path('/workspace/MaaEnd-Android/runtime/resource_adb/image/SceneManager/ShoppingEntryBottomHalf.png')

img = Image.open(src).convert('RGBA')
top = img.height // 2
cropped = img.crop((0, top, img.width, img.height))
out.parent.mkdir(parents=True, exist_ok=True)
cropped.save(out)
print(out)
PY
```

Rule:

- keep full width
- remove only the top half
- preserve alpha

## 6. Example: inverted template based on upstream resource colors

Used when the Android shape is correct, but the icon needs the opposite visual polarity.

Source shape:

- `assets/resource_adb/image/SceneManager/ShoppingEntry.png`

Color reference:

- `assets/resource/image/SceneManager/ShoppingEntry2.png`

Output:

- `runtime/resource_adb/image/SceneManager/ShoppingEntryInverted.png`

Reference command:

```bash
python3 - <<'PY'
from pathlib import Path
from PIL import Image

adb_src = Path('/workspace/MaaEnd/assets/resource_adb/image/SceneManager/ShoppingEntry.png')
res2_src = Path('/workspace/MaaEnd/assets/resource/image/SceneManager/ShoppingEntry2.png')
out = Path('/workspace/MaaEnd-Android/runtime/resource_adb/image/SceneManager/ShoppingEntryInverted.png')

adb_img = Image.open(adb_src).convert('RGBA')
res2 = Image.open(res2_src).convert('RGBA')
res_pixels = [px for px in res2.getdata() if px[3] > 0]

dark = min(res_pixels, key=lambda p: p[0] + p[1] + p[2])[:3]
light = max(res_pixels, key=lambda p: p[0] + p[1] + p[2])[:3]

out_img = Image.new('RGBA', adb_img.size)
out_pixels = []
for r, g, b, a in adb_img.getdata():
    if a == 0:
        out_pixels.append((0, 0, 0, 0))
        continue
    lum = (r + g + b) / 3.0
    inv_norm = 1.0 - lum / 255.0
    nr = int(dark[0] + (light[0] - dark[0]) * inv_norm)
    ng = int(dark[1] + (light[1] - dark[1]) * inv_norm)
    nb = int(dark[2] + (light[2] - dark[2]) * inv_norm)
    out_pixels.append((nr, ng, nb, a))

out_img.putdata(out_pixels)
out.parent.mkdir(parents=True, exist_ok=True)
out_img.save(out)
print(out)
PY
```

Rule:

- use the Android icon as the shape source
- use upstream `resource` image as the color-range reference
- preserve alpha

## 7. Example: combine both

When the icon needs both polarity change and badge avoidance:

1. generate `...Inverted.png`
2. crop it into `...InvertedBottomHalf.png`

Reference command:

```bash
python3 - <<'PY'
from pathlib import Path
from PIL import Image

src = Path('/workspace/MaaEnd-Android/runtime/resource_adb/image/SceneManager/ShoppingEntryInverted.png')
out = Path('/workspace/MaaEnd-Android/runtime/resource_adb/image/SceneManager/ShoppingEntryInvertedBottomHalf.png')

img = Image.open(src).convert('RGBA')
top = img.height // 2
cropped = img.crop((0, top, img.width, img.height))
out.parent.mkdir(parents=True, exist_ok=True)
cropped.save(out)
print(out)
PY
```

## 8. How to wire a local template into the pipeline

Use a local override file under `runtime/resource_adb/pipeline/...`

Example:

- `runtime/resource_adb/pipeline/SceneManager/SceneMenu.json`

Current example content:

```json
{
  "__ScenePrivateWorldEnterMenuEntryShopClick": {
    "recognition": {
      "type": "TemplateMatch",
      "param": {
        "roi": [700, 0, 425, 80],
        "template": [
          "SceneManager/ShoppingEntryBottomHalf.png",
          "SceneManager/ShoppingEntryInvertedBottomHalf.png"
        ]
      }
    }
  }
}
```

Guidelines:

- override only the minimum node necessary
- do not copy the whole upstream file unless needed
- prefer adding templates before changing ROI/threshold

## 9. Build and install

Rebuild:

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
sh gradlew :app:assembleDebug
```

Install:

```bash
$HOME/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `adb` says no device:

```text
adb: no devices/emulators found
```

reconnect the device first, then install again.

## 10. Verification checklist

After rebuilding, verify:

- the new PNG exists under:
  - `runtime/resource_adb/image/...`
- the override JSON points at the right filenames
- the APK contains the override path
- after `prepareRuntime()`, the device runtime contains the copied files
- logs now move past the original failing node

Useful checks:

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | rg "ShoppingEntry|SceneMenu.json"
adb shell find /data/local/tmp/com.maaend.android/maaend-runtime/v1 -name "ShoppingEntry*"
adb logcat -d -s MaaFrameworkBridge | tail -n 200
```

## 11. Practical advice

- Start with the smallest possible image change.
- Prefer crop-based fixes before more aggressive recolor work.
- Keep local Android-only fixes in this repo, not in upstream `MaaEnd`.
- If a badge/dot is only in one corner, cropping is usually more robust than hand-drawing.
- If color polarity differs, derive the recolor from an existing upstream variant rather than inventing colors manually.
