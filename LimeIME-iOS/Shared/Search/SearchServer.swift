import Foundation

// IM query engine: receives input codes from KeyboardViewController,
// queries lime.db for candidates, manages scoring, caching, and phrase learning.
// Port target: SearchServer.java (~1,500 lines)

final class SearchServer {

    private let db: LimeDB
    private var currentTableName: String = ""

    // MARK: - IM Capability Flags (spec §13 setTableName)
    private(set) var hasNumberMapping: Bool = false
    private(set) var hasSymbolMapping: Bool = false
    private var cachedSelkey: String = "1234567890"

    // MARK: - Sort preference (spec §15 sortSuggestions)
    /// When true, candidates are ordered by (score + basescore) DESC (default DB behaviour).
    /// When false, candidates are returned in DB insertion order (code-alphabetical).
    var sortSuggestions: Bool = true

    // MARK: - Caches
    private var mappingCache:   [String: [Mapping]] = [:]
    private var relatedCache:   [String: [Mapping]] = [:]  // related phrases as Mapping
    private var blacklistCache: Set<String> = []
    private let cacheLock    = NSLock()
    private let maxCacheEntries = 1024

    // Score constants
    private let scoreAdjustmentIncrement = 50
    private let maxScoreThreshold        = 200
    private let minScoreThreshold        = 120

    // MARK: - Learning State (spec §9)
    private var lastCommittedMapping: Mapping? = nil   // previous commit for RP learning
    private var ldPhraseList:      [Mapping]    = []   // current accumulating LD phrase
    private var ldPhraseListArray: [[Mapping]]  = []   // completed LD phrases pending write
    private let learnLock = NSLock()

    private var prefetchThread: Thread?

    init(db: LimeDB) {
        self.db = db
    }

    // MARK: - IM Selection (spec §13 setTableName)

    /// Set the phonetic keyboard variant for code remapping (spec §5).
    /// Pass "phonetic", "et_41", "et26", or "hsu".
    func setPhoneticKeyboardType(_ type: String) {
        db.phoneticKeyboardType = type
        clearAllCaches()
    }

    /// Switch IM table and update capability flags. Clears caches and triggers prefetch.
    func setTableName(_ name: String, hasNumberMapping: Bool = false, hasSymbolMapping: Bool = false) {
        self.hasNumberMapping = hasNumberMapping
        self.hasSymbolMapping = hasSymbolMapping
        cachedSelkey = db.getSelkeyForIM(name)
        // Keep LimeDB's own currentTableName in sync — getMappingByCode(softKeyboard:) uses it.
        db.setTableName(name)
        guard name != currentTableName else { return }
        currentTableName = name
        clearAllCaches()
        triggerPrefetch()
    }

    /// Backwards-compatible alias used by database setup.
    func setCurrentIM(tableName: String) { setTableName(tableName) }

    /// True if current table is a phonetic (tone-based) IM — enables code3r fallback.
    var isPhoneticTable: Bool {
        currentTableName.hasPrefix("phonetic") || currentTableName.hasPrefix("eten") ||
        currentTableName.hasPrefix("hsu")      || currentTableName.hasPrefix("dayi")
    }

    /// True if current table is Stroke5 / WB — enforces 5-character code limit (spec §5).
    var isWBTable: Bool {
        currentTableName == "wb" || currentTableName.hasPrefix("stroke")
    }

    // MARK: - Selkey / Keyname (spec §13)

    /// Returns the selection key string for the current IM (e.g. "1234567890").
    func getSelkey() -> String { cachedSelkey }

    /// Converts a typed code to display-friendly symbol names (e.g. "1q" → "ㄅㄆ").
    /// Delegates to LimeDB.keyToKeyName() which maintains per-table caches.
    func keyToKeyname(_ code: String) -> String {
        let converted = db.keyToKeyName(code, currentTableName, true)
        return converted.isEmpty ? code : converted
    }

    // MARK: - Real Code Length (spec §13 getRealCodeLength)

    /// Returns the code length consumed by the selected mapping in the composing buffer.
    /// For phonetic IMs, strips tone symbols [3467 space] to find the actual boundary.
    func getRealCodeLength(mapping: Mapping, composing: String) -> Int {
        let mappingCode = mapping.code
        guard composing.count > mappingCode.count else { return min(mappingCode.count, composing.count) }
        if isPhoneticTable {
            let toneChars: Set<Character> = ["3", "4", "6", "7", " "]
            // Stripped code length gives the base length without tone markers
            let stripped = mappingCode.filter { !toneChars.contains($0) }
            return max(stripped.count, 1)
        }
        return min(mappingCode.count, composing.count)
    }

