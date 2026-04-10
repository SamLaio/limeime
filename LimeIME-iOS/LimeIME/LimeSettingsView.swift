import SwiftUI
import UniformTypeIdentifiers

// Phase 4: Container App settings UI.
// Hosted via UIHostingController from MainViewController.

// MARK: - Root view with tab bar

struct LimeSettingsView: View {
    var body: some View {
        TabView {
            SetupTab()
                .tabItem { Label("設定", systemImage: "gearshape") }
            ManageIMTab()
                .tabItem { Label("輸入法", systemImage: "list.bullet") }
            IMStoreView()
                .tabItem { Label("商店", systemImage: "cloud.and.arrow.down") }
            ImportTab()
                .tabItem { Label("匯入", systemImage: "square.and.arrow.down") }
            SettingsTab()
                .tabItem { Label("偏好", systemImage: "slider.horizontal.3") }
        }
        .onAppear { initDatabase() }
    }

    private func initDatabase() {
        DispatchQueue.global(qos: .userInitiated).async {
            guard let db = openDB() else { return }
            try? db.seedDefaultIMs()
        }
    }
}

// MARK: - Setup Tab

struct SetupTab: View {
    @State private var isKeyboardEnabled = false

    var body: some View {
        NavigationView {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("啟用鍵盤", systemImage: isKeyboardEnabled ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(isKeyboardEnabled ? .green : .secondary)
                        Text("前往「設定 → 一般 → 鍵盤 → 鍵盤 → 新增鍵盤」，選擇 LimeIME。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                } header: { Text("步驟 1") }

                Section {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("允許完整取用", systemImage: "key")
                        Text("在剛才的鍵盤設定頁面，點選 LimeIME 並開啟「允許完整取用」。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                } header: { Text("步驟 2") }

                Section {
                    Button("前往系統設定") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }
            }
            .navigationTitle("LimeIME 設定")
            .onAppear { checkKeyboardEnabled() }
        }
    }

    private func checkKeyboardEnabled() {
        let bundleID = "net.toload.limeime.keyboard"
        let enabled = UITextInputMode.activeInputModes
            .compactMap { $0.primaryLanguage }
            .contains(where: { _ in
                // Check if our extension is in the enabled keyboards list
                UserDefaults.standard.dictionaryRepresentation().keys
                    .contains { $0.contains(bundleID) }
            })
        isKeyboardEnabled = enabled
    }
}

// MARK: - Manage IM Tab

struct ManageIMTab: View {
    @State private var imList: [IMRow] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    struct IMRow: Identifiable {
        let id: Int64
        let label: String
        let tableNick: String
        var enabled: Bool
        var sortOrder: Int
    }

    var body: some View {
        NavigationView {
            Group {
                if isLoading {
                    ProgressView("載入中…")
                } else if let err = errorMessage {
                    Text(err).foregroundColor(.secondary)
                } else if imList.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "tray")
                            .font(.largeTitle)
                            .foregroundColor(.secondary)
                        Text("尚未匯入任何輸入法。\n請至「匯入」分頁加入 .db 檔案。")
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                    }
                } else {
                    List {
                        ForEach($imList) { $row in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(row.label).font(.body)
                                    Text(row.tableNick).font(.caption).foregroundColor(.secondary)
                                }
                                Spacer()
                                Toggle("", isOn: $row.enabled)
                                    .onChange(of: row.enabled) { newVal in
                                        toggleIM(id: row.id, enabled: newVal)
                                    }
                            }
                        }
                        .onMove(perform: moveIMs)
                    }
                    .toolbar { EditButton() }
                }
            }
            .navigationTitle("管理輸入法")
            .onAppear { loadIMs() }
        }
    }

    private func loadIMs() {
        isLoading = true
        DispatchQueue.global(qos: .userInitiated).async {
            guard let db = openDB() else {
                DispatchQueue.main.async {
                    errorMessage = "無法開啟資料庫"
                    isLoading = false
                }
                return
            }
            let configs = (try? db.getAllImConfigs()) ?? []
            let rows = configs.map { c in
                IMRow(id: c.id, label: c.label.isEmpty ? c.imName : c.label,
                      tableNick: c.tableNick, enabled: c.enabled, sortOrder: c.sortOrder)
            }
            DispatchQueue.main.async {
                imList = rows
                isLoading = false
            }
        }
    }

    private func toggleIM(id: Int64, enabled: Bool) {
        DispatchQueue.global(qos: .background).async {
            guard let db = openDB() else { return }
            try? db.updateIMEnabled(id: id, enabled: enabled)
        }
    }

    private func moveIMs(from source: IndexSet, to dest: Int) {
        imList.move(fromOffsets: source, toOffset: dest)
        for (idx, row) in imList.enumerated() {
            let id = row.id
            DispatchQueue.global(qos: .background).async {
                guard let db = openDB() else { return }
                try? db.updateIMSortOrder(id: id, sortOrder: idx)
            }
        }
    }
}

