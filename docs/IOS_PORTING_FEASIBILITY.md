# iOS Porting Feasibility Study

This document evaluates the feasibility of porting LimeIME to iOS (iPhone and iPad). The analysis is organized around LimeIME's four major architectural components, with focus on cross-platform SQLite database portability, keyboard layout consistency, and technology/language selection.

---

## Executive Summary

Porting LimeIME to iOS is **feasible** but requires significant effort. The architecture divides cleanly into four components with different portability characteristics:

| Component | Role | Portability |
|-----------|------|-------------|
| **DBServer** | Database maintenance, import/export, backup/restore | **High** — standard SQLite, file operations only |
| **SearchServer** | IM mapping search, scoring, learning, caching | **High** — pure query logic, no platform dependency |
| **IMService** | Bridge between SearchServer and OS input services | **Low** — full rewrite required (Android `InputMethodService` → iOS `UIInputViewController`) |
| **Virtual Keyboard** | Keyboard rendering, touch handling, candidate bar | **Low** — full rewrite required (Android `Canvas` → iOS UIKit), but layouts can stay identical |

The core data — a single `lime.db` database with all IM mapping tables, scoring, and phrase learning — is **100% portable** with zero modification. The `lime.db` file produced on Android can be restored on iOS and vice versa. The effort is concentrated in IMService and Virtual Keyboard, which must be rewritten for iOS but can follow the same architecture.

**Recommended language: Swift.** Cross-platform frameworks (Flutter, React Native) cannot create iOS keyboard extensions. Kotlin Multiplatform is theoretically possible for DBServer/SearchServer but adds complexity without clear benefit given the existing Java codebase.

---

## Architecture Overview

### LimeIME Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Virtual Keyboard                          │
│  Keyboard rendering, touch/key events, candidate display     │
│  Android: LIMEKeyboardView, CandidateView, XML layouts       │
│  iOS:     UIKit custom views, JSON layouts                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ key events, candidate selection
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                       IMService                              │
│  Bridge between SearchServer and OS input method services     │
│  Android: LIMEService (InputMethodService)                   │
│  iOS:     UIInputViewController subclass                     │
│  - Composing text management                                 │
│  - Text insertion / deletion via OS API                       │
│  - Input method switching                                    │
│  - Learning flow coordination                                │
└──────────────────────────┬──────────────────────────────────┘
                           │ query, learn, config
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     SearchServer                             │
│  IM mapping search, scoring, caching, learning               │
│  - getMappingByCode(), getRelatedPhrase()                    │
│  - Score-based candidate ordering                            │
│  - Multi-level cache (mapping, emoji, blacklist)             │
│  Platform-independent: pure query logic over SQLite           │
└──────────────────────────┬──────────────────────────────────┘
                           │ SQL operations
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                       DBServer                               │
│  Database maintenance, import/export, backup/restore         │
│  - Import .db/.cin/.lime/.zip/.limedb into lime.db           │
│  - Backup/restore lime.db + settings                         │
│  - Schema migration                                          │
│  Platform-independent: standard SQLite + file I/O            │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
                    SQLite Databases
              lime.db | hanconvertv2.db | emoji.db
