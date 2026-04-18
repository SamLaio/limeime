---
# Issue #51 — Key Label Font Shrinkage: Root Cause & Fix

**Related:** #47 (landscape keyboard clipping fix, 6.0.1)  
**Status:** Fixed  
**Date:** 2026-04-14 / Updated: 2026-04-19

---

## 1. User Report (Issue #51)

After upgrading 6.0.0 → 6.0.1, key label font appears **~2 sizes smaller** on all keys.

Observed behaviour patterns reported by users:
- Upgrading in-place (6.0.0 → 6.0.1 APK): labels are small immediately after upgrade.
- Clean reinstall of 6.0.1: sometimes restores large labels, sometimes does not.
- Reading a backup after a clean reinstall: may revert to small labels.
- Pixel 9 (Android 16, larger screen, gesture nav) is more severely affected than Pixel 5 (Android 14, smaller screen).
- No in-app setting change restores the labels; only a full uninstall + reinstall occasionally helps.

---

## 2. What Changed in #47 (6.0.1)

The #47 fix introduced two changes to the keyboard sizing pipeline:

### 2a. `getUsableDisplayWidth()` in `LIMEBaseKeyboard` constructor
Replaces `dm.widthPixels` with a WindowMetrics-based width that excludes system bar and display cutout insets (API 30+). Applied at keyboard construction time. This part is architecturally correct.

### 2b. `scaleHorizontally()` called from `LIMEKeyboardBaseView.onMeasure()`

```java
// LIMEKeyboardBaseView.onMeasure()
if (parentWidth > 0 && width > parentWidth) {
    mKeyboard.scaleHorizontally(parentWidth - getPaddingLeft() - getPaddingRight());
    width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
}
```

```java
// LIMEBaseKeyboard.scaleHorizontally()
public void scaleHorizontally(int newWidth) {
    if (newWidth <= 0 || mTotalWidth <= 0 || newWidth >= mTotalWidth) return;
    float ratio = (float) newWidth / (float) mTotalWidth;
    for (Key key : mKeys) {
        key.x = Math.round(key.x * ratio);
        key.width = Math.round(key.width * ratio);
        key.gap = Math.round(key.gap * ratio);
    }
    mTotalWidth = newWidth;
}
```

This is a **post-construction patch** — the keyboard was already fully built, and then every key's `x`, `width`, and `gap` are scaled down.

---

## 3. Root Cause

### Step 1 — `scaleHorizontally` reduces `key.width` below `mDefaultWidth`

After `scaleHorizontally` runs, every `key.width` is multiplied by `ratio < 1.0`. However, `mDefaultWidth` (the baseline key width set at construction) is **never updated** to reflect the new scale.

### Step 2 — `getLabelSizeScale()` fires incorrectly

```java
// LIMEBaseKeyboard.Key.getLabelSizeScale()
if (width < keyboard.getKeyWidth())   // key.width vs mDefaultWidth
    mLabelSizeScale = mSplitKeyboard ? 1f : mSplitedKeyWidthScale;
```

`keyboard.getKeyWidth()` returns `mDefaultWidth` — the original pre-scale value. After `scaleHorizontally`, `key.width < mDefaultWidth` is now true for **every key**, even in portrait on a normal phone. This sets `mLabelSizeScale = mSplitedKeyWidthScale` (a value < 1.0 computed for landscape split-keyboard mode).

### Step 3 — Label font size is directly reduced

```java
// LIMEKeyboardBaseView.onBufferDraw()
labelSize = (int)(mKeyTextSize * keySizeScale * labelSizeScale);
```

`labelSizeScale` is now `mSplitedKeyWidthScale` instead of `1.0`, making the rendered font smaller.

### Step 4 — The `static` cache makes it permanent

`mLabelSizeScale` is declared as `private static float` on the inner `Key` class. Once set to a non-zero value it is **cached for the entire process lifetime** — all subsequent keyboard instances and keys share the same poisoned value. It is only cleared by process death (full uninstall/reinstall).

```java
private static float mLabelSizeScale = 0f;

public float getLabelSizeScale() {
    if (mLabelSizeScale > 0) return mLabelSizeScale;  // returns cached value forever
    ...
}
```

### Why Pixel 9 is more affected

On devices with gesture navigation bars and display cutouts (larger modern phones), the delta between `dm.widthPixels` and the inset-aware `getUsableDisplayWidth()` is larger. This increases the probability that `width > parentWidth` is true in `onMeasure()`, triggering `scaleHorizontally()` more readily.

---

## 4. Design Critique of the #47 Fix

The #47 fix is architecturally unsound. The keyboard is built assuming one width, then silently re-scaled after the fact. All the sizing variables (`mDefaultWidth`, `mSplitedKeyWidthScale`, `mLabelSizeScale`) are designed to be **set once correctly at construction time** — they are not designed to be patched post-layout.

`getUsableDisplayWidth()` was already added to the constructor precisely to get the correct width upfront. The `scaleHorizontally()` call in `onMeasure()` is a second, conflicting correction that undermines that first correction. Since `getUsableDisplayWidth()` already provides the correct inset-aware width at construction time, `scaleHorizontally()` is both redundant and harmful — it must never fire, and its presence introduces the label shrinkage bug when it does.

---

## 5. Fix Applied

**Remove `scaleHorizontally()` entirely.**

`getUsableDisplayWidth()` in the constructor already computes the correct usable width (excluding system bars and display cutout insets) before key layout is built. `scaleHorizontally()` was a redundant post-construction safety net that introduced the label shrinkage side effect whenever it triggered. Removing it is the correct fix.

### Changes

| File                        | Change                                                                              |
| --------------------------- | ----------------------------------------------------------------------------------- |
| `LIMEBaseKeyboard.java`     | Removed `scaleHorizontally()` method                                                |
| `LIMEKeyboardBaseView.java` | Removed the `if (parentWidth > 0 && width > parentWidth)` block from `onMeasure()` |

### `LIMEKeyboardBaseView.onMeasure()` after fix

```java
int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
if (parentWidth < width + DEFAULT_PREVIEW_TOP_PADDING_PX) {
    width = parentWidth;
}
```

---

## 6. Verification

1. Upgrade from 6.0.0 → 6.0.1 APK on Pixel 9 (Android 16) — key labels must remain the same size as 6.0.0.
2. Verify landscape mode on a device with gesture navigation still does not clip the rightmost key column (the original #47 requirement must still hold, now ensured by `getUsableDisplayWidth()` alone).
3. Verify portrait mode on Pixel 5 (Android 14) — no regression.
