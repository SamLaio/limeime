# Emoji Query Gap Analysis

## Purpose

This document tracks the remaining gap between the emoji query behavior specified in [EMOJI_DB_V2.md](EMOJI_DB_V2.md), the current Android implementation, and the current iOS implementation.

The important distinction is that emoji search has two different product surfaces:

- **Emoji panel search field**: explicit user search, so English prefix search starts from one character (`c*`, `cr*`, `cry*`).
- **Inline candidate-bar emoji injection**: passive suggestion in Chinese IM or English keyboard candidate paths, so English starts from two characters (`c` returns no inline emoji, `cr*` and `cry*` match).

Chinese candidate lookup keeps one-character CJK candidates and, for multi-character Chinese candidates, should query both the full candidate and the first Chinese character (`國旗* OR 國*`, `日本* OR 日*`).

## Current Gap

| Area | Expected behavior | iOS current state | Android current state | Gap |
|---|---|---|---|---|
| Emoji panel search API | DB-backed `searchEmoji(query, locale)` keeps one-character English tokens (`c*`) | No dedicated `searchEmoji` API found; panel search calls `findEmojiForCandidate` | No DB-backed panel `searchEmoji` API; panel search uses hardcoded UI keyword matching | Add a real panel-search API on both platforms or the implementation will drift from the DB spec. |
| Panel search one-character English | Searching `c` in the emoji keyboard search box returns emoji tagged by `c*` | Reuses candidate lookup, whose query builder drops single ASCII letters | Current hardcoded matcher can match some `c` cases, but not through the DB contract | Implement panel query builder separately from candidate query builder. |
| Inline English candidate threshold | `c` returns no inline emoji; `cr` and `cry` match | Implemented by dropping single ASCII alphabetic tokens | Implemented by dropping single ASCII alphabetic tokens | Behavior matches, but tests should keep guarding it. |
| Multi-character Chinese candidate expansion | `國旗` builds `國旗* OR 國*`; `日本` builds `日本* OR 日*` | Current builder returns only the full token prefix | Current builder returns only the full token prefix | Add first-CJK-character broadening on both platforms. |
| iOS candidate-bar caller | `SearchServer.injectEmoji` should call `findEmojiForCandidate` | Calls `emojiConvert`, which wraps `findEmojiForCandidate` | N/A | Behavior is mostly correct, but docs and code are not literally aligned. Either call the new API directly or document the wrapper. |
| Android panel recents | Recent tab should be driven by `emoji_user`, ordered by `last_used DESC` | N/A | `recordEmojiUsage` exists, but emoji panel commit does not call it; recent category is currently static | Wire Android emoji commit to `recordEmojiUsage` and load recent category from DB-backed recents. |
| iOS panel recents | Recent tab should reflect actual committed emoji, newest first | `recordEmojiUsage` is called on emoji commit; visual behavior still needs verification/fix | N/A | Verify and fix iOS recent tab behavior so it matches Android's intended behavior. |
| Test coverage | Tests cover candidate threshold, panel one-character search, CJK first-char broadening, and recents writeback | Candidate threshold is covered; panel search and first-char broadening are missing | Candidate threshold and user-record preservation are covered; panel search and first-char broadening are missing | Add platform tests before or with implementation changes. |

## TODO To Close The Gap

### 1. Split Query Builders

- Add a shared-concept query builder on both platforms for panel search:
  - Keep sanitized one-character ASCII alphabetic tokens.
  - Build `c*`, `cr*`, `cry*`.
  - Keep one-character CJK tokens such as `國*` and `笑*`.
- Add a separate candidate query builder on both platforms:
  - Drop one-character ASCII alphabetic tokens.
  - Keep one-character CJK tokens.
  - For multi-character Chinese candidates, include the full candidate and first Chinese character with `OR`.
  - Examples:
    - `c` -> empty
    - `cr` -> `cr*`
    - `cry` -> `cry*`
    - `國` -> `國*`
    - `國旗` -> `國旗* OR 國*`
    - `日本` -> `日本* OR 日*`

### 2. Add DB-Backed Panel Search API

- iOS: add `LimeDB.searchEmoji(_ query: String, locale: EmojiLocale, limit: Int = 200) -> [Mapping]`.
- Android: add `LimeDB.searchEmoji(String query, EmojiLocale locale, int limit)`.
- Both APIs should:
  - Use the panel-search query builder.
  - Query `emoji_fts`.
  - Order by `(u.last_used IS NULL), u.last_used DESC, d.sort_order ASC`.
  - Return empty results when sanitization produces no query.

### 3. Rewire Emoji Panel Search

