# IMService Specification for iOS Porting

This document extracts the behavioral logic of LimeIME's input method service from the Android Java sources and presents it as a platform-independent specification. It covers the complete pipeline from key event to composing to search to candidates to commit to learning.

**Source files analyzed**: `LIMEService.java`, `SearchServer.java`, `LIMEKeyboardSwitcher.java`, `CandidateView.java`, `LimeDB.java`, `Mapping.java`, `LIME.java`, `LIMEPreferenceManager.java`

---

## 1. Overview

IMService is the **bridge** between the Virtual Keyboard, the SearchServer, and the operating system's text input API. It receives key events from the keyboard, builds composing code sequences, queries SearchServer for candidates, manages candidate selection, commits text to the active text field, and coordinates the learning flow.

```
Virtual Keyboard
    │ key events (onKey)
    ▼
IMService
    │ composing code → query
    ▼
SearchServer
    │ candidate list
    ▼
IMService
    │ display candidates
    ▼
CandidateView
    │ user picks candidate
    ▼
IMService
    │ commit text → OS text API
    │ learn score/phrase → SearchServer
    ▼
OS Text Input (Android: InputConnection / iOS: textDocumentProxy)
```

---

## 2. Initialization

When a text field gains focus, `initOnStartInput()` configures the IMService based on the text field type.

### Text Field Type → Configuration

| Input Type | `mEnglishOnly` | `mPredictionOn` | Keyboard Mode |
|------------|----------------|-----------------|---------------|
| NUMBER, DATETIME | true | true | MODE_TEXT (symbol keyboard) |
| PHONE | true | true | MODE_PHONE |
| PASSWORD, WEB_PASSWORD, VISIBLE_PASSWORD | true | false | MODE_EMAIL |
| EMAIL_ADDRESS, WEB_EMAIL_ADDRESS | true | false | MODE_EMAIL |
| URI | true | false | MODE_URL |
| SHORT_MESSAGE | false | true | MODE_IM (Chinese) |
| TEXT (default) | from persistent setting | true | MODE_TEXT or Chinese IM |
| TEXT with NO_SUGGESTIONS | — | false | — |
| TEXT with AUTO_COMPLETE | — | false | completion mode in fullscreen |

### Initialization Steps

1. Check keyboard theme change — recreate input view if changed
2. Load IM keyboard config list from SearchServer
3. Reset keyboards if arrow key or split keyboard setting changed
4. Load user settings (`hasVibration`, `hasSound`, `mPersistentLanguageMode`)
5. Build activated IM list from preferences
6. Reset composing state: `mPredictionOn=true`, `mCompletionOn=false`, `mCapsLock=false`
7. Clear `tempEnglishWord` and `tempEnglishList`
8. Apply text field type configuration (table above)
9. If English-only and no prediction → force hide candidate view
10. Else → clear composing

### iOS Equivalent

iOS `UIInputViewController` receives `textDocumentProxy.keyboardType` (`.default`, `.emailAddress`, `.URL`, `.numberPad`, `.phonePad`, etc.) and `textDocumentProxy.returnKeyType`. Map these to the same configuration table. iOS does not have composing region styling, so `mPredictionOn` controls whether composing simulation is active.

**iOS Porting Note — Auto-capitalization**: LimeIME manually implements auto-capitalization via `updateShiftKeyState()` checking EditorInfo flags. iOS provides `textDocumentProxy.autocapitalizationType` (`.none`, `.words`, `.sentences`, `.allCharacters`) which the keyboard extension can read directly. Use this instead of manual implementation.

---

## 3. State Model

### Composing State

| Variable | Type | Purpose |
|----------|------|---------|
| `mComposing` | StringBuilder | Accumulates typed code characters |
| `LDComposingBuffer` | String | Tracks original composing string during continuous typing (LD) |
| `mPredictionOn` | boolean | Whether composing/prediction is active |
| `mCompletionOn` | boolean | Whether app-provided completions are active |

### Candidate State

| Variable | Type | Purpose |
|----------|------|---------|
| `selectedCandidate` | Mapping | Currently highlighted candidate |
| `committedCandidate` | Mapping | Last committed candidate (for related phrase lookup) |
| `mCandidateList` | LinkedList\<Mapping\> | Current displayed candidate list |
| `hasCandidatesShown` | boolean | Whether candidate view is visible |
| `hasChineseSymbolCandidatesShown` | boolean | Whether Chinese punctuation candidates are shown |
| `hasMappingList` | boolean | Whether current list has database mappings |

### Mode State

| Variable | Type | Purpose |
|----------|------|---------|
| `mEnglishOnly` | boolean | true = English mode, false = Chinese IM mode |
| `mEnglishFlagShift` | boolean | Shift key flag for English/Chinese toggle |
| `activeIM` | String | Current input method code (e.g., "phonetic", "cj", "dayi") |
| `hasSymbolMapping` | boolean | Whether current IM accepts symbol-key codes |
| `hasNumberMapping` | boolean | Whether current IM accepts number-key codes |
| `currentSoftKeyboard` | String | Current soft keyboard configuration name |
| `mCapsLock` | boolean | Caps lock state |
| `mAutoCap` | boolean | Auto-capitalization enabled |

### English Prediction State

| Variable | Type | Purpose |
|----------|------|---------|
| `tempEnglishWord` | StringBuffer | Accumulates English characters for prediction |
| `tempEnglishList` | List\<Mapping\> | English prediction suggestions |

### Learning State

| Variable | Type | Purpose |
|----------|------|---------|
| `auto_commit` | int | Auto-commit threshold (0 = off) |

---

## 4. Key Event Dispatch

Entry point: `onKey(int primaryCode, int[] keyCodes, int x, int y)`

### CapsLock Pre-processing

If `mCapsLock` is true and code is lowercase letter (97–122), convert to uppercase (subtract 32).