    // MARK: - Core Search (spec §13 getMappingByCode)

    /// Returns mapping candidates for the given code.
    /// Prepends a COMPOSING_CODE echo record so index 0 is always the typed code.
    /// `isSoftKeyboard`: affects ordering (soft keyboard gets more results).
    /// `getAllRecords`: use a larger limit.
    func getMappingByCode(_ code: String, isSoftKeyboard: Bool = true,
                          getAllRecords: Bool = false, limit: Int = 50) -> [Mapping] {
        let effectiveLimit = getAllRecords ? 210 : limit

        if isPhoneticTable {
            // For phonetic IMs, delegate entirely to db.getMappingByCode(softKeyboard:getAllRecords:).
            // That method handles: preProcessingRemappingCode, tone detection, and between-search
            // (expandBetweenSearchClause). Doing the remap here too would double-remap for ETEN26/HSU.
            // Cache key uses the raw lowercased input (remap is deterministic, so this is stable).
            let cacheKey = "\(currentTableName):\(code.lowercased()):\(effectiveLimit)"
            cacheLock.lock()
            if blacklistCache.contains(cacheKey) { cacheLock.unlock(); return [] }
            if let cached = mappingCache[cacheKey] { cacheLock.unlock(); return cached }
            cacheLock.unlock()

            let dbResults = db.getMappingByCode(code, softKeyboard: isSoftKeyboard,
                                                getAllRecords: getAllRecords) ?? []
            let echo = Mapping(id: 0, code: code.lowercased(), word: code.lowercased(),
                               score: 0, baseScore: 0, recordType: Mapping.RecordType.composingCode)
            let list = dbResults.isEmpty ? [] : ([echo] + dbResults)
            cacheLock.lock()
            evictIfNeeded()
            if dbResults.isEmpty { blacklistCache.insert(cacheKey) }
            else                  { mappingCache[cacheKey] = list  }
            cacheLock.unlock()
            return list
        }

        // Non-phonetic path: apply remap then exact-match query.
        let rawCode = db.preProcessingRemappingCode(code)
        let code = rawCode.lowercased()
        let cacheKey = "\(currentTableName):\(code):\(effectiveLimit)"

        cacheLock.lock()
        if blacklistCache.contains(cacheKey) { cacheLock.unlock(); return [] }
        if let cached = mappingCache[cacheKey] { cacheLock.unlock(); return cached }
        cacheLock.unlock()

        var dbResults: [Mapping]
        dbResults = (try? db.getMappingByCode(
            code, tableName: currentTableName, limit: effectiveLimit)) ?? []

        // Assign exactMatchToCode record type to all DB results
        dbResults = dbResults.map { m in
            var copy = m; copy.recordType = Mapping.RecordType.exactMatchToCode; return copy
        }

        // Apply sortSuggestions: false → DB insertion order (by id) instead of score order (spec §15)
        if !sortSuggestions {
            dbResults.sort { $0.id < $1.id }
        }

        // Prepend composing-code echo (spec §6 — index 0 always = typed code)
        let echo = Mapping(id: 0, code: code, word: code,
                           score: 0, baseScore: 0, recordType: Mapping.RecordType.composingCode)
        let list = dbResults.isEmpty ? [] : ([echo] + dbResults)

        cacheLock.lock()
        evictIfNeeded()
        if dbResults.isEmpty { blacklistCache.insert(cacheKey) }
        else                  { mappingCache[cacheKey] = list  }
        cacheLock.unlock()

        return list
    }

    // MARK: - Runtime Phrase Suggestion (spec §6, §13 makeRunTimeSuggestion)

    // Each entry is (committed Mapping, code it was typed with).
    // Mirrors Android's List<Pair<Mapping, String>> suggestionLoL.
    private var suggestionContext: [(mapping: Mapping, code: String)] = []
    private let suggestionLock = NSLock()