- iOS: change emoji panel search to call `searchEmoji` instead of `findEmojiForCandidate`.
- Android: replace or supplement the current hardcoded `findEmojiSearchResults` path with the DB-backed `searchEmoji` API.
- Keep fallback emoji lists only as UI fallback when DB data is unavailable, not as the primary search implementation.

### 4. Fix Recents

- Android:
  - Call `recordEmojiUsage` when an emoji is committed from the emoji panel.
  - Load the recent category from `emoji_user` joined with `emoji_data`, ordered by newest first.
  - If no recent emoji exist, show the default frequently-used/fallback first category.
- iOS:
  - Verify the recent tab reads the same recents ordering after `recordEmojiUsage`.
  - Fix any stale/static recent category behavior so it matches Android.

### 5. Add Tests

- iOS unit tests:
  - Panel query builder keeps `c` as `c*`.
  - Candidate query builder drops `c`.
  - Candidate query builder builds `國旗* OR 國*` and `日本* OR 日*`.
  - `searchEmoji("c", .en)` returns matching emoji from seeded DB rows.
  - `findEmojiForCandidate("c", .en)` returns empty.
  - Emoji usage writeback changes recents order.
- Android instrumentation/unit tests:
  - Same query-builder cases as iOS.
  - `searchEmoji("c", EN)` returns matching emoji from seeded rows.
  - `findEmojiForCandidate("c", EN)` returns empty.
  - `findEmojiForCandidate("日本", TW)` can find rows through `日*` when appropriate.
  - `recordEmojiUsage` affects recent ordering and survives emoji-data refresh.

### 6. Visual Verify Android

Use the `android-visual-verify` skill before closing the Android side.

Required Android visual states:

- Reinstall debug APK if needed.
- Activate LIME and switch the active IME to LIME.
- Install `大易` and `注音` from cloud sources if instrumentation tests cleared `lime.db`.
- Use a regular text input, not a URL field.
- Verify Chinese keyboard candidate bar still appears.
- Verify English keyboard has `中` at the leftmost ASDF-row key and emoji key in the old bottom-row `中` position.
- Verify emoji panel opens from the English emoji key.
- Verify emoji panel scrolls horizontally across categories.
- Verify category highlight follows the visible category.
- Verify category row order: recent, smiley, animal, food, sports, travel, objects, symbols/heart, flag, backspace.
- Verify category icons are visually consistent, preferably iOS-like monochrome.
- Verify emoji panel search:
  - Tap search field.
  - English keyboard appears under emoji search candidates with no large gap.
  - Tap soft keys only.
  - Search `c` returns emoji results.
  - Search `cr` and `cry` return expected cry/laugh related emoji.
  - Tap the emoji key while in emoji-search English keyboard; it returns to the full emoji panel.
- Verify recent tab:
  - Commit an emoji.
  - Reopen emoji panel.
  - Recent tab shows the committed emoji first.

Capture screenshots into `.Codex/txt/` for the final verification record.

### 7. Visual Verify iOS

Use the `ios-visual-verify` skill before closing the iOS side.

Required iOS visual states:

- Build and launch on Xcode iOS Simulator.
- Ensure LimeIME is active through LimeSettings or the iOS globe key.
- Enable full access when DB-backed behavior is under test.
- Install `大易` and `注音` through LimeSettings if the simulator state was reset.
- Use a regular text input, not a URL field.
- Verify Chinese keyboard candidate bar still appears.
- Verify English keyboard has `中` at the leftmost ASDF-row key and emoji key in the old bottom-row `中` position.
- Verify emoji panel opens from the English emoji key.
- Verify emoji panel scrolls horizontally across categories.
- Verify category highlight follows the visible category.
- Verify category row order and icon style match Android.
- Verify emoji panel search:
  - Tap search field.
  - English keyboard appears under emoji search candidates with no large gap.
  - Tap soft keys only.
  - Search `c` returns emoji results.
  - Search `cr` and `cry` return expected cry/laugh related emoji.
  - Tap the emoji key while in emoji-search English keyboard; it returns to the full emoji panel.
- Verify recent tab:
  - Commit an emoji.
  - Reopen emoji panel.
  - Recent tab shows the committed emoji first.

Capture screenshots into `.Codex/txt/` for the final verification record.

## Done Criteria

- Android and iOS both expose DB-backed panel search and candidate lookup with different English minimum-length rules.
- `c` matches in emoji panel search but not in passive candidate-bar emoji injection.
- `cr` and `cry` match in both surfaces.
- One-character Chinese candidates match in candidate-bar injection.
- Multi-character Chinese candidates broaden to first Chinese character.
- Android and iOS recents are backed by `emoji_user`.
- Android and iOS visual behavior matches for Chinese keyboard, English keyboard, emoji panel, emoji search, category bookmarks, backspace, and recent tab.
- Automated tests cover the query builders, DB search APIs, candidate behavior, and recents ordering.