### Special Key Routing

| Key Code | Action |
|----------|--------|
| `KEYCODE_DELETE` | `handleBackspace()` |
| `KEYCODE_SHIFT` | `handleShift()` |
| `KEYCODE_DONE` | `handleClose()` |
| `KEYCODE_UP/DOWN/LEFT/RIGHT` | Arrow key via `keyDownUp()` |
| `KEYCODE_OPTIONS` | `handleOptions()` |
| `KEYCODE_SPACE_LONGPRESS` | `showIMPicker()` — show input method picker |
| `KEYCODE_SWITCH_TO_SYMBOL_MODE` | `switchKeyboard()` — switch to symbol keyboard |
| `KEYCODE_SWITCH_SYMBOL_KEYBOARD` | `switchKeyboard()` — cycle symbol keyboards (1/2/3) |
| `KEYCODE_NEXT_IM` | `switchToNextActivatedIM(true)` — next Chinese IM |
| `KEYCODE_PREV_IM` | `switchToNextActivatedIM(false)` — previous Chinese IM |
| `KEYCODE_SWITCH_TO_ENGLISH_MODE` | `switchKeyboard()` — Chinese → English |
| `KEYCODE_SWITCH_TO_IM_MODE` | `switchKeyboard()` — English → Chinese |

### Space / Enter Handling

Conditions for candidate picking via Space:
- Space key AND not English-only AND not Phonetic IM
- OR: Space key AND not English-only AND Phonetic IM AND (composing ends with space OR composing is empty)
- OR: Enter key

If candidates are shown → `pickHighlightedCandidate()`. If pick fails and composing is empty → hide candidate view and send the key character. If no candidates shown → `sendKeyChar()`.

### Default Character Handling

All other keys → `handleCharacter(primaryCode)`, followed by auto-commit check:
- If `auto_commit > 0` AND not English-only AND composing length == `auto_commit` AND current keyboard is phonetic → `commitTyped()`.

---

## 5. Composing Flow

### Character Acceptance Rules

`handleCharacter()` determines whether to add a character to the composing buffer based on the current IM's mapping capabilities. The character is accepted into composing if it matches one of these conditions:

| Condition | Accepted Characters |
|-----------|-------------------|
| No symbol mapping, no number mapping | Letters, Space (phonetic only), comma, period |
| No symbol mapping, has number mapping | Letters, digits |
| Has symbol mapping, no number mapping | Letters, symbols, Space (phonetic only) |
| Has symbol mapping, no number mapping, Array IM | Digits after "w" prefix (Array special code) |
| Has symbol mapping, has number mapping | Letters, digits, symbols, Space (phonetic only) |

When a character is accepted:
1. `mComposing.append((char) primaryCode)`
2. `ic.setComposingText(mComposing, 1)` — display composing in text field
3. `updateCandidates()` — query SearchServer for candidates

