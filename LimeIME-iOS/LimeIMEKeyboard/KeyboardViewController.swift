import UIKit

// Full keyboard extension entry point.
// Implements IMService behavior per IM_SERVICE.md spec.

final class KeyboardViewController: UIInputViewController {

    // MARK: - Components
    private var candidateBar: CandidateBarView!
    private var keyboardView:  KeyboardView!

    // MARK: - SearchServer
    private var searchServer: SearchServer?
    private var db: LimeDB?

    // MARK: - Composing State (spec §3)
    private var mComposing:      String = ""  // current composing code buffer
    private var composingLength: Int    = 0   // chars inserted inline (iOS composing sim §12)
    private var mPredictionOn:   Bool   = true
    private var mCompletionOn:   Bool   = false

    // MARK: - Candidate State (spec §3)
    private var selectedCandidate:  Mapping? = nil
    private var committedCandidate: Mapping? = nil
    private var mCandidateList:     [Mapping] = []
    private var hasCandidatesShown: Bool = false

    // MARK: - Mode State (spec §3)
    private var mEnglishOnly: Bool = false
    private var mCapsLock:    Bool = false
    private var isShiftOn:    Bool = false
    private var activeIM:     String = "phonetic"
    private var currentLayout: LimeKeyLayout = .phonetic

    // MARK: - English Prediction (spec §7 — iOS: UITextChecker replaces custom dict)
    private var tempEnglishWord: String = ""
    private let textChecker = UITextChecker()

    // MARK: - Auto-Commit (spec §3)
    private var autoCommit: Int = 0  // 0 = off; >0 = auto-commit at that composing length

    // MARK: - Settings (spec §15 — read from shared UserDefaults)
    private var hanConvertOption:        Int  = 0     // 0=off, 1=T→S, 2=S→T
    private var autoChineseSymbol:       Bool = false // show Chinese punctuation after commit
    private var sortSuggestions:         Bool = false
    private var smartChineseInput:       Bool = false // runtime phrase suggestion
    private var learnPhrase:             Bool = true  // enable LD phrase learning
    private var englishPredictionOn:     Bool = true  // enable English prediction
    private var selkeyOption:            Int  = 0     // 0=none, 1=prepend, 2=prepend+space
    private var hasVibration:            Bool = false
    private var hasSound:                Bool = false
    private var mPersistentLanguageMode: Bool = false // persist English/Chinese mode
    private var phoneticKeyboardType:    String = "phonetic"

    // MARK: - Activated IM Cycling (spec §10)
    private var activatedIMs:  [ImConfig] = []
    private var activeIMIndex: Int        = 0

    // MARK: - Chinese Punctuation (spec §11)
    private var hasChineseSymbolCandidatesShown: Bool = false

    // MARK: - Symbol Keyboard (spec §10)
    private var isSymbolMode:       Bool = false
    private var symbolPageIndex:    Int  = 0  // 0 = lime_number_symbol, 1 = shift variant
    private let symbolLayouts = ["lime_number_symbol", "lime_number_symbol_shift"]
    // Layout to restore when leaving symbol mode
    private var preSymbolLayout: LimeKeyLayout? = nil

    // MARK: - LD Composing Buffer (spec §5, §8)
    private var LDComposingBuffer: String = ""

    // MARK: - Search Thread Management (spec §6 Thread Interruption)
    private var currentSearchID: UInt64 = 0

    // MARK: - Self-Update Guard (spec §12)
    // Set true around our own insertText/deleteBackward calls to suppress textDidChange checks
    private var isSelfUpdate = false