// MARK: - Import Tab

struct ImportTab: View {
    @State private var isImporting = false
    @State private var importProgress: String = ""
    @State private var showFilePicker = false
    @State private var pickerType: ImportType = .db

    enum ImportType { case db, txt }

    var body: some View {
        NavigationView {
            List {
                Section {
                    Button {
                        pickerType = .db
                        showFilePicker = true
                    } label: {
                        Label("匯入 .db / .limedb 檔案", systemImage: "archivebox")
                    }
                    Button {
                        pickerType = .txt
                        showFilePicker = true
                    } label: {
                        Label("匯入 .cin / .lime 文字檔", systemImage: "doc.text")
                    }
                } header: { Text("匯入輸入法") }

                if !importProgress.isEmpty {
                    Section {
                        Text(importProgress)
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    } header: { Text("狀態") }
                }

                Section {
                    Button {
                        exportBackup()
                    } label: {
                        Label("匯出備份", systemImage: "square.and.arrow.up")
                    }
                } header: { Text("備份") }
            }
            .navigationTitle("匯入 / 匯出")
            .fileImporter(
                isPresented: $showFilePicker,
                allowedContentTypes: allowedTypes(),
                allowsMultipleSelection: false
            ) { result in
                handleFileImport(result: result)
            }
            .overlay {
                if isImporting {
                    ProgressView("匯入中…")
                        .padding(24)
                        .background(RoundedRectangle(cornerRadius: 12)
                            .fill(Color(.systemBackground))
                            .shadow(radius: 8))
                }
            }
        }
    }

    private func allowedTypes() -> [UTType] {
        switch pickerType {
        case .db:  return [UTType.item]          // .db / .limedb — no standard UTI
        case .txt: return [UTType.plainText, .item]
        }
    }

    private func handleFileImport(result: Result<[URL], Error>) {
        guard case .success(let urls) = result, let url = urls.first else { return }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }

        isImporting = true
        importProgress = ""

        let ext = url.pathExtension.lowercased()
        let tableName = url.deletingPathExtension().lastPathComponent
            .components(separatedBy: .init(charactersIn: "-_")).first ?? "custom"

        DispatchQueue.global(qos: .userInitiated).async {
            guard let db = openDB() else {
                DispatchQueue.main.async {
                    isImporting = false
                    importProgress = "❌ 無法開啟資料庫"
                }
                return
            }

            do {
                if ext == "db" || ext == "limedb" {
                    let safeTable = db.isValidTableName(tableName) ? tableName : "custom"
                    try db.importFromAttachedDB(sourcePath: url.path, tableName: safeTable)
                    DispatchQueue.main.async {
                        isImporting = false
                        importProgress = "✅ 已成功匯入 \(safeTable)"
                    }
                } else {
                    try db.importTxtFile(at: url.path, tableName: tableName) { count in
                        DispatchQueue.main.async {
                            importProgress = "已匯入 \(count) 筆…"
                        }
                    }
                    DispatchQueue.main.async {
                        isImporting = false
                        importProgress = "✅ 文字檔匯入完成"
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    isImporting = false
                    importProgress = "❌ \(error.localizedDescription)"
                }
            }
        }
    }

    private func exportBackup() {
        guard let db = openDB() else { return }
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.net.toload.limeime") else { return }

        let backupPath = containerURL
            .appendingPathComponent("lime_backup_\(Date().timeIntervalSince1970).db")
            .path

        do {
            try db.exportDB(to: backupPath)
            importProgress = "✅ 備份已存至 App 共享容器"
        } catch {
            importProgress = "❌ 備份失敗：\(error.localizedDescription)"
        }
    }
}

// MARK: - Settings Tab

// Settings stored in the shared App Group so the keyboard extension can read them.
private let sharedSuite = "group.net.toload.limeime"