When a character is NOT accepted (doesn't match any condition):
1. `pickHighlightedCandidate()` — commit current candidate if any
2. `ic.commitText(String.valueOf((char) primaryCode), 1)` — send character directly
3. `finishComposing()` — clear composing state

### English Mode Character Handling

When `mEnglishOnly` is true:
1. Apply shift if keyboard is shifted → `Character.toUpperCase(primaryCode)`
2. If English prediction is enabled and character is a letter → append to `tempEnglishWord`, call `updateEnglishPrediction()`
3. If not a letter → `resetTempEnglishWord()`, call `updateEnglishPrediction()`
4. `commitText(String.valueOf((char) primaryCode), 1)` — commit character immediately (no composing buffer in English mode)

### Backspace Handling

`handleBackspace()` behavior depends on composing length:

| Composing Length | Action |
|-----------------|--------|
| > 1 | Delete last character from `mComposing`, update composing text, re-query candidates |
| == 1 | `clearComposing(true)` — clear composing and force-clear system buffer |
| == 0, candidates shown (not Chinese symbol) | `clearComposing(false)` — clear candidates |
| == 0, candidates shown (Chinese symbol) | Hide candidate view |
| == 0, English mode with prediction | Delete last char from `tempEnglishWord`, re-query English predictions |
| == 0, no candidates | Send `KEYCODE_DEL` to delete in text field |

### Stroke5 (WB) Length Limit

For Stroke5 input method, composing is truncated to maximum 5 characters. If the user types a 6th character, it is discarded and composing is reset to the first 5.

### Keyname Display

After querying candidates, `keyToKeyname(code)` converts the typed code sequence to display-friendly symbol names. If the converted string differs from the original code, it is shown in the composing bar above the candidate list.

Examples:
- Phonetic: "bp" → "ㄅㄆ"
- Cangjie: "ab" → "日月"
- Dayi: typed shifted keys → Dayi radical names

This is cached per code in `keynamecache` for performance.

### Phonetic Keyboard Code Remapping

Different phonetic keyboard layouts map the same QWERTY key to different Zhuyin (Bopomofo) symbols. `preProcessingRemappingCode()` in `LimeDB` translates the typed key sequence to the canonical phonetic code before database lookup.

**Standard Phonetic**: No remapping. Keys map directly to Zhuyin (defined in keyboard XML labels).

**ETEN 41-key**: Single remap table. Each QWERTY key maps to one Zhuyin symbol regardless of position.
- Remap: `ETEN_KEY` → `ETEN_KEY_REMAP` (character-by-character substitution)

**ETEN 26-key**: Dual remap tables — `INITIAL` and `FINAL` — because some keys map to different Zhuyin depending on whether they appear at a non-final or final position in the syllable.
- Non-final position: `ETEN26_KEY` → `ETEN26_KEY_REMAP_INITIAL`
- Final position: `ETEN26_KEY` → `ETEN26_KEY_REMAP_FINAL`
- Exception: keys "q", "w", "d", "f", "j", "k" always use INITIAL remap even when single character
- Position detection: if preceding characters match `[dfjk ]$`, the next character uses INITIAL (new syllable starting)

**HSU**: Dual remap tables with similar logic.
- Non-final position: `HSU_KEY` → `HSU_KEY_REMAP_INITIAL`
- Final position: `HSU_KEY` → `HSU_KEY_REMAP_FINAL`
- Exception: keys "a", "e", "s", "d", "f", "j" always use INITIAL remap when single character
- Position detection: if preceding characters match `[sdfj ]$`, the next character uses INITIAL

**Shifted Key Remapping** (also applies to other IMs):
- Dayi, EZ, standard Phonetic: `SHIFTED_NUMBERIC_KEY` ("!@#$%^&*()") → `SHIFTED_NUMBERIC_KEY_REMAP` ("1234567890") and `SHIFTED_SYMBOL_KEY` ("<>?_:+\"") → `SHIFTED_SYMBOL_KEY_REMAP` (",./-;='")
- Array: `SHIFTED_SYMBOL_KEY` → `SHIFTED_SYMBOL_KEY_REMAP` only

Remap tables are cached per `(tableName + phoneticKeyboardType)` combination in a `HashMap<String, HashMap<String, String>>`.

### Auto-Commit

Checked after every `handleCharacter()` call:
- Condition: `auto_commit > 0` AND not English-only AND `mComposing.length() == auto_commit` AND current keyboard is phonetic (keyboard name contains "phone")
- Action: `commitTyped(ic)` — automatically commits the best candidate

### Continuous Typing (LD Composing)

When the user types more code characters than needed for the first matched word, the system supports continuous typing without requiring the user to explicitly select each candidate:

1. After `commitTyped()`, check: `mComposing.length() > selectedCandidate.getCode().length()`
2. If true → `composingNotFinish = true`
3. Determine real code length via `getRealCodeLength(selectedCandidate, mComposing)`:
   - For phonetic: strips tone symbols [3467 ] from the matched code to find the boundary
   - For ETEN: applies `preProcessingRemappingCode()` before boundary detection
   - If code is dual-mapped: uses full composing length (abandons LD)
4. Trim committed code from composing: `mComposing.delete(0, committedCodeLength)`
5. Strip leading space if present
6. Buffer the mapping for LD learning: `SearchSrv.addLDPhrase(selectedCandidate, false)`
7. If remaining composing > 0: `ic.setComposingText(mComposing, 1)` and `updateCandidates()` — re-query for remaining code
8. Track full original composing in `LDComposingBuffer`

When composing finally finishes (no remaining code):
- If `LDComposingBuffer` is not empty → `SearchSrv.addLDPhrase(selectedCandidate, true)` — final mapping, triggers LD learning
- If `LDComposingBuffer` is empty (LD interrupted) → `SearchSrv.addLDPhrase(null, true)` — signal end without learning

---

## 6. Candidate Flow

### Query Pipeline

`updateCandidates()` runs on a background thread:

1. **Stroke5 length check**: If WB keyboard, truncate composing to 5 characters
2. **Main query**: `SearchSrv.getMappingByCode(code, isSoftKeyboard, getAllRecords)`
   - `code`: current composing string
   - `isSoftKeyboard`: true for soft keyboard (affects candidate ordering)
   - `getAllRecords`: false for initial query, true for "show all" request
3. **Thread yield**: Short sleep to allow interruption by newer query
4. **Selection key setup**:
   - Get base selkey from `SearchSrv.getSelkey()` (typically "1234567890" or IM-specific)
   - Determine `mixedModeSelkey`: space (" ") for symbol-mapping IMs (except Dayi and standard phonetic), backtick ("`") for others
   - Apply `selkeyOption`: 0 = no prepend, 1 = prepend mixedModeSelkey, 2 = prepend mixedModeSelkey + space
5. **Emoji injection** (if enabled):
   - Try English emoji lookup on first candidate word
   - If no English match, try Traditional Chinese (TW) lookup on second candidate
   - If no TW match, try Simplified Chinese (CN) lookup
   - Deduplicate emoji by word
   - Insert at configurable position (`getEmojiDisplayPosition()`, default: 3)
6. **Display candidates**: `setSuggestions(list, hasPhysicalKeyPressed, selkey)`
7. **Keyname display**: Convert code to display symbols via `keyToKeyname()`, show in composing bar if different from raw code

If query returns empty → `clearSuggestions()` (which may trigger Chinese punctuation display if `autoChineseSymbol` is enabled).

### Default Candidate Selection

In `setSuggestions()`:
- If candidate at index 1 exists and `isExactMatchToCodeRecord()` → select index 1 (skip the composing code at index 0)
- Else → select index 0

### Runtime Phrase Suggestion

When `smartChineseInput` is enabled, `makeRunTimeSuggestion()` builds phrase candidates incrementally:

1. Maintains `suggestionLoL` — a list of lists of `Pair<Mapping, code>` representing candidate phrase chains
2. As user types more characters, extends existing suggestions by combining previous best matches with new exact matches for the remaining code
3. Validates combinations against the `related` table (phrase must exist as a related word pair)
4. Scores combinations by base score, averaged over word length
5. Best suggestion list is kept at the last position of `suggestionLoL`
6. Results are tagged as `RECORD_RUNTIME_BUILT_PHRASE`
7. On backspace: removes suggestions that used the deleted character's code
8. On composing restart (length == 1): clears all suggestions

### Thread Interruption

Each `updateCandidates()` call checks if a previous query thread is alive and interrupts it. The query thread checks for interruption at yield points and terminates early if interrupted. This prevents stale results from overwriting newer queries.

---

## 7. English Prediction Flow

A parallel candidate pipeline active when `mEnglishOnly` is true and `englishPrediction` is enabled.

### Accumulation

- Letter character → append to `tempEnglishWord`, call `updateEnglishPrediction()`
- Non-letter character → `resetTempEnglishWord()`, call `updateEnglishPrediction()`
- Backspace → delete last char from `tempEnglishWord`, call `updateEnglishPrediction()`

### Query Pipeline

`updateEnglishPrediction()` runs on a background thread:

1. **Cursor context validation**:
   - Check text after cursor: must be empty or non-alphanumeric
   - Check text before cursor: must match `tempEnglishWord` (prevents stale predictions when cursor moved)
2. **Query**: `SearchSrv.getEnglishSuggestions(tempEnglishWord)`
3. **Build candidate list**: Insert `tempEnglishWord` itself as first item (composing code record), then add suggestions
4. **Emoji injection**: Same logic as Chinese flow but English-only lookup (`EMOJI_EN`)
5. **Store in `tempEnglishList`**: Used later for commit handling
6. **Display**: `setSuggestions(list, hasPhysicalKeyPressed, "1234567890")`

If `tempEnglishWord` is empty or query returns empty → `clearSuggestions()`.

### iOS Porting: Use Built-in APIs Instead of Custom Dictionary

iOS provides built-in English word completion that keyboard extensions can use, eliminating the need to port LimeIME's custom English dictionary and prediction logic.

**`UITextChecker`** — Apple's word completion and spell checking API:
- `completions(forPartialWordRange:in:language:)` — returns word completions for a partial string
- `guesses(forWordRange:in:language:)` — returns spelling correction suggestions
- Available to keyboard extensions without "Allow Full Access"
- Supports multiple languages

**`UILexicon`** — personalized vocabulary (via `requestSupplementaryLexicon()` on `UIInputViewController`):
- Contains words from user's contacts, text shortcuts, and paired device vocabulary
- Returns `UILexiconEntry` objects with `userInput` → `documentText` mappings
- Supplements `UITextChecker` with personalized vocabulary

**What to drop on iOS:**
- English dictionary data in `lime.db`
- `SearchServer.getEnglishSuggestions()` implementation
- `tempEnglishWord` / `tempEnglishList` state tracking
- Cursor context validation logic (`getTextBeforeCursor`/`getTextAfterCursor` matching)

**What to keep:**
- Emoji injection on top of `UITextChecker` results (wrap results as `Mapping` objects with `RECORD_ENGLISH_SUGGESTION` type, then apply the same emoji lookup)
- Candidate display integration (feed `UITextChecker` completions into the candidate bar)

**What iOS does NOT provide:** The system's full predictive text engine (the one powering the built-in keyboard). Third-party extensions cannot access that. `UITextChecker` provides word completion (prefix matching) and spell correction, which is functionally equivalent to `getEnglishSuggestions()`.

---

## 8. Candidate Selection and Commit

### Selection Entry Points

- **`pickHighlightedCandidate()`**: Called by Space/Enter key. Delegates to `mCandidateView.takeSelectedSuggestion()` which calls back `pickCandidateManually(index)`.
- **`pickCandidateManually(int index)`**: Called when user taps a candidate or via selection key. Sets `selectedCandidate = mCandidateList.get(index)`.

### Three Commit Paths

**Path 1 — Completion Suggestion** (fullscreen mode with app completions):
- Condition: `mCompletionOn` and candidate `isPartialMatchToCodeRecord()`
- Action: `ic.commitCompletion(mCompletions[index])`

**Path 2 — Chinese / Related Phrase Candidate**:
- Condition: `mComposing.length() > 0` OR candidate is not a composing code record (i.e., it's a related phrase or database match)
- Action: `commitTyped(ic)` (see detailed flow below)

**Path 3 — English Prediction**:
- Condition: English-only mode with prediction
- If emoji: `ic.commitText(word + " ", 0)`
- If regular word: `ic.commitText(word.substring(tempEnglishWord.length()) + " ", 0)` — commits only the suffix not yet typed, plus a space
- Then: `resetTempEnglishWord()`, `clearSuggestions()`

After any selection via `pickCandidateManually()` → `updateRelatedPhrase(true)` to fetch related phrases for the next word.

### commitTyped() Detailed Flow

1. **Null check**: If `selectedCandidate` is null, return
2. **Surrogate check**: If word contains Unicode surrogate (emoji), commit directly and clear composing
3. **Han conversion**: If `hanConvertOption` != 0, apply `SearchSrv.hanConvert(word)` before committing. Show toast notification if `hanConvertNotify` is enabled (throttled to once per minute).
   - **iOS Porting Note**: LimeIME uses `hanconvertv2.db` for simple 1-to-1 character-by-character lookup (no context-dependent conversion). iOS provides `CFStringTransform` with `kCFStringTransformToSimplifiedChinese` / `kCFStringTransformToTraditionalChinese` which is functionally equivalent or better — uses Apple's maintained mapping, no need to ship `hanconvertv2.db`. Drop: `LimeHanConverter` class and `hanconvertv2.db`. Replace with a single `CFStringTransform()` call.
4. **Commit text**: `ic.commitText(wordToCommit, 1)`
5. **Special clear**: If Stroke5 (wb), emoji, or Chinese punctuation → `clearComposing(true)`
6. **Continuous typing check** (LD composing):
   - Get `committedCodeLength` via `SearchSrv.getRealCodeLength(selectedCandidate, mComposing)`
   - If `mComposing.length() > selectedCandidate.getCode().length()` → composing not finished:
     - Buffer mapping: `SearchSrv.addLDPhrase(selectedCandidate, false)`
     - Trim composing: `mComposing.delete(0, committedCodeLength)`
     - Strip leading space if present
     - If remaining composing > 0 → `ic.setComposingText(mComposing, 1)` + `updateCandidates()`
   - If composing finished:
     - If `LDComposingBuffer` not empty → `SearchSrv.addLDPhrase(selectedCandidate, true)` (final)
     - Else → `SearchSrv.addLDPhrase(null, true)` (interrupted)
7. **Post-commit** (only if composing finished, no continuous typing):
   - `committedCandidate = new Mapping(selectedCandidate)` — save for related phrase
   - `clearComposing(false)`
   - `updateRelatedPhrase(false)` — display related phrases as next candidates
   - `SearchSrv.learnRelatedPhraseAndUpdateScore(committedCandidate)` — learn score + related
   - `SearchSrv.getCodeListStringFromWord(committedCandidate.getWord())` — reverse lookup

### Runtime Phrase Learning on Commit

When a `RUNTIME_BUILT_PHRASE` candidate is selected, `getRealCodeLength()` spawns a background thread to learn all sub-phrases from `bestSuggestionList`:
- For each sub-phrase where the selected word starts with the sub-phrase word:
  - If word length > 8 → stop learning
  - `dbadapter.addOrUpdateMappingRecord(code, word)` — save as new mapping
  - `removeRemappedCodeCachedMappings(code)` — invalidate cache

### Related Phrase Display

`updateRelatedPhrase()` runs after commit on a background thread:
1. Check `committedCandidate` has a non-empty word
2. Skip if committed candidate is emoji or Chinese punctuation
3. Query: `SearchSrv.getRelatedByWord(committedCandidate.getWord(), getAllRecords)`
4. Display results as candidate list with selkey "1234567890"

---

## 9. Learning Flow

Learning is triggered in the background thread spawned by `learnRelatedPhraseAndUpdateScore()`. Three mechanisms operate in sequence:

### Score Learning

When the user selects a candidate, `learnRelatedPhraseAndUpdateScore(committedCandidate)` adds it to a `scorelist` queue. The background thread processes the queue:
- Updates the `score` column in the IM table for the selected mapping
- Updates in-memory cache to reflect the new score
- Higher scores cause the candidate to appear higher in future results (when `sortSuggestions` is enabled)

### Related Phrase (RP) Learning

`learnRelatedPhrase()` processes consecutive pairs from the `scorelist`:

For each pair (unit, unit2) where:
- Both have non-empty words
- unit is exact match, partial match, or related phrase record
- unit2 is exact match, partial match, related phrase, Chinese punctuation, or emoji

Action:
1. `dbadapter.addOrUpdateRelatedPhraseRecord(unit.getWord(), unit2.getWord())` — increments score in `related` table
2. Returns the updated score
3. **LD trigger**: If returned score > 20 AND `learnPhrase` is enabled → `addLDPhrase(unit, false)` + `addLDPhrase(unit2, true)` — feeds the pair into LD learning

### LD (Learning Dictionary) Phrase Learning

`addLDPhrase(mapping, ending)` buffers individual mappings:
- If `mapping` is not null → add to `LDPhraseList`
- If `ending` is true → if list has > 1 mappings, add to `LDPhraseListArray`; start new `LDPhraseList`
- If `mapping` is null and `ending` is true → discard current list (interrupted)

`learnLDPhrase()` processes accumulated phrases from `LDPhraseListArray`:

For each phrase list (max 4 characters):

1. **Get first unit's code**: If the unit has no code (selected from related phrase list), do reverse lookup via `dbadapter.getMappingByWord()` to find the canonical code
2. **Build codes character by character**:
   - **LDCode**: Full concatenation of each character's code (e.g., "su3" + "cl3" = "su3cl3")
   - **QPCode**: First character of each character's code (e.g., "s" + "c" = "sc")
3. **For multi-character words within the phrase**: Break down into individual characters, look up each one's code
4. **For phonetic table**: Strip tone symbols [3467 ] from LDCode → e.g., "su3cl3" becomes "sucl"
5. **Write to database**:
   - If phonetic and LDCode length > 1: `addOrUpdateMappingRecord(LDCode, baseWord)`
   - If phonetic and QPCode length > 1: `addOrUpdateMappingRecord(QPCode, baseWord)`
   - If non-phonetic and baseCode length > 1: `addOrUpdateMappingRecord(baseCode, baseWord)`
6. **Invalidate caches**: `removeRemappedCodeCachedMappings()` + `updateSimilarCodeCache()`

### QP (Quick Phrase) Code

QPCode is part of LD learning. It creates a first-letter shortcut for learned phrases:
- QPCode = first character of each constituent word's code, concatenated
- Example: 你好 with codes "su3" + "cl3" → QPCode = "sc"
- User can later type "sc" and "你好" appears as a candidate
- Only generated for phonetic table (other IMs use the full concatenated code as LDCode)

---

## 10. Mode Switching

### Two Levels of Switching

LimeIME has **two independent levels** of keyboard/IM switching:

1. **OS-level switching** — switch between LimeIME and other system keyboards (e.g., iOS built-in, other 3rd-party keyboards). LimeIME registers as a **single keyboard** with the OS.
2. **LIME-internal switching** — switch between different input methods (Phonetic, Cangjie, Dayi, English, Symbol, etc.) within LimeIME itself. The OS is not aware of these internal IMs.

### Key Assignments for Switching

| Key Action | Function |
|------------|----------|
| **Keyboard key — single press** | Hide the soft keyboard |
| **Keyboard key — long press** | Show options menu (switch to other system keyboards, Han conversion settings, etc.) |
| **Space key — slide left/right** | Switch to next/previous IM within LIME |
| **Space key — long press** | Show LIME IM picker (list of activated IMs) |
| **NEXT_IM / PREV_IM keys** | Switch to next/previous IM within LIME (if keyboard layout includes these keys) |
| **SWITCH_TO_ENGLISH / SWITCH_TO_IM keys** | Toggle between Chinese IM and English mode |
| **SWITCH_TO_SYMBOL keys** | Toggle to symbol keyboard |

### iOS Porting Note — Globe Key (OS-Level Keyboard Switch)

iOS keyboard extensions must implement OS-level keyboard switching, but **the globe key does not need its own dedicated bottom-row position**. Apple's contract (stable since iOS 11):

- Check `UIInputViewController.needsInputModeSwitchKey` on every layout pass (e.g., in `viewWillLayoutSubviews`)
- **If `false`** (modern iPhone X and later — virtually all current iPhones): The system draws its own globe/dictation row below the keyboard's input view. The user taps the system's globe and UIKit handles the switch — LIME does nothing.
- **If `true`** (iPad in some configurations, older Home-button iPhones, certain embedded contexts): LIME must provide an in-keyboard control that calls `advanceToNextInputMode()`. Apple does **not** require this control to be a dedicated, always-visible globe button — it can be any easily-accessible affordance.
- Re-check on trait/size changes — the value can change (e.g., iPad split view, external keyboard attach/detach)

**LIME design — long-press the keyboard key:**

LIME does not show an independent globe key. The design mirrors the existing LIME Android behavior, with one visual difference for iOS:

- **Keyboard key — single press**: Hide the soft keyboard (same on Android and iOS)
- **Keyboard key — long press**:
  - **Visual preview**: Android shows a keyboard icon preview; iOS shows a **globe icon (🌐) preview** to satisfy Apple's "clearly visible globe affordance" expectation
  - **Action**: Pop up an options menu containing:
    - **Switch to next system keyboard** → calls `advanceToNextInputMode()` (one entry in the menu, not the only action)
    - **Show keyboard picker** → `handleInputModeList(from:with:)`
    - Han conversion settings
    - LIME preferences
    - Other options

This long-press-on-keyboard-key pattern (with globe icon as the long-press preview) is used by existing App Store–approved third-party keyboards. The globe icon preview is what makes it acceptable to Apple's review — the user sees a globe affordance when they long-press, even though the actual switch is one menu item among several.

When `needsInputModeSwitchKey == false` (modern iPhones), the OS-switch entries in the menu can either be hidden (system row handles it) or kept for parity with the Android behavior.

**Independent of OS switching — LIME-internal IM switching:**

- **Space key — slide left/right**: Switch to next/previous IM within LIME (Phonetic ↔ Cangjie ↔ Dayi ↔ ...)
- **Space key — long press**: Show LIME IM picker (list of activated IMs)
- These are completely invisible to iOS — no API calls, just internal state changes via `switchToNextActivatedIM()`

**Do NOT delete the `advanceToNextInputMode()` code path** — it must exist conditionally inside the long-press menu for the cases where `needsInputModeSwitchKey == true`.

### Chinese ↔ English

`switchChiEng()`:
1. `clearComposing(false)` — clear current composing
2. `mKeyboardSwitcher.toggleChinese()` — switch keyboard layout
3. `mEnglishOnly = !mKeyboardSwitcher.isChinese()` — update mode flag
4. `mLIMEPref.setLanguageMode(mEnglishOnly)` — persist if persistent mode enabled
5. Show toast notification (Chinese/English)
6. `clearSuggestions()` — clear candidate list

### Switch to Next/Previous IM (LIME-Internal)

`switchToNextActivatedIM(boolean forward)`:
- Cycles through the activated IM list (from `keyboard_state` preference)
- Updates `activeIM`, calls `SearchSrv.setTableName()` to switch the query table
- Updates keyboard layout via `mKeyboardSwitcher.setKeyboardMode()`
- Resets composing and candidates
- Triggered by: space key slide/long-press, NEXT_IM/PREV_IM key codes

### Symbol Keyboard

`switchKeyboard(keycode)` handles:
- `KEYCODE_SWITCH_TO_SYMBOL_MODE`: Toggle to symbol keyboard
- `KEYCODE_SWITCH_SYMBOL_KEYBOARD`: Cycle through symbol keyboards 1/2/3
- `KEYCODE_SWITCH_TO_ENGLISH_MODE`: Switch from Chinese IM to English keyboard
- `KEYCODE_SWITCH_TO_IM_MODE`: Switch from English back to Chinese IM

---

## 11. Chinese Punctuation

### Auto Chinese Symbol

When `autoChineseSymbol` is enabled and `clearSuggestions()` is called in Chinese mode with candidates visible:
1. `hasChineseSymbolCandidatesShown = true`
2. Load Chinese symbol list via `ChineseSymbol.getChineseSymbolList()`
3. Display with selkey "1234567890"
4. User can pick a punctuation symbol like a regular candidate

### Punctuation Characters

The Chinese symbol list includes: 。，、；：？！「」『』【】〈〉《》（）—…·＆＊＃＠ and more.

---

## 12. OS Text API Mapping

### Android InputConnection → iOS textDocumentProxy

| Android `InputConnection` | iOS `textDocumentProxy` | Context in IMService |
|---------------------------|------------------------|---------------------|
| `setComposingText(text, cursor)` | `insertText()` + manual tracking | Composing display — iOS has no native composing region. Must insert text and track length to delete-and-replace on update. |
| `finishComposingText()` | No-op (text already inserted) | End composing — on iOS the text is already committed inline. |
| `commitText(text, cursor)` | `insertText(text)` | Commit selected candidate |
| `deleteSurroundingText(before, after)` | `deleteBackward()` × count | Delete characters (used sparingly) |
| `sendKeyEvent(KeyEvent)` | `insertText()` or `deleteBackward()` | Arrow keys, DEL key — iOS `textDocumentProxy` has `adjustTextPosition(byCharacterOffset:)` for cursor movement |
| `getTextBeforeCursor(n, flags)` | `documentContextBeforeInput` | Read preceding text for English prediction validation and related phrase context |
| `getTextAfterCursor(n, flags)` | `documentContextAfterInput` | Read following text for English prediction validation |
| `commitCompletion(CompletionInfo)` | N/A | App-provided completions — not available on iOS keyboard extensions |
| `clearMetaKeyStates(states)` | N/A | Physical keyboard meta state — not applicable on iOS |

### iOS Composing Simulation

Since iOS `textDocumentProxy` has no `setComposingText()`:
1. Track composing length internally (e.g., `composingLength` counter)
2. On first composing character: `insertText(char)`; set `composingLength = 1`
3. On subsequent characters: `insertText(char)`; increment `composingLength`
4. On composing update (backspace within composing): `deleteBackward()` × `composingLength`, then `insertText(newComposingText)`; update `composingLength`
5. On commit: composing text is already in place — just reset `composingLength = 0`
6. On cancel: `deleteBackward()` × `composingLength`; reset

---

## 13. SearchServer API Reference

Methods called by IMService, with parameters and behavior:

### Query Methods

```
getMappingByCode(String code, boolean isSoftKeyboard, boolean getAllRecords) → List<Mapping>
```
Main candidate lookup. Returns mappings matching `code` from the current IM table, sorted by score. Triggers `makeRunTimeSuggestion()` internally if smart input is enabled.

```
getRelatedByWord(String word, boolean getAllRecords) → List<Mapping>
```
Related phrase lookup. Returns words that commonly follow `word`, from the `related` table.

```
getEnglishSuggestions(String word) → List<Mapping>
```
English word prediction. Returns words starting with `word` from the English dictionary.

```
getSelkey() → String
```
Returns the selection key string for the current IM (e.g., "1234567890").

```
keyToKeyname(String code) → String
```
Converts typed code to display symbol names (e.g., "bp" → "ㄅㄆ"). Cached.

```
getRealCodeLength(Mapping selectedMapping, String currentCode) → int
```
Determines the actual code length consumed by the selected mapping in the composing buffer. Handles phonetic tone stripping and ETEN remapping. Also triggers runtime phrase learning for `RUNTIME_BUILT_PHRASE` candidates.

### Learning Methods

```
learnRelatedPhraseAndUpdateScore(Mapping committedCandidate)
```
Adds mapping to score queue. Background thread processes: updates score in DB, learns related phrases, triggers LD learning when RP score > 20.

```
addLDPhrase(Mapping mapping, boolean ending)
```
Buffers a mapping for LD phrase learning. When `ending=true`, saves the accumulated list. When `mapping=null` and `ending=true`, discards the current list.

### Configuration Methods

```
setTableName(String table, boolean numberMapping, boolean symbolMapping)
```
Switches the active IM table. Updates `hasNumberMapping` and `hasSymbolMapping` flags. Triggers cache prefetch.

```
getTablename() → String
```
Returns the current active table name.

```
resetCache()
```
Clears all cached mappings. Called when switching IMs or clearing user data.

### Conversion Methods

```
hanConvert(String input) → String
```
Applies Traditional↔Simplified Chinese conversion based on `hanConvertOption` setting. **iOS alternative**: Replace with `CFStringTransform(kCFStringTransformToSimplifiedChinese / kCFStringTransformToTraditionalChinese)` — no need to port `LimeHanConverter` or ship `hanconvertv2.db`.

```
emojiConvert(String code, int type) → List<Mapping>
```
Converts a word to emoji. Type: `EMOJI_EN`, `EMOJI_TW`, `EMOJI_CN`. **iOS note**: No built-in word-to-emoji API exists on iOS. Keep the feature by converting `emoji.db` to a bundled JSON dictionary (small read-only dataset, no SQLite overhead needed). Preserves the inline emoji suggestion UX.

```
getCodeListStringFromWord(String word)
```
Reverse lookup: returns all codes that produce `word`. Used for notification display.

### Lifecycle Methods

```
postFinishInput()
```
Called when input finishes (text field loses focus). Flushes pending learning: copies `scorelist` to snapshot, spawns background thread to run `learnRelatedPhrase()` and `learnLDPhrase()` on accumulated data. Ensures all queued learning is processed even if the user switches away.

### IM Configuration Methods

```
getAllImKeyboardConfigList() → List<ImConfig>
```
Returns all IM-to-keyboard configuration mappings.

```
getKeyboardConfigList() → List<Keyboard>
```
Returns all keyboard layout configurations from the `keyboard` table. Used by `LIMEKeyboardSwitcher` to map IM codes to keyboard XML resources.

---

## 14. Data Model: Mapping

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Database record ID |
| `code` | String | Input code (lowercase) |
| `codeorig` | String | Original input code (preserves case) |
| `code3r` | String | Code without tone keys (phonetic only) |
| `word` | String | Output word / candidate text |
| `pword` | String | Parent word (for related phrases) |
| `related` | String | Related phrase info |
| `score` | int | User score (learned frequency) |
| `basescore` | int | Base score from preloaded data |
| `highLighted` | Boolean | From highlighted list vs exact match |
| `recordType` | int | Type classifier (see constants below) |

### Record Type Constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `RECORD_COMPOSING_CODE` | 1 | The input code itself (first item in list) |
| `RECORD_EXACT_MATCH_TO_CODE` | 2 | Exact code match from database |
| `RECORD_PARTIAL_MATCH_TO_CODE` | 3 | Prefix match |
| `RECORD_RELATED_PHRASE` | 4 | Related phrase suggestion |
| `RECORD_ENGLISH_SUGGESTION` | 5 | English word suggestion |
| `RECORD_RUNTIME_BUILT_PHRASE` | 6 | Generated phrase from LD/QP learning |
| `RECORD_CHINESE_PUNCTUATION_SYMBOL` | 7 | Chinese punctuation |
| `RECORD_HAS_MORE_RECORDS_MARK` | 8 | "More..." placeholder |
| `RECORD_EXACT_MATCH_TO_WORD` | 9 | Reverse lookup: word→code exact |
| `RECORD_PARTIAL_MATCH_TO_WORD` | 10 | Reverse lookup: word→code partial |
| `RECORD_COMPLETION_SUGGESTION_WORD` | 11 | Word completion suggestion |
| `RECORD_EMOJI_WORD` | 12 | Emoji character |

### Type Checker Methods

Each record type has a corresponding `is*Record()` method (e.g., `isExactMatchToCodeRecord()`, `isRelatedPhraseRecord()`, `isRuntimeBuiltPhraseRecord()`). These are used throughout the commit and learning flows to determine handling logic.

---

## 15. User Settings Reference

All user-adjustable settings from `LIMEPreferenceManager`, grouped by category with the logic each setting affects.

### Input Behavior

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Active IM | `keyboard_list` | String | "phonetic" | Which IM table SearchServer queries |
| IM activated state | `keyboard_state` | String | all enabled | Which IMs appear in NEXT_IM/PREV_IM cycle |
| Phonetic keyboard type | `phonetic_keyboard_type` | String | "phonetic" | Code remapping in `preProcessingRemappingCode()`: Standard / ETEN / ETEN26 / HSU |
| Auto-commit threshold | `auto_commit` | int | 0 (off) | Composing auto-commit when length reaches threshold (phonetic only) |
| Han conversion | `han_convert_option` | int | 0 (off) | 0=off, 1=T→S, 2=S→T. Applied in `commitTyped()` before `commitText()` |
| Han conversion notify | `han_convert_notify` | boolean | true | Toast notification on Han conversion (throttled to 1/min) |
| Selection key option | `selkey_option` | int | 0 | Candidate selkey prepend: 0=none, 1=prepend mixedModeSelkey, 2=prepend with space |
| Smart Chinese input | `smart_chinese_input` | boolean | false | Enables runtime phrase suggestion (`makeRunTimeSuggestion`) in SearchServer |
| Auto Chinese symbol | `auto_chinese_symbol` | boolean | false | Shows Chinese punctuation candidates when candidate list cleared |
| Persistent language mode | `persistent_language_mode` | boolean | false | Remembers Chinese/English mode across text fields |
| Language mode | `language_mode` | String | "no" | Stored Chinese/English state ("yes"=English, "no"=Chinese) |

### Learning

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Learn related words | `candidate_suggestion` | boolean | true | Enables RP learning in `learnRelatedPhrase()` |
| Learn phrase | `learn_phrase` | boolean | true | Enables LD phrase learning when RP score > 20 |
| Sort suggestions | `learning_switch` | boolean | true | Score-based candidate reordering in search results |
| Similar code enable | `similiar_enable` | boolean | true | Show candidates with similar (prefix-matching) codes |
| Similar code limit | `similiar_list` | int | 20 | Max number of similar-code candidates |

### Emoji

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Emoji mode | `enable_emoji` | boolean | true | Emoji injection in candidate list (both Chinese and English flows) |
| Emoji display position | `enable_emoji_position` | int | 3 | Insert position in candidate list |

### English

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| English prediction | `english_dictionary_enable` | boolean | true | `updateEnglishPrediction()` activation in English mode |
| Auto-capitalization | `auto_cap` | boolean | true | `updateShiftKeyState()` after key handling |

### Keyboard Appearance

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Font size | `font_size` | float | 1.0 | Candidate text size multiplier |
| Keyboard size | `keyboard_size` | float | 1.0 | Key size scale multiplier |
| Show arrow keys | `show_arrow_key` | int | 0 | Arrow key row visibility mode |
| Split keyboard | `split_keyboard_mode` | int | 0 | Split keyboard layout mode |
| Keyboard theme | `keyboard_theme` | int | 0 | Theme index — triggers input view recreation on change |
| Show number keypads | `display_number_keypads` | boolean | false | Number row visibility |
| Number row in English | `number_row_in_english` | boolean | true | Number row in English keyboard |

### Feedback

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Vibrate on keypress | `vibrate_on_keypress` | boolean | true | Haptic feedback on key press. iOS: `UIImpactFeedbackGenerator` |
| Vibrate level | `vibrate_level` | int | 40 | Vibration duration in ms. iOS: map to `UIImpactFeedbackGenerator` style — e.g., ≤20ms→`.light`, ≤50ms→`.medium`, >50ms→`.heavy`. Note: iOS does not allow custom duration; only style selection. |
| Sound on keypress | `sound_on_keypress` | boolean | false | Audio feedback. iOS: use `UIInputViewController.playInputClick()` — the proper API for keyboard click sounds, respects system sound settings |

### Reverse Lookup

| Setting | Pref Key | Type | Default | Affects |
|---------|----------|------|---------|---------|
| Reverse lookup notify | `reverse_lookup_notify` | boolean | true | Show code lookup notification after commit |
| Reverse lookup table | `{table}_im_reverselookup` | String | "none" | Which IM to use for reverse lookup display |

### Physical Keyboard (exclude from iOS port)

These settings are not applicable to iOS keyboard extensions:
`physical_keyboard_enable`, `physical_keyboard_type`, `disable_physical_selkey`, `disable_physical_selkey_option`, `english_dictionary_physical_keyboard`, `physical_keyboard_sort`, `switch_english_mode`, `switch_english_mode_shift`, `hide_software_keyboard_typing_with_physical`, `three_rows_remapping`

### Per-Table Metadata (managed by DBServer, not user-facing)

`{table}total_record`, `{table}mapping_version`, `{table}mapping_file`, `{table}mapping_file_temp`, `total_userdict_record`, `searchsrv_reset_cache`