    // MARK: - Keyboard Geometry
    private let candidateBarHeight: CGFloat = 44
    private let keyRowHeight:       CGFloat = 44
    private var keyboardHeightConstraint: NSLayoutConstraint?

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        if let loaded = LayoutLoader.load("lime_phonetic") { currentLayout = loaded }
        LayoutLoader.prefetchCommonLayouts()
        setupDatabase()
        setupKeyboardUI()
        applyHeight()
    }

    /// Called every time the keyboard becomes visible (spec §2 initOnStartInput).
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        initOnStartInput()
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        applyHeight()
        updateGlobeKeyVisibility()
    }

    // MARK: - Trait / Theme Change (spec §2)

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard let prev = previousTraitCollection else { return }

        // Theme change (light ↔ dark): keyboard background updates automatically via system colors,
        // but we need to refresh the keyboard view to pick up any color-dependent resources.
        if prev.userInterfaceStyle != traitCollection.userInterfaceStyle {
            keyboardView?.setLayout(currentLayout)
        }

        // Horizontal size class change (e.g. iPad split-screen, landscape):
        // reset composing and reload the layout.
        if prev.horizontalSizeClass != traitCollection.horizontalSizeClass {
            cancelComposing()
            updateGlobeKeyVisibility()
            applyHeight()
        }
    }

    override func textWillChange(_ textInput: UITextInput?) {
        // Nothing — detection deferred to textDidChange
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // Guard: skip checks triggered by our own insertText/deleteBackward (spec §12)
        guard !isSelfUpdate else { return }
        // If the cursor changed externally while composing, cancel composing
        if composingLength > 0 {
            let before = textDocumentProxy.documentContextBeforeInput ?? ""
            if !before.hasSuffix(mComposing) {
                cancelComposing()
            }
        }
        updateShiftForAutoCap()
    }

    // MARK: - Initialization (spec §2 initOnStartInput)

    private func initOnStartInput() {
        mCompletionOn = false
        mCapsLock     = false

        // Map keyboard type → mEnglishOnly + mPredictionOn (spec §2 table)
        switch textDocumentProxy.keyboardType ?? .default {
        case .numberPad, .decimalPad, .asciiCapableNumberPad:
            mEnglishOnly = true; mPredictionOn = true
        case .phonePad:
            mEnglishOnly = true; mPredictionOn = true
        case .emailAddress, .URL:
            mEnglishOnly = true; mPredictionOn = false
        default:
            // Restore persisted language mode if enabled (spec §15)
            if mPersistentLanguageMode {
                mEnglishOnly = sharedDefaults?.bool(forKey: "persisted_english_mode") ?? false
            } else {
                mEnglishOnly = false
            }
            mPredictionOn = true
        }

        let layoutName = mEnglishOnly ? "lime_abc" : "lime_phonetic"
        if let newLayout = LayoutLoader.load(layoutName), newLayout.id != currentLayout.id {
            currentLayout = newLayout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }

        clearComposing(force: false)
        tempEnglishWord = ""
    }

    // MARK: - Database Setup

    private func setupDatabase() {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.net.toload.limeime")
        else { return }

        let dbPath = containerURL.appendingPathComponent("lime.db").path

        // Copy bundled lime.db to App Group container on first launch
        if !FileManager.default.fileExists(atPath: dbPath) {
            copyBundledDB(to: dbPath)
        }

        guard let limeDB = try? LimeDB(path: dbPath) else { return }
        db = limeDB
        searchServer = SearchServer(db: limeDB)

        // Load settings from shared UserDefaults (spec §15)
        loadSettings()

        // Auto-import phonetic (and seedDefaultIMs) FIRST so getAllImConfigs() is populated.
        importPhoneticIfNeeded(db: limeDB, containerURL: containerURL)

        // Load activated IM list from keyboard_state preference
        let allIMs = (try? limeDB.getAllImConfigs()) ?? []
        let kbState = sharedDefaults?.string(forKey: "keyboard_state") ?? ""
        if kbState.isEmpty {
            activatedIMs = allIMs.filter { $0.enabled }
        } else {
            let enabled = Set(kbState.components(separatedBy: ","))
            activatedIMs = allIMs.filter { enabled.contains($0.tableNick) }
        }
        if activatedIMs.isEmpty {
            activatedIMs = allIMs.filter { $0.enabled }
        }

        // Final fallback: im table still empty (e.g. no DB yet) — scan tables with data directly.
        if activatedIMs.isEmpty {
            activatedIMs = buildFallbackIMList(db: limeDB)
        }

        // Set initial active IM
        if let first = activatedIMs.first {
            activeIM      = first.tableNick.isEmpty ? "phonetic" : first.tableNick
            activeIMIndex = 0
        } else if let firstIM = allIMs.first(where: { $0.enabled }) {
            activeIM = firstIM.tableNick.isEmpty ? "phonetic" : firstIM.tableNick
        }
        let caps = imCapabilities(for: activeIM, db: limeDB)
        searchServer?.setTableName(activeIM, hasNumberMapping: caps.hasNumber,
                                   hasSymbolMapping: caps.hasSymbol)
        searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
        searchServer?.sortSuggestions = sortSuggestions
        applyFeedbackSettings()
    }

    /// Build an activatedIMs list directly from IM data tables that have rows.
    /// Used as a fallback when the im table is empty (first launch before any import).
    private func buildFallbackIMList(db: LimeDB) -> [ImConfig] {
        let candidates: [(nick: String, label: String, keyboard: String)] = [
            ("phonetic", "注音",     "lime_phonetic"),
            ("dayi",     "大易",     "lime_dayi"),
            ("cj",       "倉頡",     "lime_cj"),
            ("cj5",      "倉頡五代", "lime_cj"),
            ("array",    "行列",     "lime_array"),
            ("array10",  "行列十",   "lime_array"),
            ("wb",       "筆順五碼", "lime_wb"),
            ("hs",       "許氏",     "lime_hs"),
            ("ez",       "輕鬆",     "lime_ez"),
            ("scj",      "速成",     "lime_cj"),
            ("ecj",      "易倉頡",   "lime_cj"),
        ]
        var idx: Int64 = 0
        return candidates.compactMap { (nick, label, keyboard) in
            guard db.tableHasData(nick) else { return nil }
            defer { idx += 1 }
            return ImConfig(id: idx, imName: nick, tableNick: nick, label: label,
                            keyboardId: keyboard, keyboardLandscapeId: keyboard,
                            enabled: true, sortOrder: Int(idx))
        }
    }

    /// Shared UserDefaults for reading settings written by the container app.
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: "group.net.toload.limeime")
    }

    /// Load all user preferences from shared UserDefaults (spec §15).
    private func loadSettings() {
        let d = sharedDefaults
        hanConvertOption     = d?.integer(forKey: "hanConvertOption")     ?? 0
        autoChineseSymbol    = d?.bool(forKey: "autoChineseSymbol")       ?? false
        sortSuggestions      = d?.bool(forKey: "sortSuggestions")         ?? false
        smartChineseInput    = d?.bool(forKey: "smartChineseInput")       ?? false
        learnPhrase          = d?.bool(forKey: "learnPhrase")             ?? true
        englishPredictionOn  = d?.bool(forKey: "englishPrediction")       ?? true
        selkeyOption         = d?.integer(forKey: "selkeyOption")         ?? 0
        hasVibration         = d?.bool(forKey: "hasVibration")            ?? false
        hasSound             = d?.bool(forKey: "hasSound")                ?? false
        mPersistentLanguageMode = d?.bool(forKey: "persistentLanguageMode") ?? false
        phoneticKeyboardType = d?.string(forKey: "phonetic_keyboard_type") ?? "phonetic"
        autoCommit           = d?.integer(forKey: "auto_commit")          ?? 0
    }

    /// Push feedback settings to KeyboardView (spec §15).
    private func applyFeedbackSettings() {
        keyboardView?.feedbackVibration = hasVibration
        keyboardView?.feedbackSound     = hasSound
    }

    private func copyBundledDB(to destPath: String) {
        guard let srcURL = Bundle.main.url(forResource: "lime", withExtension: "db") else { return }
        try? FileManager.default.copyItem(at: srcURL, to: URL(fileURLWithPath: destPath))
    }

    private func importPhoneticIfNeeded(db: LimeDB, containerURL: URL) {
        // Use tableHasData (not tableExists): bundled lime.db pre-creates all tables as empty shells.
        // Only import if the phonetic table genuinely has no data.
        guard !db.tableHasData("phonetic") else { return }
        guard let srcURL = Bundle.main.url(forResource: "phonetic", withExtension: "db") else { return }
        try? db.importFromAttachedDB(sourcePath: srcURL.path, tableName: "phonetic")
        try? db.seedDefaultIMs()
        searchServer?.clearAllCaches()
    }

    /// Determine whether an IM's code table uses digit and/or symbol characters.
    /// The phonetic family (and similar tone-based IMs) use digits 0-9 for initials/tones
    /// and symbols like ;, /, ., - for finals. Non-phonetic IMs (Cangjie, Array, etc.)
    /// typically use only letters.
    private func imCapabilities(for imCode: String,
                                db: LimeDB) -> (hasNumber: Bool, hasSymbol: Bool) {
        let lc = imCode.lowercased()
        // Phonetic family: standard phonetic, ETEN 26/41, HSU, Dayi — all use digits+symbols
        let phoneticFamily = ["phonetic", "et26", "et_41", "eten", "hsu", "hs", "dayi", "ez"]
        if phoneticFamily.contains(where: { lc.hasPrefix($0) }) {
            return (hasNumber: true, hasSymbol: true)
        }
        // Stroke5 (wb) uses only letters
        // Cangjie (cj), Array, EZ, etc. — detect from DB
        return db.detectIMCapabilities(tableName: imCode)
    }

    // MARK: - UI Setup

    private func setupKeyboardUI() {
        view.backgroundColor = UIColor.systemGray4

        // Candidate bar
        candidateBar = CandidateBarView()
        candidateBar.delegate = self
        candidateBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(candidateBar)

        // Keyboard view
        keyboardView = KeyboardView(layout: currentLayout)
        keyboardView.delegate = self
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboardView)

        NSLayoutConstraint.activate([
            candidateBar.topAnchor.constraint(equalTo: view.topAnchor),
            candidateBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            candidateBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            candidateBar.heightAnchor.constraint(equalToConstant: candidateBarHeight),

            keyboardView.topAnchor.constraint(equalTo: candidateBar.bottomAnchor),
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        // Space-key gestures (swipe + long-press) are now added directly in
        // KeyboardView.makeKeyButton so they survive every setLayout() call.
    }

    private func applyHeight() {
        let rowCount = CGFloat(currentLayout.rows.count)
        let totalHeight = candidateBarHeight + rowCount * keyRowHeight
        if let existing = keyboardHeightConstraint {
            existing.constant = totalHeight
        } else {
            let c = view.heightAnchor.constraint(equalToConstant: totalHeight)
            c.priority = UILayoutPriority(rawValue: 999)
            c.isActive = true
            keyboardHeightConstraint = c
        }
    }

    // MARK: - Key Event Dispatch (spec §4 onKey)

    private func onKey(primaryCode: Int) {
        // CapsLock pre-processing: lowercase → uppercase (spec §4)
        var code = primaryCode
        if mCapsLock && code >= 97 && code <= 122 { code -= 32 }

        switch code {
        case LimeKeyCode.delete.rawValue:      handleBackspace()
        case LimeKeyCode.shift.rawValue:       handleShift()
        case LimeKeyCode.done.rawValue:        handleClose()
        case LimeKeyCode.globe.rawValue:       advanceToNextInputMode()
        case LimeKeyCode.switchToEnglish.rawValue: switchChiEng(toEnglish: true)
        case LimeKeyCode.switchToIM.rawValue:  switchChiEng(toEnglish: false)
        case LimeKeyCode.switchToSymbol.rawValue:     switchToSymbol()
        case LimeKeyCode.switchSymbolKeyboard.rawValue: cycleSymbolPage()
        case LimeKeyCode.nextIM.rawValue:      switchToNextActivatedIM(forward: true)
        case LimeKeyCode.prevIM.rawValue:      switchToNextActivatedIM(forward: false)
        case LimeKeyCode.space.rawValue:       handleEnterOrSpace(isEnter: false)
        case LimeKeyCode.enter.rawValue:       handleEnterOrSpace(isEnter: true)
        default:
            // Selkey routing: if candidates shown and key matches a selection key (spec §6)
            if hasCandidatesShown && !mEnglishOnly && tryPickBySelkey(code: code) { break }
            handleCharacter(code)
            // Auto-commit check (spec §5)
            if autoCommit > 0, !mEnglishOnly,
               mComposing.count == autoCommit,
               searchServer?.isPhoneticTable == true {
                commitTyped()
            }
        }
    }

    // MARK: - Space / Enter Handling (spec §4)

    private func handleEnterOrSpace(isEnter: Bool) {
        let isPhonetic = searchServer?.isPhoneticTable ?? false

        // Determine whether to pick the highlighted candidate (spec §4 conditions)
        let shouldPick: Bool
        if isEnter {
            shouldPick = hasCandidatesShown
        } else if mEnglishOnly {
            shouldPick = false
        } else if !isPhonetic {
            shouldPick = hasCandidatesShown
        } else {
            // Phonetic: pick if composing ends with space (tone entered) or composing is empty
            shouldPick = hasCandidatesShown && (mComposing.hasSuffix(" ") || mComposing.isEmpty)
        }

        if shouldPick {
            let picked = pickHighlightedCandidate()
            if !picked && mComposing.isEmpty {
                clearSuggestions()
                textDocumentProxy.insertText(isEnter ? "\n" : " ")
            }
        } else {
            // Not picking — for phonetic space is a tone marker
            if !isEnter, !mEnglishOnly, isPhonetic, !mComposing.isEmpty {
                handleCharacter(LimeKeyCode.space.rawValue)  // space as tone mark
            } else {
                textDocumentProxy.insertText(isEnter ? "\n" : " ")
            }
        }
    }

    // MARK: - Character Handling (spec §5 handleCharacter / Character Acceptance Rules)

    private func handleCharacter(_ code: Int) {
        guard code > 0, let scalar = Unicode.Scalar(code) else { return }
        let char      = Character(scalar)
        let charStr   = String(char)

        if mEnglishOnly {
            handleEnglishCharacter(code: code, char: char)
            return
        }

        let hasSymbol  = searchServer?.hasSymbolMapping ?? false
        let hasNumber  = searchServer?.hasNumberMapping ?? false
        let isPhonetic = searchServer?.isPhoneticTable ?? false
        let isLetter   = (code >= 97 && code <= 122) || (code >= 65 && code <= 90)
        let isDigit    = code >= 48 && code <= 57
        let isSpace    = code == 32
        let isComma    = code == 44
        let isPeriod   = code == 46

        // Acceptance rules (spec §5 table)
        let accepted: Bool
        if !hasSymbol && !hasNumber {
            accepted = isLetter || (isPhonetic && isSpace) || isComma || isPeriod
        } else if !hasSymbol && hasNumber {
            accepted = isLetter || isDigit
        } else if hasSymbol && !hasNumber {
            let isSymbol = !isLetter && !isDigit && code > 32
            accepted = isLetter || isSymbol || (isPhonetic && isSpace)
        } else {
            let isSymbol = !isLetter && !isDigit && code > 32
            accepted = isLetter || isDigit || isSymbol || (isPhonetic && isSpace)
        }

        if accepted {
            let insertChar = (isShiftOn && !isSpace) ? charStr.uppercased() : charStr

            // Stroke5 (WB) 5-character limit: discard the 6th character (spec §5)
            if searchServer?.isWBTable == true && mComposing.count >= 5 { return }

            // Append to composing buffer first, then insert (so textDidChange check passes)
            mComposing += insertChar
            // iOS composing simulation: insert char inline (spec §12)
            isSelfUpdate = true
            textDocumentProxy.insertText(insertChar)
            isSelfUpdate = false
            composingLength += 1
            // WB: truncate query code to 5 characters
            updateCandidates()
        } else {
            // Not accepted: commit current candidate, then send char directly (spec §5)
            _ = pickHighlightedCandidate()
            let insertChar = isShiftOn ? charStr.uppercased() : charStr
            isSelfUpdate = true
            textDocumentProxy.insertText(insertChar)
            isSelfUpdate = false
            finishComposing()
        }

        if isShiftOn && code != LimeKeyCode.shift.rawValue { setShift(false) }
    }

    // MARK: - English Character Handling (spec §5 English Mode)

    private func handleEnglishCharacter(code: Int, char: Character) {
        let charStr    = String(char)
        let insertChar = isShiftOn ? charStr.uppercased() : charStr

        if char.isLetter {
            tempEnglishWord += insertChar
        } else {
            resetTempEnglishWord()
        }
        isSelfUpdate = true
        textDocumentProxy.insertText(insertChar)
        isSelfUpdate = false
        updateEnglishPrediction()
        if isShiftOn { setShift(false) }
    }

    // MARK: - Backspace Handling (spec §5 handleBackspace — 6 cases)

    private func handleBackspace() {
        if mComposing.count > 1 {
            // Case 1: composing > 1 → remove last char from composing, then delete from document
            // (update mComposing BEFORE deleteBackward so textDidChange check passes)
            mComposing.removeLast()
            isSelfUpdate = true
            textDocumentProxy.deleteBackward()
            isSelfUpdate = false
            composingLength -= 1
            updateCandidates()
            candidateBar.setComposingCode(keyname(mComposing))

        } else if mComposing.count == 1 {
            // Case 2: composing == 1 → clear composing and force-remove from document (spec §5)
            clearComposing(force: true)

        } else if hasCandidatesShown && hasChineseSymbolCandidatesShown {
            // Case 4: Chinese punctuation list shown → hide it without deleting (spec §11)
            hasChineseSymbolCandidatesShown = false
            hasCandidatesShown  = false
            selectedCandidate   = nil
            mCandidateList      = []
            candidateBar.setCandidates([])

        } else if hasCandidatesShown {
            // Case 3: composing empty, candidates shown → clear (spec §5 clearComposing(false))
            clearComposing(force: false)

        } else if mEnglishOnly && !tempEnglishWord.isEmpty {
            // Case 5: English prediction word → delete last char and re-query
            tempEnglishWord.removeLast()
            isSelfUpdate = true
            textDocumentProxy.deleteBackward()
            isSelfUpdate = false
            updateEnglishPrediction()

        } else {
            // Case 6: no composing, no candidates → pass delete to text field
            textDocumentProxy.deleteBackward()
        }
    }

    // MARK: - Shift / CapsLock (spec §4 handleShift)

    private func handleShift() {
        if mCapsLock {
            mCapsLock = false; setShift(false)
        } else if isShiftOn {
            mCapsLock = true   // second tap → caps lock
        } else {
            setShift(true)
        }
    }

    private func setShift(_ on: Bool) {
        isShiftOn = on
        keyboardView.setShift(on)
    }

    private func handleClose() {
        clearComposing(force: false)
        dismissKeyboard()
    }

    private func updateShiftForAutoCap() {
        // Auto-capitalization only applies in English mode.
        // In Chinese/phonetic mode the composing codes are always lowercase —
        // auto-shifting them to uppercase breaks all DB lookups.
        guard mEnglishOnly else { return }
        guard !isShiftOn, !mCapsLock else { return }
        // iOS provides autocapitalizationType directly (spec §2 iOS note)
        guard let capType = textDocumentProxy.autocapitalizationType,
              capType == .sentences || capType == .allCharacters || capType == .words else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let atStart = before.isEmpty || before.hasSuffix(". ") || before.hasSuffix("! ") || before.hasSuffix("? ")
        if atStart { setShift(true) }
    }

    // MARK: - iOS Composing Simulation (spec §12)

    /// Clear composing state. `force=true` removes inline chars from the document.
    private func clearComposing(force: Bool) {
        if force {
            isSelfUpdate = true
            for _ in 0..<composingLength { textDocumentProxy.deleteBackward() }
            isSelfUpdate = false
        }
        mComposing       = ""
        composingLength  = 0
        selectedCandidate = nil
        mCandidateList   = []
        hasCandidatesShown = false
        hasChineseSymbolCandidatesShown = false
        candidateBar.setComposingCode("")
        candidateBar.setCandidates([])
    }

    /// Cancel composing without touching the document (cursor moved externally).
    private func cancelComposing() {
        mComposing       = ""
        composingLength  = 0
        selectedCandidate = nil
        mCandidateList   = []
        hasCandidatesShown = false
        hasChineseSymbolCandidatesShown = false
        candidateBar.setComposingCode("")
        candidateBar.setCandidates([])
    }

    /// Reset composing tracking after text has been committed or cleared.
    private func finishComposing() {
        mComposing       = ""
        composingLength  = 0
        selectedCandidate = nil
        hasCandidatesShown = false
    }

    // MARK: - Candidate Flow (spec §6 updateCandidates)

    private func updateCandidates() {
        guard mPredictionOn, let ss = searchServer, !mComposing.isEmpty else {
            clearSuggestions(); return
        }
        // On composing restart (length == 1): clear runtime suggestion context (spec §6)
        if mComposing.count == 1 && smartChineseInput {
            ss.clearSuggestionContext()
        }
        // WB/Stroke5: query with at most 5 characters (spec §5)
        let code = ss.isWBTable ? String(mComposing.prefix(5)) : mComposing
        currentSearchID &+= 1
        let sid = currentSearchID

        let doRuntime = smartChineseInput
        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            var results = ss.getMappingByCode(code, isSoftKeyboard: true)
            if !results.isEmpty {
                // Emoji injection at position 3 (spec §6 step 5)
                let emojiType = LimeDB.EMOJI_TW  // default: Traditional Chinese emoji set
                results = ss.injectEmoji(into: results, code: code, type: emojiType, insertAt: 3)
                if doRuntime {
                    results = ss.makeRunTimeSuggestion(code: code, currentList: results)
                }
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self, self.currentSearchID == sid else { return }
                results.isEmpty ? self.clearSuggestions() : self.setSuggestions(results)
            }
        }
    }

    /// Set candidate list and default selection (spec §6 Default Candidate Selection).
    private func setSuggestions(_ list: [Mapping]) {
        mCandidateList     = list
        hasCandidatesShown = !list.isEmpty

        // If index 1 is an exact match, select it (skips the composing echo at index 0)
        if list.count > 1 && list[1].isExactMatchToCodeRecord {
            selectedCandidate = list[1]
        } else {
            selectedCandidate = list.first(where: { !$0.isComposingCodeRecord })
        }

        // Display keyname in composing bar (spec §6 step 7)
        candidateBar.setComposingCode(keyname(mComposing))
        showCandidates(list.filter { !$0.isComposingCodeRecord })
    }

    /// Show candidates in the bar, applying current selkey config (spec §6).
    private func showCandidates(_ list: [Mapping]) {
        let selkey = searchServer?.getSelkey() ?? "1234567890"
        candidateBar.setSelkeyConfig(selkeys: selkey, option: selkeyOption)
        candidateBar.setCandidates(list)
    }

    private func clearSuggestions() {
        // Auto Chinese Symbol: when candidates disappear in Chinese mode, show punctuation (spec §11)
        if autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown {
            let punctuation = KeyboardViewController.chinesePunctuationMappings()
            if !punctuation.isEmpty {
                mCandidateList              = punctuation
                hasCandidatesShown          = true
                hasChineseSymbolCandidatesShown = true
                selectedCandidate           = nil
                showCandidates(punctuation)
                return
            }
        }
        hasChineseSymbolCandidatesShown = false
        mCandidateList     = []
        hasCandidatesShown = false
        selectedCandidate  = nil
        candidateBar.setCandidates([])
    }

    private func keyname(_ code: String) -> String {
        searchServer?.keyToKeyname(code) ?? code
    }

    // MARK: - Candidate Selection (spec §8)

    /// Pick the highlighted candidate. Returns true if a candidate was committed.
    @discardableResult
    private func pickHighlightedCandidate() -> Bool {
        guard let candidate = selectedCandidate else { return false }
        pickCandidateManually(candidate)
        return true
    }

    private func pickCandidateManually(_ candidate: Mapping) {
        selectedCandidate = candidate
        commitTyped()
        updateRelatedPhrase()
    }

    // MARK: - Commit Flow (spec §8 commitTyped)

    private func commitTyped() {
        guard let candidate = selectedCandidate else { return }

        // Unicode surrogate / emoji: commit directly and force-clear (spec §8)
        let isEmoji = candidate.isEmojiRecord || containsEmojiSurrogatePair(candidate.word)

        // iOS composing simulation: delete composing chars, then insert word (spec §12 step 5)
        // isSelfUpdate suppresses textDidChange composing-integrity check during our own writes
        isSelfUpdate = true
        for _ in 0..<composingLength { textDocumentProxy.deleteBackward() }

        // Han conversion: iOS uses CFStringTransform (spec §8 step 3)
        var wordToCommit = candidate.word
        if hanConvertOption == 1 {
            // Traditional → Simplified
            let mutable = NSMutableString(string: wordToCommit)
            CFStringTransform(mutable, nil, "Hant-Hans" as CFString, false)
            wordToCommit = mutable as String
        } else if hanConvertOption == 2 {
            // Simplified → Traditional
            let mutable = NSMutableString(string: wordToCommit)
            CFStringTransform(mutable, nil, "Hans-Hant" as CFString, false)
            wordToCommit = mutable as String
        }

        // Commit the word (spec §8 step 4)
        textDocumentProxy.insertText(wordToCommit)
        isSelfUpdate = false

        // Continuous typing check (spec §8 step 6)
        let codeLen = searchServer?.getRealCodeLength(
            mapping: candidate, composing: mComposing) ?? min(candidate.code.count, mComposing.count)

        // Emoji or WB/punctuation: force-clear after commit (spec §8)
        let forceClearAfterCommit = isEmoji
            || candidate.isChinesePunctuationRecord
            || (searchServer?.isWBTable == true)

        if mComposing.count > candidate.code.count && !forceClearAfterCommit {
            // Remaining composing code → re-establish inline and re-query
            var remaining = String(mComposing.dropFirst(min(codeLen, mComposing.count)))
            if remaining.hasPrefix(" ") { remaining = String(remaining.dropFirst()) }
            mComposing      = remaining
            composingLength = remaining.count
            if !remaining.isEmpty {
                isSelfUpdate = true
                textDocumentProxy.insertText(remaining)
                isSelfUpdate = false
                updateCandidates()
                candidateBar.setComposingCode(keyname(remaining))
                // Buffer for LD learning (spec §5 Continuous Typing)
                if learnPhrase { searchServer?.addLDPhrase(candidate, ending: false) }
                // Track LD composing buffer (spec §5)
                if LDComposingBuffer.isEmpty { LDComposingBuffer = candidate.word }
                else { LDComposingBuffer += candidate.word }
                return
            }
        }

        // Post-commit (spec §8 step 7)
        committedCandidate = candidate
        if forceClearAfterCommit {
            clearComposing(force: true)
            LDComposingBuffer = ""
            if learnPhrase { searchServer?.addLDPhrase(nil, ending: true) }
        } else {
            finishComposing()
            // Signal LD learning end if buffer had accumulated
            if !LDComposingBuffer.isEmpty {
                if learnPhrase { searchServer?.addLDPhrase(candidate, ending: true) }
                LDComposingBuffer = ""
            }
        }
        if learnPhrase { searchServer?.learnRelatedPhraseAndUpdateScore(candidate) }
        // Record committed candidate + its code for runtime phrase suggestion cross-check (spec §6)
        if smartChineseInput { searchServer?.addToSuggestionContext(candidate, code: candidate.code) }

        // Reverse lookup notification — show code list briefly in composing bar (spec §8, §13)
        if let ss = searchServer {
            let word = candidate.word
            DispatchQueue.global(qos: .background).async { [weak self] in
                guard let result = ss.getCodeListStringFromWord(word), !result.isEmpty else { return }
                DispatchQueue.main.async { self?.showToast(result) }
            }
        }
    }

    // MARK: - Related Phrase Display (spec §8 updateRelatedPhrase)

    private func updateRelatedPhrase() {
        guard let committed = committedCandidate,
              !committed.word.isEmpty,
              !committed.isEmojiRecord,
              !committed.isChinesePunctuationRecord,
              let ss = searchServer else { return }

        let word = committed.word
        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            let related = ss.getRelatedByWord(word)
            DispatchQueue.main.async { [weak self] in
                guard let self = self, !related.isEmpty else { return }
                self.mCandidateList    = related
                self.hasCandidatesShown = true
                self.selectedCandidate = related.first
                self.showCandidates(related)
            }
        }
    }

    // MARK: - English Prediction (spec §7 — iOS: UITextChecker)

    private func updateEnglishPrediction() {
        guard englishPredictionOn else { return }
        guard !tempEnglishWord.isEmpty else { clearSuggestions(); return }
        let word = tempEnglishWord
        // Validate cursor context (spec §7) — read documentContext on main thread
        let beforeCursor = textDocumentProxy.documentContextBeforeInput ?? ""
        guard beforeCursor.hasSuffix(word) else { return }

        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            guard let self = self else { return }
            let range = NSRange(location: 0, length: (word as NSString).length)
            let completions = self.textChecker.completions(
                forPartialWordRange: range, in: word, language: "en_US") ?? []
            var mappings: [Mapping] = completions.prefix(20).map { suggestion in
                Mapping(id: 0, code: word, word: suggestion,
                        score: 0, baseScore: 0, recordType: Mapping.RecordType.englishSuggestion)
            }
            // Emoji injection for English predictions (spec §6 step 5, §7)
            if !mappings.isEmpty, let ss = self.searchServer {
                mappings = ss.injectEmoji(into: mappings, code: word, type: LimeDB.EMOJI_EN, insertAt: 3)
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                if mappings.isEmpty { self.clearSuggestions() }
                else {
                    self.mCandidateList    = mappings
                    self.hasCandidatesShown = true
                    self.selectedCandidate = mappings.first
                    self.showCandidates(mappings)
                }
            }
        }
    }

    private func resetTempEnglishWord() { tempEnglishWord = "" }

    /// Commit an English suggestion: insert only the untyped suffix + space (spec §8 Path 3).
    private func commitEnglishSuggestion(_ word: String) {
        let suffix = word.count > tempEnglishWord.count
            ? String(word.dropFirst(tempEnglishWord.count)) : ""
        textDocumentProxy.insertText(suffix + " ")
        resetTempEnglishWord()
        clearSuggestions()
    }

    // MARK: - Mode Switching (spec §10)

    /// Toggle Chinese ↔ English mode (spec §10 switchChiEng).
    private func switchChiEng(toEnglish: Bool) {
        if isSymbolMode { exitSymbolMode() }
        clearComposing(force: false)
        mEnglishOnly = toEnglish
        // Persist language mode if setting is enabled (spec §15)
        if mPersistentLanguageMode {
            sharedDefaults?.set(toEnglish, forKey: "persisted_english_mode")
        }
        clearSuggestions()
        resetTempEnglishWord()
        let layoutName = toEnglish ? "lime_abc" : "lime_phonetic"
        if let loaded = LayoutLoader.load(layoutName) { currentLayout = loaded }
        keyboardView.setLayout(currentLayout)
        applyHeight()
    }

    /// Cycle to next/previous LIME-internal IM (spec §10 switchToNextActivatedIM).
    private func switchToNextActivatedIM(forward: Bool) {
        guard !activatedIMs.isEmpty, let ss = searchServer else { return }
        let count = activatedIMs.count
        activeIMIndex = forward
            ? (activeIMIndex + 1) % count
            : (activeIMIndex - 1 + count) % count
        let im = activatedIMs[activeIMIndex]
        activeIM = im.tableNick.isEmpty ? "phonetic" : im.tableNick

        // Clear composing and candidates before switching
        clearComposing(force: false)
        LDComposingBuffer = ""

        // Reconfigure SearchServer for the new IM
        if let db = self.db {
            let caps = imCapabilities(for: activeIM, db: db)
            ss.setTableName(activeIM, hasNumberMapping: caps.hasNumber, hasSymbolMapping: caps.hasSymbol)
        } else {
            ss.setTableName(activeIM)
        }
        ss.setPhoneticKeyboardType(phoneticKeyboardType)

        // Update keyboard layout to match the new IM if available
        let preferredLayout = "lime_\(activeIM)"
        if let newLayout = LayoutLoader.load(preferredLayout), newLayout.id != currentLayout.id {
            currentLayout = newLayout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }
    }

    // MARK: - Symbol Keyboard (spec §10)

    /// Enter symbol keyboard mode (spec §10 switchToSymbol).
    private func switchToSymbol() {
        guard !isSymbolMode else { cycleSymbolPage(); return }
        isSymbolMode    = true
        symbolPageIndex = 0
        preSymbolLayout = currentLayout
        clearComposing(force: false)
        loadSymbolLayout(page: 0)
    }

    /// Cycle through symbol keyboard pages (spec §10 KEYCODE_SWITCH_SYMBOL_KEYBOARD).
    private func cycleSymbolPage() {
        guard isSymbolMode else { switchToSymbol(); return }
        symbolPageIndex = (symbolPageIndex + 1) % symbolLayouts.count
        loadSymbolLayout(page: symbolPageIndex)
    }

    /// Load a symbol keyboard layout page.
    private func loadSymbolLayout(page: Int) {
        let id = symbolLayouts[page]
        let layout = LayoutLoader.load(id) ?? currentLayout
        currentLayout = layout
        keyboardView?.setLayout(layout)
        applyHeight()
    }

    /// Exit symbol mode and restore the previous keyboard layout.
    private func exitSymbolMode() {
        guard isSymbolMode else { return }
        isSymbolMode = false
        let restore = preSymbolLayout ?? currentLayout
        currentLayout = restore
        keyboardView?.setLayout(restore)
        applyHeight()
    }

    // MARK: - Globe Key Visibility (spec §10)

    /// The keyboard key (code -3) is always visible — it is the primary dismiss affordance
    /// and doubles as the long-press globe menu entry point (spec §10).
    /// Only legacy code-200 globe keys (hardcoded fallback layouts) are conditionally shown.
    private func updateGlobeKeyVisibility() {
        keyboardView?.setGlobeKeyVisible(needsInputModeSwitchKey)
    }

    // MARK: - Selkey (spec §6)

    /// Try to pick a candidate by selkey index. Returns true if a candidate was picked.
    @discardableResult
    private func tryPickBySelkey(code: Int) -> Bool {
        guard selkeyOption > 0 else { return false }
        let selkey = searchServer?.getSelkey() ?? "1234567890"
        guard let scalar = Unicode.Scalar(code),
              let idx = selkey.firstIndex(of: Character(scalar)) else { return false }
        let offset = selkey.distance(from: selkey.startIndex, to: idx)
        // Filter out composing-code echo to get real candidates
        let realCandidates = mCandidateList.filter { !$0.isComposingCodeRecord }
        guard offset < realCandidates.count else { return false }
        pickCandidateManually(realCandidates[offset])
        return true
    }

    // MARK: - Emoji / Surrogate Pair Detection (spec §8)

    /// Returns true if the string contains a Unicode surrogate pair (emoji or extended CJK).
    private func containsEmojiSurrogatePair(_ word: String) -> Bool {
        word.unicodeScalars.contains { $0.value > 0xFFFF }
    }

    // MARK: - Toast Notification (spec §8, §13)

    private var toastTimer: Timer?

    /// Briefly display a message in the composing-code label (spec §8 reverse-lookup notification).
    private func showToast(_ message: String) {
        toastTimer?.invalidate()
        candidateBar.setComposingCode(message)
        toastTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: false) { [weak self] _ in
            self?.candidateBar.setComposingCode("")
        }
    }

    // MARK: - Chinese Punctuation List (spec §11)

    /// Standard Chinese punctuation set shown after a commit when autoChineseSymbol is on.
    static func chinesePunctuationMappings() -> [Mapping] {
        let symbols = ["，", "。", "、", "；", "：", "？", "！",
                       "「", "」", "『", "』", "【", "】", "〔", "〕",
                       "（", "）", "《", "》", "〈", "〉",
                       "…", "——", "～", "·", "※",
                       "\u{201C}", "\u{201D}", "\u{2018}", "\u{2019}"]
        return symbols.map {
            Mapping(id: 0, code: "", word: $0, score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.chinesePunctuation)
        }
    }

    // MARK: - Space Key Gestures (spec §10)
    // Space-key gestures (swipe left/right and long-press) are wired directly in
    // KeyboardView.makeKeyButton — no setup needed here.
}