    /// Build incremental runtime phrase suggestions (spec §6 step 9).
    ///
    /// For each candidate in `currentList`, checks whether that candidate forms
    /// a valid phrase pair with any previously-committed word (via the `related` table).
    /// Matching candidates are promoted to just after the first real result.
    ///
    /// Called after `getMappingByCode()` on the background thread; gated by `smartChineseInput`.
    func makeRunTimeSuggestion(code: String, currentList: [Mapping]) -> [Mapping] {
        suggestionLock.lock()
        let context = suggestionContext
        suggestionLock.unlock()

        guard !context.isEmpty else { return currentList }

        // Build set of words that are valid follow-ons from any committed word.
        // Use the related cache (populated by getRelatedByWord) so it's fast.
        var validNext = Set<String>()
        for entry in context {
            let related = getRelatedByWord(entry.mapping.word)
            for r in related { validNext.insert(r.word) }
        }
        guard !validNext.isEmpty else { return currentList }

        // Partition currentList: promoted (in validNext) vs. the rest.
        // Preserve original order within each partition.
        var promoted: [Mapping] = []
        var rest:     [Mapping] = []
        for var m in currentList {
            if validNext.contains(m.word) {
                m.recordType = Mapping.RecordType.runtimeBuiltPhrase
                promoted.append(m)
            } else {
                rest.append(m)
            }
        }

        guard !promoted.isEmpty else { return currentList }

        // Insert promoted candidates right after the composing-echo (index 0),
        // keeping the rest in their original positions.
        if let echoIdx = rest.firstIndex(where: { $0.isComposingCodeRecord }) {
            rest.insert(contentsOf: promoted, at: echoIdx + 1)
            return rest
        }
        return promoted + rest
    }

    /// Record a committed candidate so future composing can be cross-checked (spec §6).
    func addToSuggestionContext(_ candidate: Mapping, code: String) {
        suggestionLock.lock()
        defer { suggestionLock.unlock() }
        suggestionContext.append((mapping: candidate, code: code))
        // Keep at most 4 entries to bound memory
        if suggestionContext.count > 4 { suggestionContext.removeFirst() }
    }

    /// Clear runtime suggestion context (spec §6 — on composing restart at length 1).
    func clearSuggestionContext() {
        suggestionLock.lock()
        suggestionContext = []
        suggestionLock.unlock()
    }

    // No pruneSuggestionOnBackspace: suggestionContext holds committed words, not composing
    // chains. Backspace shortens mComposing; makeRunTimeSuggestion reruns with the new code.

    // MARK: - Related Phrases (spec §13 getRelatedByWord)

    /// Returns related-phrase candidates following parentWord as Mapping objects.
    func getRelatedByWord(_ word: String, getAllRecords: Bool = false) -> [Mapping] {
        cacheLock.lock()
        if let cached = relatedCache[word] { cacheLock.unlock(); return cached }
        cacheLock.unlock()

        let limit = getAllRecords ? 50 : 10
        let results = (try? db.getRelatedMappings(parentWord: word, limit: limit)) ?? []

        cacheLock.lock()
        evictIfNeeded()
        relatedCache[word] = results
        cacheLock.unlock()

        return results
    }

    // MARK: - Learning (spec §9 learnRelatedPhraseAndUpdateScore)

