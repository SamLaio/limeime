# Emoji Keyboard — iOS + Android v1 (English layout first)

> **v1 scope** (this document): iOS + Android emoji keyboard panel, emoji-launcher key on the **English keyboard layout first**, target Emoji 17.0.
>
> **v2 (deferred, separate plan)**: phonetic/symbol-page coverage and skin-tone picker. The data rebuild, search pipeline, and emoji recent-tab behavior below are platform-agnostic so both v1 platform implementations share one schema and behavior.

## Context

User asked whether LimeIME can add an emoji button on the keyboard that "links to the iOS emoji keyboard". The literal request — directly invoking Apple's system Emoji keyboard from a custom keyboard extension — is **not possible** through any public iOS API. The realistic, cross-platform interpretation is:

> Add a dedicated emoji key on the LimeIME keyboard that opens an **in-keyboard emoji panel** rendered by LimeIME itself, using a freshly-rebuilt `emoji.db` as the data source.

This is exactly what every third-party iOS keyboard does (Gboard, SwiftKey, etc.) and is the natural Android approach as well — Android has no separate "system emoji keyboard" to link to in the first place; emoji are emitted by whichever IME is active. So the universal design is: **same UX, same preference keys, same data source, parallel platform-native implementations.**

The current `emoji.db` is outdated (issue #29) — older Unicode/emoji version, no category data, and the `(tag, value)` keyword-row schema makes high-quality search hard. v1 therefore **rebuilds `emoji.db` from CLDR/emojibase up front**, including category, multilingual names, and keyword improvements for both iOS and Android.

### Why universal

- Both platforms already inject emoji into the candidate bar from the same `emoji.db`.
- Both platforms already share the `enable_emoji` / `enable_emoji_position` preference keys.
- Replacing the data source benefits both candidate-bar injection and the new panel, with one rebuild and a thin migration shim.
- Keeping the UX, preference names, and visual hierarchy identical reduces user confusion when switching devices and keeps the docs/screenshots reusable.

## Goal

- Add a dedicated emoji key on the LimeIME **English keyboard** layout's bottom row on iOS and Android.
- Tapping it opens an emoji picker panel that **replaces** the keys area (candidate bar stays visible where the platform normally shows it) — the regular keyboard rows (incl. space) are hidden while the emoji panel is shown.
- Panel UX **mirrors the iOS system Emoji keyboard**:
  - **Search field at the top** of the emoji panel (filter emoji by tag/name).
  - **Horizontally scrollable/paged emoji grid** of large emoji glyphs, organized by category in one continuous surface.
  - **Icon-only category bookmark strip** at the bottom for quick jump (Recent, Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags style icons; use the closest available SF Symbols / glyphs).
  - **ABC** key at the bottom-left of the bookmark strip to dismiss the panel and return to the regular English keyboard.
  - **Backspace** key at the bottom-right of the bookmark strip (so the user can correct a mistakenly inserted emoji without leaving the panel).
  - **No space key** while the emoji panel is active.
  - When the emoji search field is active, switch into iOS-style **emoji search mode**: show a regular English keyboard for typing the query, and show search results in a horizontally scrollable emoji candidate strip above that keyboard.
- Visibility of the button is gated by a new preference `enable_emoji_button` (default `true`).
- Data source is a rebuilt `emoji.db` (Emoji 17.0, see "Data rebuild" below). The same DB ships in `Database/` and is consumed unchanged by the existing candidate-injection path on both platforms.

## Non-goals (v1)

- Emoji-launcher key on phonetic, symbol, or other non-English layouts — deferred to v2 unless a platform already has a low-risk existing slot.
- Switching to Apple's system Emoji keyboard (impossible from an iOS extension).
- Skin-tone modifier picker — follow-up work after v1 ships.

## Shared contract (must match exactly)

| Concept | Value |
|---|---|
| Preference key (visibility) | `enable_emoji_button` (Bool, default `true`) |
| Emoji-key special code | `-201` (opens the panel) |
| Panel-internal special codes | `-202` ABC (dismiss panel), `-5` backspace (reuse existing), `-203..-212` icon category jump/bookmark buttons |
| Commit method | Insert the emoji string at cursor (no composition) |
| Button glyph | `😀` literal label (fallback SF Symbol `face.smiling` if the system font can't render it) |
| Bottom-row position (iPhone) | English layout `lime_abc`: emoji key takes the **current `中` slot** (10% width, between `done` and `,`). The `中` key (`switchToIM = -10`) moves up to the home row (`k l m n o p q r s`) which currently ends at 90% width and has a 10% empty slot — `中` becomes the 10th key on that row. |
| Bottom-row position (iPad) | English iPad layouts trim `space` and place the emoji key immediately **left of space** and the microphone key immediately **right of space**. |
| Bottom-row position (Android phone/tablet) | English keyboard layout gets a `😀` launcher in the same role as iOS: a modifier-style bottom-row key that opens the panel. Prefer replacing an existing language-switch/emoji-capable modifier slot; otherwise trim `space` by one key width and place `😀` near the left of `space`, with `ABC` used only inside the emoji panel. |
| Recent category | First emoji category/page. Shows recently committed emoji newest-first from `emoji_user`; if no usage exists yet, show the same default popular seed used by Android's current implementation. Android current behavior is the reference; iOS must match it. |
| Data source | New `emoji` table + `emoji_fts` index in rebuilt `emoji.db` (see [EMOJI_DB_V2.md](EMOJI_DB_V2.md)) |

## Current implementation gap — hardcoded category pages

Investigation after the initial iOS/Android emoji-panel implementation found that the normal category pages are still backed by hardcoded fallback arrays instead of the full `emoji_data` catalog:

- iOS: `EmojiPanelFallback.categories` in `KeyboardViewController.swift`.
- Android: the former `EMOJI_CATEGORIES` hardcoded catalog in `LIMEService.java` (now retained only as `FALLBACK_EMOJI_CATEGORIES`).
- The two hardcoded lists are effectively identical: 272 visible slots, 261 unique emoji.
- The bundled `Database/emoji.db` currently contains 1707 distinct `emoji_data` rows.
- `emoji_data` includes a full `People & Body` group with 376 emoji, but the current category strip has no dedicated People page.
- Search and candidate emoji lookup can reach the DB-backed catalog, but normal category browsing exposes only the static curated subset.

This is not acceptable for the final v1 behavior. The hardcoded arrays are fallback seed data only. The primary category pages must be loaded from `emoji_data`.

Bundled DB counts at the time of this investigation:

| DB group | Count |
|---|---:|
| Smileys & Emotion | 163 |
| People & Body | 376 |
| Animals & Nature | 154 |
| Food & Drink | 129 |
| Travel & Places | 170 |
| Activities | 74 |
| Objects | 217 |
| Symbols | 155 |
| Flags | 269 |
| **Total** | **1707** |

## Current iOS implementation status

iOS now loads normal emoji category pages from the DB-backed `emoji_data` catalog instead of showing only the hardcoded fallback subset.

Current behavior:

- Category pages are built from `LimeDB.loadEmojiCategoryPages()`.
- DB-backed categories follow the fixed catalog group order:
  - Smileys & Emotion
  - People & Body
  - Animals & Nature
  - Food & Drink
  - Travel & Places
  - Activities
  - Objects
  - Symbols
  - Flags
- Each category is ordered by `sort_order ASC`.
- User usage does not reorder normal category pages. Usage affects Recent and emoji search/candidate ranking only.
- The first page remains Recent, backed by `emoji_user`; if no recent emoji exist, the fallback popular seed is used.
- Hardcoded fallback arrays remain only as offline/failure fallback data.

### iOS category expansion

The iOS emoji panel now expands each DB category into as many pages as needed. This fixes the earlier bug where each category only rendered one hardcoded page even though the DB contained many more emoji.

Implementation notes:

- Category paging is calculated from the current emoji viewport and cell capacity.
- The category bookmark strip still jumps by semantic category, not by individual physical page.
- Swiping within a large category advances through that category's pages before moving into the next category.
- The page/category highlighter maps the current physical page back to its semantic category.

### iOS prewarm and cache

The iOS keyboard now has a best-effort in-memory prewarm for DB-backed category pages.

Current timing:

1. `KeyboardViewController.setupDatabase()` finishes DB bootstrap.
2. The controller receives the ready DB/SearchServer on the main thread.
3. `preloadEmojiCategoryPages()` starts on a background queue.
4. `loadEmojiCategoryPages()` uses the cached pages if prewarm finished before the user opens the emoji panel.

Important caveat:

- This is an in-memory prewarm, not a persisted preseed.
- If the user taps the emoji key before DB setup and category prewarm finish, the first open can still pay the synchronous category load cost.
- This cache currently lives in `KeyboardViewController`, which is an architecture leak tracked in [IOS_LIMEDB_LEAK.md](IOS_LIMEDB_LEAK.md). The final shape should move the cache behind `SearchServer` or a dedicated provider owned by `SearchServer`.

### Emoji category API ownership

The DB-backed category-page API was designed and implemented on iOS first. For emoji category browsing, Android should sync to the iOS API shape rather than forcing iOS to rename this part to an older Android `SearchServer` surface.

Parity target:

- iOS keeps `loadEmojiCategoryPages()` as the semantic API for the full normal emoji grid.
- Android adds the matching `SearchServer.loadEmojiCategoryPages()` API and delegates to `LimeDB.loadEmojiCategoryPages()` or the Android equivalent.
- Both platforms return the same logical page structure: Recent first, then catalog categories in fixed group order.
- `preloadEmojiCategoryPages()` or the equivalent prewarm hook should also live behind `SearchServer` on both platforms.
- Existing Android emoji APIs such as `searchEmoji`, `loadRecentEmoji`, and `recordEmojiUsage` remain Android-name-compatible; category-page loading is the exception where Android follows the iOS-first API.

### iOS page-gap fix

iOS now collapses the visual gap between pages inside the same category.

Expected behavior:

- Pages within a large category sit edge-to-edge.
- A category boundary may still have the normal outer inset so category transitions remain readable.
- Horizontal scrolling should feel like one continuous iOS-style emoji surface, not separated cards.

### iOS rendering performance

The iOS panel also uses virtual rendering for the horizontally paged surface:

- The scroll view keeps the full content width.
- Only the visible/nearby page window is rendered.
- Reusable emoji labels are pooled to reduce allocation churn.
- Pagination results are cached and invalidated only when the emoji pages or cell capacity change.

This makes paging smooth after the emoji panel is open. The remaining lag risk is first open before category prewarm has completed.

## Concrete keyboard layout contract

This section records the concrete layout decisions from the iOS + Android visual-debugging session. Treat this as the platform parity target.

### English keyboard launcher placement

Phone English layouts use the same semantic placement on both platforms:

- The `中` key belongs on the leftmost key of the ASDF/home row.
- The old bottom-row `中` location becomes the emoji launcher key (`-201`, label `😀` or platform smile fallback).
- The emoji launcher is a modifier-style key, not a text-input key, and opens the LimeIME emoji panel without committing text.
- The English keyboard must still be able to switch back to Chinese IM through the relocated `中` key.
- On Android, returning from emoji mode must not turn the candi-bar/microphone area into the Android keyboard-hide/up icon.

iPhone and Android phone should therefore feel the same even if their native key rendering differs. iPad/tablet may use wider spacing, but the same control semantics apply. On iPad English layouts, the bottom-row cluster around space is `emoji key | space | microphone`; this mirrors the emoji-search English keyboard state and keeps emoji entry close to the typing area.

### Normal emoji panel state

When the user taps the emoji launcher from the English keyboard:

- The regular key grid is hidden.
- The emoji panel appears in the keyboard area with a transparent/platform keyboard background, matching the other LimeIME keyboard surfaces.
- The search field is fixed at the top.
- The emoji grid is horizontally scrollable across category pages. It is one continuous scroll surface with category anchors, not separate tab pages.
- The bottom strip is fixed and contains: `ABC`, Recent, Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags, Backspace.
- `ABC` returns to the English keyboard.
- Backspace deletes the previous document character and keeps the emoji panel open.
- Tapping emoji commits the emoji and keeps the emoji panel open.

### Category bookmark strip

The bottom category row is a bookmark row, not a real tab bar:

- Tapping a category icon scrolls/jumps the emoji grid to that category anchor.
- Horizontal swiping the grid updates the highlighted category icon to match the visible page.
- Icons must be visually consistent. Preferred style is iOS-like black-and-white line icons. A mix of colored emoji icons and monochrome icons is not acceptable.
- Icon order is fixed: recent clock, smiley, person/body, animal, apple/food, car/travel, ball/activity, lightbulb/objects, heart/symbols, flag.
- Recent clock must be the same apparent size as the other category icons.
- Flag must be visible immediately before backspace.
- Backspace must remain visible at the far right.

### Emoji search-active state

When the user taps the emoji panel search field:

- The full emoji grid collapses into a horizontal emoji result strip.
- A regular English keyboard appears below the result strip for query entry.
- There must be no large gap between the emoji result strip and the English keyboard.
- Typing is through the soft English keyboard.
- The English keyboard's emoji key switches back to the full emoji panel (no English keyboard visible).
- Keyboard backspace edits the search query while the search field is active.
- Return/done dismisses search focus first; if the query is empty, the full emoji panel returns.

Current Android behavior after the latest fixes is the reference for normal panel scrolling, recent behavior, category highlight, and search-to-panel switching. iOS must be brought to the same behavior where it differs.

## Data prerequisite — see [EMOJI_DB_V2.md](EMOJI_DB_V2.md)

This UI plan **depends on the data rebuild shipping first or in the same release**. The data side — Emoji 17.0 sources, schema with category + bilingual names, FTS5 index, legacy `en`/`tw` views, build script `.claude/scripts/build_emoji_db.py`, broader candidate-bar matching, and platform upgrade paths — is fully specified in [docs/EMOJI_DB_V2.md](EMOJI_DB_V2.md).

What this UI plan consumes from the rebuilt DB:

- `loadEmojiCategoryPages()` or `loadEmojiByGroup(groupName:)` for the categorized panel grid, with each DB-backed category ordered by `sort_order ASC`.
- `searchEmoji(_:, locale:)` (FTS5-backed) for the panel search field.
- The new `findEmojiForCandidate(_:, locale:, limit:)` / Android equivalent is also rewired in v1. Those data/search changes live in `LimeDB.swift`, `LimeDB.java`, and `SearchServer` code and are detailed in [EMOJI_DB_V2.md](EMOJI_DB_V2.md). Listed here only because the UI work consumes the same APIs.
- `recordEmojiUsage(_:)` for every emoji commit from the panel or candidate bar, so the Recent category stays synchronized across keyboard modes.

What's deliberately out of scope here: Emoji 17.0 build script, FTS5 schema details, Android cache wipe, GRDB FTS5 build settings — all in the DB plan.

## iOS implementation

| Concern | File | Notes |
|---|---|---|
| Key code enum | `LimeIME-iOS/Shared/Models/KeyLayout.swift` (L8-L26) | Add `case emojiPanel = -201`, `case emojiABC = -202`, `case emojiCategoryJump0..9 = -203..-212` to `LimeKeyCode` |
| iPhone English layout | `LimeIME-iOS/Shared/Models/KeyLayout.swift:158-198` (`static let english`) and `LimeIME-iOS/LimeKeyboard/Layouts/lime_abc.json` (+ `lime_abc_shift.json` to mirror) | (a) Append `中` (`switchToIM = -10`, label "中文", widthPercent 10) as the 10th key on the home/asdf row (currently `k l m n o p q r s` at 90% — the empty 10% slot at the right end). (b) Replace the bottom-row `中文` slot with the emoji launcher (`emojiPanel = -201`, label "😀", widthPercent 10, `isModifier: true`). All other bottom-row keys keep their current widths; total stays 100%. |
| iPad English layout | `LimeIME-iOS/LimeKeyboard/Layouts/lime_abc_ipad.json`, `lime_english_ipad.json`, `lime_english_number_ipad.json` (+ shift variants) | Trim `space` width and place the emoji launcher immediately left of `space`; place the microphone key immediately right of `space`. The surrounding modifier keys keep their existing widths so each bottom row still totals 100%. |
| Key dispatch | `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift:1027` (`onKey`) | `case LimeKeyCode.emojiPanel.rawValue: showEmojiPanel()`; `case .emojiABC.rawValue: hideEmojiPanel()`; category jump cases scroll the grid to the matching category anchor; backspace reuses existing `handleBackspace` |
| Panel container (new) | `LimeIME-iOS/LimeKeyboard/EmojiPanelView.swift` | Normal mode stack: search field (top) → horizontally scrollable/paged `UICollectionView` emoji grid → icon bookmark footer (`UIStackView` with ABC + category icons + backspace). Search-active mode stack: search field (top) → horizontal emoji result strip → English keyboard rows. |
| Panel mount/dismiss | `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (mirror `expandedCandidatesPanel` ~L380) | When mounting: hide the keys-area `UIStackView`, add `EmojiPanelView` in the same slot so candidate bar stays anchored above. Restore on dismiss. |
| Emoji loader / search APIs | `LimeIME-iOS/Shared/Database/LimeDB.swift` | `loadAllEmoji()`, `searchEmoji(_:locale:)`, `findEmojiForCandidate(_:locale:limit:)` — all FTS5-backed. Spec lives in [EMOJI_DB_V2.md](EMOJI_DB_V2.md); this UI plan only consumes them. |
| Preference UI | `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift:15` | `@AppStorage("enable_emoji_button") enableEmojiButton: Bool = true` toggle next to existing `enable_emoji` |
| Preference read | `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift:640` | Read `enable_emoji_button` alongside `enable_emoji` |
| Conditional render | `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` (mirror `setGlobeKeyVisible` ~L380) | Hide the emoji-launcher key when toggle is off |
| Encoding | All edited/new Swift files | UTF-8 with BOM (Chinese strings in Settings labels) |

## Android implementation

Android implements the same user-facing panel and shared key codes with native Android views.

| Concern | File | Notes |
|---|---|---|
| Key constants | `LimeStudio/app/src/main/java/net/toload/main/hd/LIME.java` or existing key-code constants owner | Add/reserve `-201` emoji panel, `-202` ABC, `-203..-212` category jump buttons; reuse existing delete/backspace code. |
| English layout | Android English keyboard XML/layout source (current English keyboard definition) | Add `😀` launcher to the English bottom row. Keep the same visual role as iOS: modifier-style key, near the space/language area, no composition. |
| Key dispatch | `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java` | Dispatch `-201` to show the emoji panel; `-202` to hide it and restore English keyboard; category jump codes scroll the emoji grid to anchors; backspace delegates to existing delete handling. |
| Panel view | Existing Android keyboard view layer / new `EmojiPanelView` equivalent | Normal mode stack: search field (top) → horizontally scrollable/paged emoji grid → icon bookmark footer (`ABC + category icons + backspace`). Search-active mode stack: search field (top) → horizontal emoji result strip → English keyboard rows. |
| Data/search APIs | `LimeStudio/app/src/main/java/net/toload/main/hd/limedb/LimeDB.java`, `SearchServer.java` | Use the same Emoji DB V2 APIs and FTS5 behavior as candidate injection. |
| Preference UI/read | Android preferences screen + `LIMEService.java` | Add/read `enable_emoji_button` with default `true`, same key as iOS. |
| Assets/icons | Android drawable/vector resources if needed | Prefer simple monochrome vector icons matching iOS category bookmark semantics. Text glyph fallback is acceptable when icons are not available. |

## Android implementation status

Android now implements the v1 emoji panel contract using the same DB-backed category API shape as iOS.

### Android category data

- Normal category browsing is loaded from DB-backed `emoji_data` through `SearchServer.loadEmojiCategoryPages()`.
- `LimeDB.loadEmojiCategoryPages()` returns catalog groups in fixed DB order and `sort_order ASC`.
- Hardcoded emoji arrays remain only as fallback seed data.
- People & Body is now a first-class category between Smileys and Animals.
- Rendered category counts are no longer limited to the old hardcoded subset when `emoji_data` is available.

### Android category expansion

- Large categories are split across multiple physical pages.
- The bookmark strip remains semantic: one icon per category, not one icon per physical page.
- Swiping moves through all pages in a category before entering the next category.
- Category highlight maps the current physical page back to its owning category.

### Android gap and scroll behavior

- Remove extra visual gaps between physical pages inside the same category.
- Keep normal category-boundary spacing only where it improves readability.
- Verify horizontal scrolling starts correctly from sparse Recent blank areas.

### Android prewarm/cache

- Category-page prewarm is owned by `SearchServer`, not `LIMEService`.
- Prewarm starts after SearchServer/DB initialization.
- Category pages are cached in memory for the IME process lifetime.
- Cache is invalidated when emoji usage is recorded or SearchServer cache reset is requested.

### Android recent/search/usage parity

- Recent, category, search, and panel usage operations route through `SearchServer`.
- `recordEmojiUsage()` is called for emoji committed from the full panel and emoji search result strip; inline candidate-bar emoji usage already routes through the shared emoji APIs where committed.
- Recent is newest-first and duplicate-collapsed through the `emoji_user` table, with fallback seed data when empty.

### Android UI polish

- Bookmark strip order matches iOS: Recent, Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags.
- Category icons are consistent monochrome drawn icons.
- `ABC` restores the regular English keyboard without changing the candidate/microphone area into the keyboard-hide/up icon.
- No space key is visible while the normal emoji panel is active.
- Search-active mode remains: search field, horizontal results strip, English keyboard.

### Android verification

- Test on Android phone and tablet/emulator.
- Verify DB-backed category browsing exposes the full available emoji set.
- Verify smooth paging through large categories.
- Verify no page gap inside a category.
- Verify first emoji-panel open after prewarm is complete.
- Verify fallback behavior when DB category loading fails.

## Approach (cross-platform v1)

### 1. Key definition & layout edits

A new key code `-201` (`emojiPanel`) is added to the **English layout** on iOS and Android. Codes `-202..-212` are reserved for the panel's internal chrome (ABC, icon category jump/bookmark buttons).

- **iPhone**: no `space`-trim. Move `中文` (`-10`) up to the home row's empty 10% slot (after `s`). Place the emoji launcher (10% width) at the bottom-row position vacated by `中文`.
- **iPad**: trim `space`. Place the emoji launcher immediately left of `space`, and microphone immediately right of `space`.
- **Android phone/tablet**: add the emoji launcher to the English bottom row using the platform's existing key sizing conventions. Prefer an existing language/modifier slot; if none is available, trim `space` by one key width and place `😀` next to the language/space cluster.

### 2. Dispatch

A platform-native `showEmojiPanel()` / `hideEmojiPanel()` pair:

- `showEmojiPanel()`: hide the keys-area view, mount the emoji panel view in its slot. Trigger an async load of the emoji dataset on first invocation (search dispatch queue / Android background worker); cache it so subsequent opens are instant.
- `hideEmojiPanel()`: tear down the panel and restore the keys-area view. Triggered by the ABC button on the bookmark strip.
- Both flows preserve the candidate bar (anchored above) and the current composing state.

### 3. Panel layout

```text
┌─────────────────────────────────────────────────┐
│ candidate bar (unchanged, stays anchored above) │
├─────────────────────────────────────────────────┤
│ 🔍 [ Search Emoji                             ] │   ← search field
├─────────────────────────────────────────────────┤
│                                                 │
│   🙌 🎉 ❤️ 😊 💕 😳 ☺️ 😀      ← iOS-style emoji │
│   🥳 😒 😍 😘 😔 👍 😀 ...        grid            │
│   👏 🕰️ 👌 😭 ☺️ ✌️ 😀 ...                      │
│                                                 │
├─────────────────────────────────────────────────┤
│ [ABC] [🕘][😀][人][🐶][🍎][⚽][🚗][💡][♡][⚑] [⌫] │   ← jump bookmarks
└─────────────────────────────────────────────────┘
```

- **Search field**: fixed at the top of the emoji panel, matching the iOS Emoji keyboard placement. Typing runs the FTS5 pipeline (see "Keyword matching") and updates the grid live. Empty query → restore the full categorized view.
- **Grid**: horizontally scrollable/paged like the iOS Emoji keyboard, not independent per-category tab pages. Categories are laid out in one continuous collection view with category anchors (Recent, Smileys & Emotion, People & Body, Animals & Nature, Food & Drink, Travel & Places, Activities, Objects, Symbols, Flags). Swiping can move across category boundaries naturally.
- **DB-backed category contents**: normal category pages must be loaded from `emoji_data`, not hardcoded arrays. For each category, query by `group_name` and order by `sort_order ASC`. The hardcoded arrays may only be used as an offline/failure fallback when the DB is unavailable or a group returns empty.
- **Category sorting**: keep normal category pages in catalog order (`sort_order ASC`). Do not reorder category pages by user usage; usage belongs in Recent and in search/candidate ranking. Stable category ordering preserves browseability and muscle memory.
- **Recent category/page**: first page and first bookmark after `ABC`. It is not a static category tab. It reads `emoji_user` and shows recently committed emoji newest-first, with duplicates collapsed to one glyph. Emoji committed from the panel, search result strip, or inline candidate bar all call `recordEmojiUsage(_:)` / Android equivalent and update this page. If there is no usage history yet, show the same default popular seed used by Android's current implementation. Current Android recent behavior is the expected behavior; current iOS behavior is not good enough until it matches this contract.
- **Sparse Recent page hit area**: iOS Recent may contain only a few real emoji. The blank portion must still scroll exactly like a full category page. In the current iOS implementation, Recent fills unused visible cell slots with invisible interactive filler cells: same cell size and gesture path as real emoji cells, `tag = -1` so taps do nothing, near-transparent real glyph/background so UIKit hit-testing treats them like real cells. Do not replace this with inert empty labels, clear-only views, or a separate transparent page holder unless visual verification proves dragging from the blank Recent area still scrolls from Recent to Smileys.
- **Category bookmark strip** (always visible): leftmost = ABC (closes the panel and returns to the regular English keyboard), then icon-only category bookmarks (tap → scroll/jump the grid to that category's anchor), rightmost = backspace (`-5`, reuses existing backspace handler).
- **Category icons**: these are jump bookmarks, not true tabs and not separate views. Use iOS-like simple monochrome icon buttons where possible. Suggested mapping: `clock`/recent, `face.smiling`, person/body, animal/paw, `apple.logo` or food glyph, car/travel, ball/activity, lightbulb/objects, heart/symbols, flag/flags. If an SF Symbol is unavailable in the extension target, fall back to a text glyph with the same visual role.
- **No space key** is shown anywhere while the panel is mounted — matches the iOS system Emoji keyboard.
- Tapping an emoji cell commits the glyph to the active editor and keeps the panel open (sticky) so users can pick multiple emoji.
- **Adaptive grid sizing**: do not hard-code one final emoji cell size for all devices. Size from the actual emoji viewport. On iPhone, use 3-4 visible rows, 7-10 columns, and bounded phone cell/font sizes. On iPad, use 4-5 visible rows, 8-10 columns, and bounded tablet cell/font sizes.
- **Current iOS sizing formula**: choose the visible row count from viewport height (`4` preferred on iPhone, `5` preferred on iPad, dropping one row when the viewport is too short). Compute `cellHeight = clamp(floor(viewportHeight / rows), platformMin, platformMax)`. Phone bounds are `46...54`; iPad bounds are `48...72`. Compute glyph size from the cell: phone `cellHeight * 0.90` capped to `30...36`, iPad `cellHeight * 0.78` capped to `40...56`. Compute columns from width: phone `7...10`; iPad `8...10` using a target cell width of `max(cellHeight * 1.95, 116)`.
- **Sparse category sizing**: category pages are allowed to size cells from the number of rows they actually need, not only the global 5-row iPad grid. For example, a 4-row Animals page can use taller cells to avoid a large unused lower band. Dense pages still use the global row count.
- **Recent filler exception**: Recent keeps invisible interactive filler cells so blank recent-page areas remain horizontally draggable; do not remove this to improve sparse-page sizing.

### 3a. Search-active layout

When the user taps the search field or starts typing in it, the panel enters an iOS-style search-active state:

```text
┌─────────────────────────────────────────────────┐
│ 🔍 [ cr                                      ⓧ ] │   ← search field
├─────────────────────────────────────────────────┤
│ 🦀 ♋ 🐚 🤣 😆 😹 😂 🍙 ...                    │   ← horizontal results
├─────────────────────────────────────────────────┤
│ [q][w][e][r][t][y][u][i][o][p]                 │
│   [a][s][d][f][g][h][j][k][l]                  │   ← English keyboard
│ [⇧] [z][x][c][v][b][n][m] [⌫]                  │
│ [123] [😀] [space/search] [return/done]         │
└─────────────────────────────────────────────────┘
```

- **English keyboard appears for query entry**: the emoji grid/bookmark strip is replaced by the regular English key rows while the search field is first responder.
- **Results become a candidate strip**: FTS5 search results render as a horizontally scrollable emoji candidate row directly above the English keyboard. Tapping a result commits that emoji and keeps the search active.
- **Search clear**: the clear button in the search field empties the query; an empty query exits search-active mode and restores the full emoji grid + bookmark strip.
- **ABC / return behavior**: ABC still returns to the regular English keyboard outside the emoji panel. The English keyboard's return/done key should dismiss search focus first; if the query is empty, it returns to normal emoji grid mode.
- **Backspace behavior**: keyboard backspace edits the search query while the search field has focus. The panel-level bottom-right backspace is only visible in normal emoji-grid mode.

### 4. Data source

Consume the FTS5-backed APIs added in [EMOJI_DB_V2.md](EMOJI_DB_V2.md):

- `loadEmojiCategoryPages()` for the categorized grid, ordered by `sort_order ASC` within each `group_name`. This is iOS-first and Android must add the same `SearchServer` API name/contract.
- `loadEmojiByGroup(groupName:)` may exist as a lower-level helper, but the panel should consume `loadEmojiCategoryPages()` so both platforms share the same category-page contract.
- `searchEmoji(query, locale)` for the search field — debounced ≥150 ms, run on the search dispatch queue.
- `findEmojiForCandidate(candidate, locale, limit)` for inline emoji suggestions in the Chinese IM candidate bar and English keyboard candidate path.
- `recordEmojiUsage(value)` on every emoji commit, regardless of whether the emoji came from the full panel grid, search result strip, or inline candidate bar.

Keyword thresholds are intentionally different by entry point:

- **Emoji keyboard search box**: explicit search, so English prefix matching starts from one character (`c*`, `cr*`, `cry*`). Chinese/CJK also starts from one character (`國*`, `笑*`).
- **Chinese IM keyboard / English keyboard inline suggestions**: passive suggestion, so English prefix matching starts from two characters (`cr*`, `cry*`) and bare `c` returns no emoji. Chinese/CJK candidates still match from one character.
- **Chinese IM candidate broadening**: for multi-character Chinese candidates, also match the first Chinese character. Example: candidate `國旗` queries both `國旗*` and `國*`; candidate `日本` queries both `日本*` and `日*`.

### 5. Preference toggle

- **iOS**: SwiftUI `@AppStorage("enable_emoji_button")` toggle in `PreferencesTabView.swift`, with bilingual label (English: "Show emoji panel button" / Traditional Chinese: "顯示 Emoji 鍵盤按鈕"). `KeyboardViewController` reads the preference at keyboard construction time and passes a flag to the layout-build step that hides the launcher key when `false`.
- **Android**: add the same `enable_emoji_button` preference key to the Android preferences UI/storage. `LIMEService` reads it during keyboard construction and hides the launcher key when `false`.
- Default is `true` on both platforms.

### 6. Encoding

Any Swift source file added or edited must be saved as **UTF-8 with BOM** (Chinese strings appear in the Settings toggle label and panel placeholder text).

## Risks / open questions

- **iPhone home-row reflow**: moving `中文` from the bottom row to the home row changes a long-standing layout. Verify muscle memory isn't disrupted; the bottom-row `中文` slot is a natural visual home for the emoji modifier key (same width, same row, modifier-style chrome).
- **iPad / Android tablet balance**: trimming `space` reduces typing area slightly. Verify the trimmed width still feels comfortable on portrait and landscape tablet sizes; if too narrow, fall back to replacing a side modifier slot instead.
- **Panel height vs keyboard height**: the emoji panel (search + grid + bookmark strip) must match the keys-area height so the keyboard footprint doesn't jump. Verify on landscape iPad split keyboard and Android tablet landscape.
- **Skin tones / fuzzy search**: explicit non-goals for v1; follow-ups.
- Data-side risks (build reproducibility, FTS5 in GRDB, font fallback, CN drop) live in [EMOJI_DB_V2.md](EMOJI_DB_V2.md).

## Verification (iOS + Android v1)

End-to-end manual test on iPhone (WJIP17 per `reference_ios_devices.md`), iPad, Android phone, and Android tablet/emulator:

1. With `enable_emoji_button = true`, open LimeIME and switch to the **English** layout in a text field.
   - **iPhone**: confirm the bottom row's `中文` slot now shows the 😀 emoji-launcher key, and `中文` has moved up to the right end of the home row (after `s`). All other key positions unchanged.
   - **iPad**: confirm `space` is slightly narrower, the 😀 emoji-launcher is immediately left of `space`, and the microphone key is immediately right of `space`.
   - **Android phone/tablet**: confirm the English layout shows the 😀 emoji-launcher in the agreed bottom-row modifier slot; non-English layouts are unchanged unless explicitly opted in.
   - Phonetic / symbol-page layouts are unchanged (no emoji key).
2. Tap the emoji key → the regular keys area is replaced by the emoji panel; candidate bar still visible above; **no space key is shown anywhere on screen**.
3. The panel shows: search field at the top, horizontally scrollable/paged iOS-style emoji grid in the middle, icon-only bookmark strip at the bottom (`ABC | category icons | backspace`). Search results use **English by default; Traditional Chinese (TW) labels appear** for users with a Chinese system locale (panel uses `name_tw` when locale is `zh-Hant`, otherwise `name_en`).
4. Swipe horizontally through the emoji grid → scrolling crosses category boundaries in one continuous surface. Tap each category icon → the grid jumps to the corresponding category anchor.
5. Tap the search field → the emoji grid/bookmark strip switches to search-active mode: English keyboard appears below; horizontal emoji result strip appears above it.
6. Smoke-test that the panel search field wires up to the FTS5 backend: type `flag` → country flags, `國旗` → country flags, empty query → categorized view. In the emoji search box, verify English starts at one character (`c`, `cr`, `cry` all search as prefixes). (Full search-quality matrix is in [EMOJI_DB_V2.md](EMOJI_DB_V2.md) verification §5.)
7. While the search field contains text, tap an emoji result in the horizontal strip → glyph is committed and search mode stays active; tap keyboard backspace → query text is edited, not the document.
8. Clear the search field → full emoji grid and bottom bookmark strip return.
9. On iOS, tap the new home-row `中文` key → keyboard switches back to phonetic IM (same behavior as before, just from a different position). On Android, verify the existing language-switch path still works from the English layout.
10. Tap several emoji in succession → each glyph is committed to the document; the panel stays open (sticky behavior).
11. Tap the Recent bookmark → the just-committed emoji appear newest-first, with no duplicates. If no emoji has ever been committed, the page shows Android's current default popular seed. Android is the reference behavior; iOS must match it.
    - On iOS, verify sparse Recent explicitly: with only a few recent emoji visible, drag from the blank area below/right of the real emoji. It must scroll forward to the Smileys page, not only scroll when the drag starts on a real emoji glyph.
12. In Chinese IM and English inline suggestion paths, verify English does not suggest emoji for bare `c`, but does for `cr` / `cry`; verify Chinese candidates also match by first Chinese character (`國旗` → `國旗*` + `國*`).
13. Tap the bottom-right backspace key on the bookmark strip → last character of the document is deleted; panel stays open.
14. Tap the bottom-left ABC key → panel dismisses; the regular English keys return; cursor position preserved.
15. Rotate to landscape (iPhone + iPad + Android tablet) → search field, horizontal grid/search-result strip, English search keyboard, and icon bookmark strip lay out without clipping; row/column count adjusts from the actual viewport. On iPad, sparse category pages should enlarge cells enough to avoid an obvious unused lower band while dense pages still fit.
16. In Settings, toggle `enable_emoji_button` off → reopen the keyboard → emoji-launcher key gone. On iOS, the English layout still has `中文` on the home row (the home-row reflow is permanent in v1, not gated by the toggle). Toggle back on → emoji key reappears in the bottom row.
17. Confirm preference `enable_emoji_button` persists across keyboard restarts.
18. Issue #29 end-to-end test (candidate-bar broadening + cache wipe + Android upgrade) lives in [EMOJI_DB_V2.md](EMOJI_DB_V2.md) verification §6-13.

## Decisions — resolved (UI side)

1. **Launcher key placement** ✅
   - iPhone: move `中文` from bottom row to the home row's empty 10% slot (after `s`); emoji launcher takes the vacated bottom-row position.
   - iPad: trim `space`; emoji launcher immediately left of `space`, microphone immediately right of `space`.
   - Android: use the English bottom-row modifier/language area; prefer replacing an existing low-risk modifier slot, otherwise trim `space` by one key width.
2. **Default for existing users** ✅ — `enable_emoji_button = true` (always visible).
3. **Recent category behavior** ✅ — Recent is the first emoji page/bookmark, backed by `emoji_user` newest-first usage. Android current behavior is the reference; iOS must be fixed to match.
4. **Keyword threshold split** ✅ — inline English emoji suggestions start at two characters; emoji search-box English search starts at one character; Chinese IM candidate matching also queries the first Chinese character.

(Data-side decisions — locales, target version, FTS5, legacy views, upgrade paths — are in [EMOJI_DB_V2.md](EMOJI_DB_V2.md).)

## Implementation order

This UI plan and [EMOJI_DB_V2.md](EMOJI_DB_V2.md) ship in the same cross-platform release (single PR or two-PR sequence with DB landing first):

1. **DB plan first** — build script, new schema, FTS5 APIs, candidate-bar rewiring, Android cache-wipe hook.
2. **Shared UI contract** — key codes, preference key, panel states, category bookmark behavior, recent behavior, search-active behavior.
3. **iOS UI** — iPhone home-row reflow, iPad space-trim, emoji-launcher key, panel view, preference toggle.
4. **Android UI** — English layout launcher, panel view, preference toggle, Android FTS/search wiring.

## Cleanup note

This plan supersedes `docs/IOS_EMOJI_BUTTON_PLAN.md`. After exiting plan mode, that file should be deleted (I cannot delete files in plan mode).