// MARK: - KeyboardViewDelegate
extension KeyboardViewController: KeyboardViewDelegate {

    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef) {
        onKey(primaryCode: keyDef.code)
    }

    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef) {
        // Keyboard key (code -3) and legacy globe key (code -200): show iOS+LIME options menu (spec §10).
        if keyDef.code == LimeKeyCode.done.rawValue || keyDef.code == LimeKeyCode.globe.rawValue {
            showGlobeMenu(from: view)
        }
        // Space key: show LIME-internal IM picker only (spec §10: NOT iOS keyboard switch)
        else if keyDef.code == LimeKeyCode.space.rawValue {
            showLimeIMPicker()
        }
    }

    // MARK: - IM Switching Helper

    /// Switch to a LIME-internal IM by absolute index in activatedIMs.
    private func switchIM(toIndex i: Int) {
        guard i < activatedIMs.count else { return }
        let im = activatedIMs[i]
        activeIMIndex = i
        activeIM = im.tableNick.isEmpty ? "phonetic" : im.tableNick
        clearComposing(force: false)
        if let db = self.db {
            let caps = imCapabilities(for: activeIM, db: db)
            searchServer?.setTableName(activeIM, hasNumberMapping: caps.hasNumber,
                                       hasSymbolMapping: caps.hasSymbol)
        } else {
            searchServer?.setTableName(activeIM)
        }
        searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
        if let layout = LayoutLoader.load("lime_\(activeIM)"), layout.id != currentLayout.id {
            currentLayout = layout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }
    }

    /// Long-press on keyboard key: options menu with iOS keyboard switch (spec §10).
    private func showGlobeMenu(from sourceView: UIView) {
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        // iOS system keyboard switch (spec §10: keyboard key long-press → switch to other system keyboards)
        alert.addAction(UIAlertAction(title: NSLocalizedString("切換輸入法", comment: "Switch Input Mode"),
                                      style: .default) { [weak self] _ in
            self?.advanceToNextInputMode()
        })
        // LIME-internal IMs
        for (i, im) in activatedIMs.enumerated() {
            let label = im.label.isEmpty ? im.tableNick : im.label
            alert.addAction(UIAlertAction(title: label, style: .default) { [weak self] _ in
                self?.switchIM(toIndex: i)
            })
        }
        alert.addAction(UIAlertAction(title: NSLocalizedString("取消", comment: "Cancel"),
                                      style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds
        }
        present(alert, animated: true)
    }

    /// Long-press on space key: LIME-internal IM picker only (spec §10: no iOS keyboard switch).
    private func showLimeIMPicker() {
        guard !activatedIMs.isEmpty else { return }
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        for (i, im) in activatedIMs.enumerated() {
            let label = im.label.isEmpty ? im.tableNick : im.label
            alert.addAction(UIAlertAction(title: label, style: .default) { [weak self] _ in
                self?.switchIM(toIndex: i)
            })
        }
        alert.addAction(UIAlertAction(title: NSLocalizedString("取消", comment: "Cancel"),
                                      style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = keyboardView
            popover.sourceRect = keyboardView?.bounds ?? .zero
        }
        present(alert, animated: true)
    }
}

// MARK: - CandidateBarViewDelegate
extension KeyboardViewController: CandidateBarViewDelegate {

    func candidateBarView(_ view: CandidateBarView, didSelect mapping: Mapping) {
        if mapping.isEnglishSuggestionRecord {
            commitEnglishSuggestion(mapping.word)
        } else {
            selectedCandidate = mapping
            commitTyped()
            updateRelatedPhrase()
        }
    }

    func candidateBarViewDidRequestMore(_ view: CandidateBarView) {
        guard let ss = searchServer, !mComposing.isEmpty else { return }
        let more = ss.getMappingByCode(mComposing, getAllRecords: true)
        mCandidateList = more
        hasCandidatesShown = !more.isEmpty
        showCandidates(more.filter { !$0.isComposingCodeRecord })
    }
}