    /// Records a committed candidate selection for score update and related-phrase learning.
    /// Matches spec §9: score learning + RP learning + LD trigger when RP score > 20.
    func learnRelatedPhraseAndUpdateScore(_ candidate: Mapping) {
        let parent = lastCommittedMapping
        lastCommittedMapping = candidate
        let tableName = currentTableName

        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let self = self else { return }

            // Update score for the committed candidate
            if candidate.id > 0 {
                let cacheKey = "\(tableName):\(candidate.code)"
                self.cacheLock.lock()
                let cached = self.mappingCache[cacheKey]
                self.cacheLock.unlock()
                let currentScore = cached?.first(where: { $0.id == candidate.id })?.score
                    ?? self.minScoreThreshold
                let newScore = min(currentScore + self.scoreAdjustmentIncrement, self.maxScoreThreshold)
                try? self.db.updateScore(id: candidate.id, score: newScore, tableName: tableName)
                // Invalidate cache for this code
                self.cacheLock.lock()
                self.mappingCache.removeValue(forKey: cacheKey)
                self.blacklistCache.remove(cacheKey)
                self.cacheLock.unlock()
            }

            // Learn related phrase from consecutive pair (spec §9 RP Learning)
            if let p = parent, !p.word.isEmpty, !candidate.word.isEmpty,
               p.isRealCandidate, candidate.isRealCandidate {
                let score = (try? self.db.learnRelatedPhrase(
                    parentWord: p.word, childWord: candidate.word)) ?? 0
                // Invalidate related cache
                self.cacheLock.lock()
                self.relatedCache.removeValue(forKey: p.word)
                self.cacheLock.unlock()
                // LD trigger: if RP score > 20 → feed into LD learning (spec §9)
                if score > 20 {
                    self.addLDPhrase(p, ending: false)
                    self.addLDPhrase(candidate, ending: true)
                }
            }
        }
    }

    // MARK: - LD Learning (spec §9 addLDPhrase / learnLDPhrase)

    /// Buffer a mapping for LD phrase learning. When ending=true, saves the accumulated list.
    func addLDPhrase(_ mapping: Mapping?, ending: Bool) {
        learnLock.lock()
        defer { learnLock.unlock() }
        if let m = mapping { ldPhraseList.append(m) }
        if ending {
            if ldPhraseList.count > 1 { ldPhraseListArray.append(ldPhraseList) }
            ldPhraseList = []
        }
    }

    /// Process accumulated LD phrases and write learned multi-character codes to DB.
    private func learnLDPhrase() {
        learnLock.lock()
        let toLearn = ldPhraseListArray
        ldPhraseListArray = []
        learnLock.unlock()

        let tableName = currentTableName
        for phrase in toLearn {
            guard phrase.count > 1, phrase.count <= 4 else { continue }
            var ldCode   = ""
            var qpCode   = ""
            var baseWord = ""
            for m in phrase {
                ldCode   += m.code
                if let first = m.code.first { qpCode += String(first) }
                baseWord += m.word
            }
            if isPhoneticTable {
                // Strip tone symbols (spec §9 QPCode / LDCode)
                let tones: Set<Character> = ["3", "4", "6", "7", " "]
                let stripped = ldCode.filter { !tones.contains($0) }
                if stripped.count > 1 {
                    try? db.addOrUpdateMappingRecord(code: stripped, word: baseWord, tableName: tableName)
                }
                if qpCode.count > 1 {
                    try? db.addOrUpdateMappingRecord(code: qpCode, word: baseWord, tableName: tableName)
                }
            } else {
                if ldCode.count > 1 {
                    try? db.addOrUpdateMappingRecord(code: ldCode, word: baseWord, tableName: tableName)
                }
            }
        }
    }

    // MARK: - Emoji (spec §6, §13 emojiConvert)

    /// Inject emoji candidates into a candidate list at the given position (spec §6 step 5).
    /// `type`: LimeDB.EMOJI_TW / EMOJI_CN / EMOJI_EN
    /// `insertAt`: 0-based index in the real (non-echo) candidate list.
    func injectEmoji(into list: [Mapping], code: String, type: Int, insertAt: Int = 3) -> [Mapping] {
        let emojiCandidates = db.emojiConvert(code, type)
        guard !emojiCandidates.isEmpty else { return list }

        // Deduplicate: drop emoji whose word is already in the list
        let existingWords = Set(list.map { $0.word })
        let unique = emojiCandidates.filter { !existingWords.contains($0.word) }
        guard !unique.isEmpty else { return list }

        var result = list
        let idx = min(insertAt, result.count)
        result.insert(contentsOf: unique, at: idx)
        return result
    }

    // MARK: - Reverse Lookup (spec §8, §13)

    /// Returns a formatted string of all codes for a given word, with key names applied.
    func getCodeListStringFromWord(_ word: String) -> String? {
        db.getCodeListStringByWord(word)
    }

    // MARK: - Finish Input (spec §13 postFinishInput)

    /// Flush all pending learning when the text field loses focus.
    func postFinishInput() {
        DispatchQueue.global(qos: .background).async { [weak self] in
            self?.learnLDPhrase()
        }
    }

    // MARK: - Cache Management

    func clearAllCaches() {
        cacheLock.lock()
        mappingCache.removeAll()
        relatedCache.removeAll()
        blacklistCache.removeAll()
        cacheLock.unlock()
    }

    private func evictIfNeeded() {
        // cacheLock must be held by caller
        if mappingCache.count >= maxCacheEntries  { mappingCache.removeAll() }
        if relatedCache.count  >= maxCacheEntries  { relatedCache.removeAll() }
        if blacklistCache.count >= maxCacheEntries  { blacklistCache.removeAll() }
    }

    // MARK: - Prefetch

    /// Background-thread prefetch for first-character keys — mirrors Android's prefetchCache().
    private func triggerPrefetch() {
        prefetchThread?.cancel()
        let tableName = currentTableName
        let t = Thread {
            let keys = "abcdefghijklmnoprstuvwxyz1234567890"
            for ch in keys {
                guard !Thread.current.isCancelled else { return }
                _ = self.getMappingByCode(String(ch))
            }
        }
        t.qualityOfService = .background
        prefetchThread = t
        t.start()
    }
}