struct SettingsTab: View {
    // Use @AppStorage with the shared suite name so the keyboard extension can read these values.
    @AppStorage("candidateCount",      store: UserDefaults(suiteName: sharedSuite))
    private var candidateCount: Int    = 50
    @AppStorage("candidateFontSize",   store: UserDefaults(suiteName: sharedSuite))
    private var fontSize: Double       = 18
    @AppStorage("hanConvertOption",    store: UserDefaults(suiteName: sharedSuite))
    private var hanConvert: Int        = 0
    @AppStorage("autoChineseSymbol",   store: UserDefaults(suiteName: sharedSuite))
    private var autoChineseSymbol: Bool = false
    @AppStorage("sortSuggestions",     store: UserDefaults(suiteName: sharedSuite))
    private var sortSuggestions: Bool  = false
    @AppStorage("smartChineseInput",   store: UserDefaults(suiteName: sharedSuite))
    private var smartChineseInput: Bool = false
    @AppStorage("learnPhrase",         store: UserDefaults(suiteName: sharedSuite))
    private var learnPhrase: Bool      = true
    @AppStorage("englishPrediction",   store: UserDefaults(suiteName: sharedSuite))
    private var englishPrediction: Bool = true
    @AppStorage("selkeyOption",        store: UserDefaults(suiteName: sharedSuite))
    private var selkeyOption: Int      = 0
    @AppStorage("hasVibration",        store: UserDefaults(suiteName: sharedSuite))
    private var hasVibration: Bool     = false
    @AppStorage("hasSound",            store: UserDefaults(suiteName: sharedSuite))
    private var hasSound: Bool         = false
    @AppStorage("persistentLanguageMode", store: UserDefaults(suiteName: sharedSuite))
    private var persistentLanguageMode: Bool = false
    @AppStorage("phonetic_keyboard_type", store: UserDefaults(suiteName: sharedSuite))
    private var phoneticKeyboardType: String = "phonetic"
    @AppStorage("auto_commit",         store: UserDefaults(suiteName: sharedSuite))
    private var autoCommit: Int        = 0

    private let hanOptions = ["不轉換", "繁→簡", "簡→繁"]
    private let phoneticKbOptions = ["phonetic", "et_41", "et26", "hsu"]
    private let phoneticKbLabels  = ["標準注音", "倚天41鍵", "倚天26鍵", "許式鍵盤"]

    var body: some View {
        NavigationView {
            Form {
                Section("候選字") {
                    Stepper("每次顯示 \(candidateCount) 個",
                            value: $candidateCount, in: 15...210, step: 15)
                    HStack {
                        Text("字體大小")
                        Spacer()
                        Text("\(Int(fontSize)) pt").foregroundColor(.secondary)
                    }
                    Slider(value: $fontSize, in: 14...28, step: 1)
                    Toggle("選字鍵 (1–9)", isOn: Binding(
                        get: { selkeyOption > 0 },
                        set: { selkeyOption = $0 ? 1 : 0 }))
                }

                Section("注音鍵盤") {
                    Picker("鍵盤類型", selection: $phoneticKeyboardType) {
                        ForEach(0..<phoneticKbOptions.count, id: \.self) { i in
                            Text(phoneticKbLabels[i]).tag(phoneticKbOptions[i])
                        }
                    }
                    Stepper("自動送字：\(autoCommit == 0 ? "關" : "\(autoCommit)碼")",
                            value: $autoCommit, in: 0...10)
                }

                Section("漢字轉換") {
                    Picker("模式", selection: $hanConvert) {
                        ForEach(0..<hanOptions.count, id: \.self) { i in
                            Text(hanOptions[i]).tag(i)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("輸入行為") {
                    Toggle("自動中文符號", isOn: $autoChineseSymbol)
                    Toggle("智慧組詞", isOn: $smartChineseInput)
                    Toggle("學習詞組", isOn: $learnPhrase)
                    Toggle("英文預測", isOn: $englishPrediction)
                    Toggle("排序候選字", isOn: $sortSuggestions)
                    Toggle("持續語言模式", isOn: $persistentLanguageMode)
                }

                Section("回饋") {
                    Toggle("震動", isOn: $hasVibration)
                    Toggle("音效", isOn: $hasSound)
                }

                Section("關於") {
                    LabeledContent("版本", value: appVersion())
                    Link("原始碼 (GitHub)",
                         destination: URL(string: "https://github.com/lime-ime/limeime")!)
                }
            }
            .navigationTitle("偏好設定")
        }
    }

    private func appVersion() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
        return "\(v) (\(b))"
    }
}

// MARK: - Shared DB opener
private func openDB() -> LimeDB? {
    guard let containerURL = FileManager.default.containerURL(
        forSecurityApplicationGroupIdentifier: "group.net.toload.limeime") else { return nil }
    let dbURL = containerURL.appendingPathComponent("lime.db")
    copyBundledDBIfNeeded(to: dbURL)
    return try? LimeDB(path: dbURL.path)
}

private func copyBundledDBIfNeeded(to dest: URL) {
    guard let src = Bundle.main.url(forResource: "lime", withExtension: "db") else { return }
    let size = (try? dest.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
    guard size < 1_000_000 else { return }
    try? FileManager.default.removeItem(at: dest)
    try? FileManager.default.copyItem(at: src, to: dest)
}