```

### iOS App Structure

On iOS, the four components are distributed across two targets that share data through an **App Group** shared container:

| iOS Target | Components | Role |
|------------|------------|------|
| **Container App** | DBServer (full), SearchServer (partial) | Settings UI, database import/export, backup/restore, IM management |
| **Keyboard Extension** | IMService, Virtual Keyboard, SearchServer, DBServer (read-only) | The actual keyboard, subclassing `UIInputViewController` |

Both targets access the same `lime.db` in the App Group shared container. The Container App handles heavy write operations (import, backup/restore); the Keyboard Extension primarily reads.

---

## Component 1: DBServer

### Role

DBServer manages all database file operations: importing IM tables from external sources into `lime.db`, exporting user data, backup/restore, and schema migration. It is a **file operations layer** that sits beneath SearchServer.

### Android Implementation

- **Class**: `DBServer.java` (~800 lines), singleton pattern
- **Database**: Single `lime.db` containing all IM mapping tables, `related` phrases, `im` config, `keyboard` config
- **Helper databases**: `hanconvertv2.db` (Traditional/Simplified conversion), `emoji.db` (emoji lookup) — opened separately
- **SQL layer**: `LimeDB.java` (~2,500 lines) — all parameterized SQL operations
- **Import formats**: `.db` (via `ATTACH DATABASE`), `.cin`/`.lime` (text parsing), `.zip`/`.limedb` (decompression + import)
- **Backup format**: ZIP containing `lime.db` + `lime.db-journal` + `shared_prefs.bak` (Java `ObjectOutputStream`)

### iOS Porting Strategy

**Portability: High.** DBServer's operations are standard SQLite + file I/O with no platform-specific logic.

**Database access**: Use **GRDB.swift** (recommended Swift SQLite wrapper) — provides Swift-idiomatic API, migration support, direct SQL execution, WAL mode support, and thread-safe reader/writer separation. This closely matches the existing `LimeDB` patterns.

| Option | Description | Recommendation |
|--------|-------------|----------------|
| SQLite C API | Direct `sqlite3_open()` — available on iOS | Maximum control, verbose code |
| **GRDB.swift** | Popular Swift SQLite wrapper | **Recommended** — closest to Android `SQLiteDatabase` API |
| Core Data | Apple's ORM with SQLite backend | Overkill for a well-defined schema |

**Schema**: All table schemas use standard SQL types and are **fully portable without modification**:

**IM Mapping Tables** (phonetic, cj, dayi, pinyin, wb, ez, array, hs, etc.):

| Column | Type | Purpose |
|--------|------|---------|
| `_id` | INTEGER PRIMARY KEY | Record ID |
| `code` | TEXT | Input code / key sequence |
| `word` | TEXT | Output character / word |
| `score` | INTEGER | User frequency score (learning) |
| `basescore` | INTEGER | Pre-loaded base frequency |
| `code3r` | TEXT | Code without tones (phonetic only) |

**Related Phrases Table** (`related`):

| Column | Type | Purpose |
|--------|------|---------|
| `_id` | INTEGER PRIMARY KEY | Record ID |
| `pword` | TEXT | Previous (parent) word |
| `cword` | TEXT | Next (child) word |
| `score` | INTEGER | User frequency score |
| `basescore` | INTEGER | Base frequency |

**IM Configuration** (`im`) and **Keyboard Configuration** (`keyboard`) tables: standard TEXT and BOOLEAN columns, fully portable.

**Import flow**: All import methods translate directly to iOS:

| Operation | Android | iOS Equivalent |
|-----------|---------|----------------|
| Binary DB import | `ATTACH DATABASE` + `INSERT INTO...SELECT FROM` | **Same SQL** — works on iOS SQLite |
| Text file parsing (.cin/.lime) | `importTxtTable()` — delimiter detection, line-by-line insert | Identical logic in Swift — `String` splitting, batch `INSERT` |
| ZIP decompression | zip4j library | `ZIPFoundation` (Swift Package) |
| File selection | Storage Access Framework | `UIDocumentPickerViewController` |
| Database path | `Context.getDatabasePath()` | App Group shared container path |

**Pre-built IM source files**: The 50+ `.db`/`.zip`/`.limedb`/`.cin` files in `Database/` are import sources, not runtime databases. They can be bundled in the iOS app and imported into `lime.db` using the same `ATTACH` + `INSERT` SQL flow.

### Cross-Platform Backup/Restore

This is a key design goal: a backup created on Android should be restorable on iOS, and vice versa.

**Problem**: The current backup format includes `shared_prefs.bak` using Java `ObjectOutputStream` — not readable on iOS.

**Proposed cross-platform backup format (v2):**

```
backup_v2.zip
├── lime.db              ← SQLite database (identical on both platforms)
├── settings.json        ← Preferences as JSON (replaces shared_prefs.bak)
└── manifest.json        ← Version info, platform, timestamp
```

**`settings.json` example:**
```json
{
  "format_version": 1,
  "platform": "android",
  "app_version": "6.0.0",
  "preferences": {
    "keyboard_state": "0;1;2;3",
    "keyboard_list": "phonetic",
    "candidate_suggestion": true,
    "learn_phrase": true,
    "font_size": 1.0,
    "keyboard_size": 1.0,
    "vibrate_on_keypress": true,
    "han_convert_option": 0,
    "phonetic_keyboard_type": "standard"
  }
}
```

**Implementation:**
- **Android side**: Add JSON export/import in `DBServer` alongside existing Java serialization. Continue supporting legacy `shared_prefs.bak` for backward compatibility; write both formats on backup.
- **iOS side**: Native JSON support via `Codable` / `JSONSerialization`. Read `settings.json` and map to `UserDefaults` (App Group shared).
- **Database file**: `lime.db` is a standard SQLite file — copy directly between platforms.
- **Platform-specific settings** (e.g., `physical_keyboard_type`, `hide_software_keyboard_typing_with_physical`) are stored under a platform key in the JSON and ignored by the other platform.

### Memory Considerations

iOS keyboard extensions have a ~30–60 MB memory limit. Only three databases are opened at runtime:

| Database | Size | Notes |
|----------|------|-------|
| `lime.db` (main) | Variable | Size depends on how many IMs the user has loaded |
| `hanconvertv2.db` | 274 KB | Traditional/Simplified converter |
| `emoji.db` | Small | Emoji lookup |

SQLite only loads pages on demand — actual memory usage depends on query patterns, not total file size. Use `PRAGMA cache_size` to control page cache. Open helper databases on-demand and close when not needed.

### Effort: Low–Medium

The SQL schema and import logic port directly. Main work: rewrite file I/O from Java to Swift, replace Java serialization with JSON, adapt file paths for App Group container.

---

## Component 2: SearchServer

### Role

SearchServer is the **single interface** for all IM query operations. It receives input codes from IMService, queries `lime.db` for matching candidates, applies score-based ordering, manages the learning flow (score updates, related phrase recording), and maintains multi-level caches for performance.

### Android Implementation

- **Class**: `SearchServer.java` (~1,500 lines)
- **Core operations**: `getMappingByCode()`, `getRelatedPhrase()`, `learnRelatedPhraseAndUpdateScore()`
- **Caching**: Multi-level — mapping cache, emoji cache, keyboard list cache, blacklist cache (`ConcurrentHashMap`, 1024-entry limit)
- **Learning**: Increments `score` column when user selects a candidate; records parent→child word pairs in `related` table
- **State**: Tracks current IM table name, active keyboard, composing state
- **Thread safety**: Caches use `ConcurrentHashMap`; database operations serialize through `LimeDB`

### iOS Porting Strategy

**Portability: High.** SearchServer is pure query logic over SQLite with in-memory caching. No platform-specific APIs are used in the search/learning flow.

**What ports directly:**
- All SQL queries (`SELECT ... FROM {table} WHERE code = ? ORDER BY score DESC`) — identical on iOS
- Score-based candidate ordering — pure arithmetic
- Related phrase lookup and learning — standard SQL `INSERT`/`UPDATE`
- Blacklist cache logic — Swift `Dictionary` replaces `ConcurrentHashMap`
- Multi-level caching pattern — Swift dictionary caches with same invalidation logic

**What changes:**
- `ConcurrentHashMap` → Swift `Dictionary` with `NSLock` or actor-based concurrency
- `LimeDB` SQL calls → GRDB.swift query API (or raw SQL strings — both work)
- `SystemClock.sleep()` → `Thread.sleep()` or Swift concurrency (`Task.sleep()`)

**Key design decision**: SearchServer should be a standalone Swift class with no UIKit dependencies, so it can be unit-tested independently and shared between the Container App and the Keyboard Extension.

### Effort: Medium

Port ~1,500 lines of query/caching/learning logic from Java to Swift. The logic is straightforward — no complex algorithms, just SQL queries and dictionary caches. The SQL statements themselves are identical.

---

## Component 3: IMService

### Role

IMService is the **bridge** between SearchServer and the operating system's input method framework. It receives key events from Virtual Keyboard, converts them into code sequences, queries SearchServer for candidates, manages composing text, and commits selected text to the active text field via the OS text input API.

### Android Implementation

- **Class**: `LIMEService.java` (~2,000 lines), extends `InputMethodService`
- **Text interaction**: `InputConnection` — rich API with composing spans, cursor movement, text selection, batch edits
- **Key handling**: `onKey()` from soft keyboard, `onKeyDown()`/`onKeyUp()` from physical keyboard
- **Composing flow**: Sets composing region with underline styling; replaces on candidate selection
- **Learning coordination**: Calls `SearchServer.learnRelatedPhraseAndUpdateScore()` after user selects a candidate
- **Keyboard management**: Delegates to `LIMEKeyboardSwitcher` for layout switching; manages shift/symbol/IM modes
- **Features**: Auto-commit, Chinese punctuation substitution, voice input integration, physical keyboard dual-code mapping

### iOS Porting Strategy

**Portability: Low — full rewrite required.** Android's `InputMethodService` and iOS's `UIInputViewController` are fundamentally different APIs.

**iOS `UIInputViewController` vs Android `InputMethodService`:**

| Aspect | Android `InputMethodService` | iOS `UIInputViewController` |
|--------|------------------------------|----------------------------|
| Text interaction | `InputConnection` — rich API with composing spans, selection, batch edits | `textDocumentProxy` — limited to insert, delete, move cursor, read nearby text |
| Composing text | Native composing region with underline styling | **No built-in composing region** — must simulate with inline text + manual delete-on-change |
| Candidate bar | Separate `setCandidatesView()` | Must be part of the keyboard's own `inputView` |
| Lifecycle | Long-lived service (`onCreate`/`onDestroy`) | Created and destroyed per text field focus/blur |
| Memory limit | No hard limit | **~30–60 MB** — extension terminated if exceeded |
| Network | Available with permission | Blocked unless **"Allow Full Access"** enabled |
| Clipboard | Available | Blocked unless **"Allow Full Access"** enabled |
| Background | Can run in background | **No background execution** |
| Physical keyboard | Full interception via `onKeyDown()`/`onKeyUp()` | **Not available** — iOS handles physical keyboard at system level |

**Key challenges:**

1. **Composing text simulation**: iOS has no native composing region. The IMService must track composing state internally, insert provisional text via `textDocumentProxy.insertText()`, and delete it (via `deleteBackward()`) when the composing string changes. This is the most complex part of the port.

2. **`textDocumentProxy` limitations**: Cannot read the full document — only `documentContextBeforeInput` and `documentContextAfterInput` (nearby text). Related phrase learning can still work because it only needs the immediately preceding word.

3. **Lifecycle differences**: iOS creates/destroys the extension per text field. SearchServer caches and database connections must be managed efficiently across these short lifecycles. Use the App Group shared container to persist state.

4. **No physical keyboard support**: iOS keyboard extensions are not involved when a physical keyboard is connected. This is a platform limitation — physical keyboard users must use iOS built-in Chinese input methods. The physical keyboard dual-code mapping feature from Android cannot be ported.

**What ports conceptually (same logic, different API):**
- Code sequence building from key events
- SearchServer query coordination
- Candidate selection and text commit
- Learning flow after candidate selection
- IM/English/Symbol mode switching
- Auto-commit logic
- Chinese punctuation substitution

### Apple Requirements

- **Globe key** (🌐): Must call `advanceToNextInputMode()` — mandatory for App Store approval
- **Privacy**: No data collection/transmission without "Allow Full Access" and disclosure
- **Binary size**: Keep dependencies minimal for keyboard extension

### Effort: High

Full rewrite of ~2,000 lines. The business logic (composing flow, learning coordination, mode switching) translates conceptually, but every OS interaction must be reimplemented against `UIInputViewController` and `textDocumentProxy`. Composing text simulation is the hardest part.

---

## Component 4: Virtual Keyboard

### Role

Virtual Keyboard handles keyboard rendering, touch event processing, key preview popups, long-press menus, and the candidate bar display. It receives touch input from the user and forwards key events to IMService.

### Android Implementation

- **Keyboard rendering**: `LIMEKeyboardView.java` / `LIMEKeyboardBaseView.java` — double-buffered `Canvas` drawing with text height/width caching
- **Keyboard model**: `LIMEBaseKeyboard.java` — parses XML layout definitions into key/row objects
- **Keyboard switching**: `LIMEKeyboardSwitcher.java` — maps IM codes to XML resource IDs, caches keyboard instances
- **Candidate bar**: `CandidateView.java` (~600 lines) — horizontal scrolling candidate display
- **Layout definitions**: 60+ XML files in `res/xml/` defining key positions, labels, sub-labels, popups
- **Key attributes**: `codes`, `keyLabel` (with `\n` for sub-labels), `keyWidth` (percentage: `10%p`), `keyHeight` (dip), `popupKeyboard`, `popupCharacters`, `keyEdgeFlags`, `isModifier`, `isSticky`, `isRepeatable`
- **Dimensions**: 46dip key height (portrait), 36dip (landscape); percentage-based widths

### iOS Porting Strategy

**Portability: Low — full UI rewrite required.** Android's `Canvas` API has no iOS equivalent. However, the **layout definitions** (key positions, sizes, labels) are fully portable.

**Shared JSON Layout Format**

Convert existing Android XML layouts to a cross-platform JSON format that both platforms can consume:

```json
{
  "id": "phonetic",
  "name": "注音",
  "defaultKeyWidth": 10,
  "keyHeight": 46,
  "rows": [
    {
      "edge": "top",
      "keys": [
        {
          "code": 49,
          "label": "1",
          "subLabel": "ㄅ",
          "widthPercent": 10,
          "edge": "left"
        },
        {
          "code": 50,
          "label": "2",
          "subLabel": "ㄉ",
          "widthPercent": 10
        }
      ]
    }
  ],
  "popups": {
    "46": {
      "characters": ["【", "〈", "『", "「", "】", "〉", "』", "」"]
    }
  }
}
```

A one-time conversion script generates JSON from the 60+ Android XML files. Both platforms parse the same JSON definitions to ensure visual consistency.

**iOS Rendering Approach**

| Approach | Pros | Cons |
|----------|------|------|
| **UIKit (`UIView` + `draw(_ rect:)`)** | Closest to Android `Canvas` approach, fine-grained control, proven in production keyboards | More boilerplate |
| SwiftUI | Modern, declarative, easier layout math | Less control over rendering, potential performance issues |
| Hybrid (SwiftUI layout + UIKit drawing) | Best of both worlds | Added complexity |

**Recommendation**: UIKit-based custom view for key rendering (matching Android's `Canvas` approach for performance), with SwiftUI for the container app settings UI.

**Candidate bar**: Use `UICollectionView` with horizontal scroll for candidate display — functionally equivalent to Android's `CandidateView`.

### Layout Similarity Goals

The following can be kept **identical** across platforms:

- QWERTY base layout with Chinese sub-labels (ㄅㄆㄇ for phonetic, 手田水 for cangjie, etc.)
- 4–5 row structure for all input methods
- Key width percentages (10%p → 10% of screen width)
- Popup / long-press character menus
- Keyboard switching flow: Chinese IM → English → Symbols
- Portrait and landscape variants

### Necessary Differences

| Aspect | Android | iOS |
|--------|---------|-----|
| **System switch key** | Optional globe/language key | **Mandatory globe key** (🌐) — Apple requirement |
| **Bottom row** | Flexible arrangement | Must accommodate globe key; resize adjacent keys |
| **Keyboard height** | Configurable via `keyHeight` dip | Follow iOS conventions (~216pt portrait iPhone, ~162pt landscape) |
| **Key preview** | Shows enlarged key on press | iOS convention is subtler — smaller preview or none |
| **Haptic feedback** | `Vibrator` API | `UIImpactFeedbackGenerator` |

### iPad Considerations

- Wider keyboard area — can show more keys per row or use larger keys
- Support split/floating keyboard mode
- Consider permanent number row (more screen space available)
- Use `UITraitCollection` to detect device class and load appropriate layout

### Effort: High

Full rewrite of keyboard rendering (~1,000+ lines) and candidate bar (~600 lines). The layout definitions are portable via JSON conversion, but all drawing code, touch handling, and animation must be reimplemented in UIKit.

---

## Language & Technology Choice

### Option A: Swift (Native iOS) — Recommended

| Aspect | Detail |
|--------|--------|
| **Keyboard Extension** | `UIInputViewController` is a native Swift/ObjC API — first-class support |
| **SQLite** | GRDB.swift — mature, well-maintained Swift SQLite wrapper |
| **Keyboard UI** | UIKit custom views for key rendering |
| **Container App** | SwiftUI for settings, import/export, IM management |
| **Performance** | Best possible — no runtime overhead, smallest binary size |
| **iOS APIs** | Full access to haptics, dark mode, dynamic type, accessibility |
| **Memory footprint** | Minimal — critical for keyboard extension's ~30–60 MB limit |

**Cons**: No code sharing with Android Java codebase. Two separate codebases to maintain. Requires iOS/Swift development expertise.

### Option B: Kotlin Multiplatform (KMP)

Could share DBServer and SearchServer logic between Android and iOS via SQLDelight.

**Cons**: Android codebase is Java, not Kotlin — requires migration first. Compose Multiplatform for iOS is beta-level. Keyboard extension integration with KMP is poorly documented. Larger binary size due to Kotlin/Native runtime. Added build complexity.

**Verdict**: Cost of Java→Kotlin migration and build complexity likely outweighs the benefit of sharing DBServer/SearchServer.

### Option C: Flutter — Not Viable

Flutter runs inside `FlutterViewController` and **cannot create iOS keyboard extensions** (`UIInputViewController`). Dart runtime + Flutter engine adds massive overhead unsuitable for keyboard extension memory constraints.

### Option D: React Native — Not Viable

Same problem as Flutter. Cannot subclass `UIInputViewController`. JavaScript runtime overhead is prohibitive.

### Recommendation

**Swift is the only practical choice** for iOS keyboard extensions. The extension architecture requires native `UIInputViewController` subclassing, and the memory constraints demand minimal runtime overhead.

---

## Input Method Feasibility

All 25+ input methods are **data-driven** — they consist of mapping tables within `lime.db` and the same query logic in SearchServer. No input method has platform-specific code. Once the four components are working with one IM, adding additional input methods requires only:

1. Importing the corresponding IM source `.db` into `lime.db` (same `ATTACH` + `INSERT` flow)
2. Adding the keyboard layout JSON (converted from existing XML)
3. Registering the IM in the `im` configuration table

| Category | Input Methods | Portability |
|----------|--------------|-------------|
| Phonetic (注音) | Phonetic, Phonetic Big5, Phonetic Complete, variants | Fully portable |
| Cangjie (倉頡) | Cangjie, CJ5, ECJ, CJ HK, Simplified Cangjie | Fully portable |
| Dayi (大易) | Dayi, Dayi Uni, Dayi UniP, variants | Fully portable |
| Pinyin (拼音) | Pinyin GB, Pinyin Big5 | Fully portable |
| Stroke (筆劃) | WB, Array, Array10, HS v1/v2/v3 | Fully portable |
| Easy Zhuyin | EZ | Fully portable |

---

## Development Effort Estimate

The development phases align with the four components:

| Phase | Component | Scope | Duration |
|-------|-----------|-------|----------|
| **Phase 1** | DBServer + SearchServer | GRDB.swift database layer, schema setup, import flow, query engine, scoring, caching, learning | 4–6 weeks |
| **Phase 2** | IMService + Virtual Keyboard (single IM) | `UIInputViewController` subclass, composing text simulation, one keyboard layout (Phonetic/注音), candidate bar, text insertion via `textDocumentProxy` | 4–6 weeks |
| **Phase 3** | Virtual Keyboard (all IMs) | JSON layout parser, all 25+ keyboard layouts, keyboard switching, symbol keyboards, popup menus | 4–6 weeks |
| **Phase 4** | DBServer (Container App) | Settings UI, database import/export, cross-platform backup/restore (v2 format), IM download/management | 2–4 weeks |
| **Phase 5** | All components | iPad layouts, dark mode, accessibility, haptics, landscape, testing, App Store submission | 2–4 weeks |
| **Total** | | | **~4–6 months** (one developer) |

---

## Risks and Challenges

| Risk | Component | Severity | Mitigation |
|------|-----------|----------|------------|
| **Memory limit** — extension terminated if exceeding ~30–60 MB | All | High | Use `PRAGMA cache_size`; profile memory on device; lazy-load helper databases |
| **Composing text simulation** — iOS has no native composing region | IMService | High | Track composing state internally; insert-then-delete pattern via `textDocumentProxy` |
| **`textDocumentProxy` limitations** — limited cursor control and document reading | IMService | Medium | Design input logic to work with surrounding text only |
| **App Review rejection** — Apple policy enforcement | All | Medium | Follow HIG strictly; include globe key; privacy disclosure |
| **"Allow Full Access" barrier** — users must explicitly enable for network/clipboard | DBServer | Medium | Core functionality works without Full Access; download databases via Container App |
| **Maintenance burden** — two separate codebases (Java + Swift) | All | Medium | Share `lime.db` schema, JSON layout definitions, and backup format across platforms |
| **Physical keyboard** — iOS does not allow custom keyboard extensions for physical keyboards | IMService | Low | Known limitation; physical keyboard users use iOS built-in input methods |
| **Extension lifecycle** — created/destroyed per text field | IMService, SearchServer | Medium | Persist caches and state via App Group; efficient re-initialization |

---

## Conclusion and Recommendation

Porting LimeIME to iOS is feasible and recommended, organized around four components:

1. **DBServer** (High portability): Standard SQLite operations port directly. Define a cross-platform backup format (ZIP with `lime.db` + `settings.json`) to enable backup/restore between Android and iOS. Use GRDB.swift for database access.

2. **SearchServer** (High portability): Pure query logic with no platform dependencies. Port ~1,500 lines of Java to Swift — SQL statements are identical, caching patterns translate directly.

3. **IMService** (Full rewrite): The bridge layer must be rewritten against `UIInputViewController` and `textDocumentProxy`. Composing text simulation is the most complex challenge. ~2,000 lines of new Swift code.

4. **Virtual Keyboard** (Full rewrite, shared layouts): Rendering code must be rewritten in UIKit, but keyboard layout definitions are portable via a shared JSON format. Convert 60+ Android XML layouts to JSON once; both platforms consume the same files.

**Language**: Swift with UIKit for keyboard rendering and SwiftUI for the container app.

**Development strategy**: Start with DBServer + SearchServer (Phase 1 — highest reuse), then build IMService + one keyboard end-to-end as proof of concept (Phase 2), then expand to all input methods (Phase 3–5).

The strongest aspect of this port is that the **core data — the single `lime.db` database with all IM mapping tables, scoring, and phrase learning — is 100% portable** between Android and iOS with zero modification.
