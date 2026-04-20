// IMDetailView.swift
// LimeIME-iOS
//
// IM detail drill-down showing metadata, keyboard picker, and table editor link.
// Spec §5.2.

import SwiftUI

// MARK: - IMDetailView

struct IMDetailView: View {

    let im: IMRow
    let onRefresh: (() -> Void)?

    @EnvironmentObject private var manageImController: ManageImController
    @EnvironmentObject private var manageRelatedController: ManageRelatedController
    @EnvironmentObject private var setupController: SetupImController
    @Environment(\.dismiss) private var dismiss

    private let sharedUD = UserDefaults(suiteName: "group.net.toload.limeime")

    // §13.3 — custom IM mapping toggles (only shown when tableNick == "custom")
    @AppStorage("accept_number_index", store: sharedDefaults) private var acceptNumberIndex: Bool = false
    @AppStorage("accept_symbol_index", store: sharedDefaults) private var acceptSymbolIndex: Bool = false

    // array10 phone-numpad auto-commit (only shown when tableNick == "array10")
    @AppStorage("auto_commit", store: sharedDefaults) private var autoCommit: Int = 0
    private let autoCommitOpts   = [0, 4, 5, 6, 7, 8, 9, 10]
    private let autoCommitLabels = ["無", "4碼", "5碼", "6碼", "7碼", "8碼", "9碼", "10碼"]

    // §8.5 — phonetic keyboard type (only shown when tableNick == "phonetic")
    @AppStorage("phonetic_keyboard_type", store: sharedDefaults) private var phoneticKeyboardType: String = "standard"
    private let phoneticOptions = ["standard", "et_41", "eten26", "eten26_symbol", "hsu", "hsu_symbol"]
    private let phoneticLabels  = ["標準", "倚天 41 鍵", "倚天 26 鍵 (英文)", "倚天 26 鍵 (符號)", "許氏 (英文)", "許氏 (符號)"]

    @State private var keyboardName: String = ""
    @State private var showRemoveAlert = false
    @State private var isRemoving = false
    @State private var showClearRelatedAlert = false
    @State private var showSharePicker = false
    @State private var isExporting = false
    @State private var shareURL: URL?
    @State private var showShareSheet = false

    init(im: IMRow, onRefresh: (() -> Void)? = nil) {
        self.im = im
        self.onRefresh = onRefresh
    }

    private var mappingVersion: String {
        sharedUD?.string(forKey: im.tableNick + "mapping_version") ?? "—"
    }

    @State private var totalRecord: String = "—"

    var body: some View {
        List {
            Section(header: Text("輸入法資訊")) {
                LabeledContent("名稱", value: im.label)
                if im.tableNick != "related" {
                    LabeledContent("版本", value: mappingVersion)
                }
                LabeledContent("筆數", value: totalRecord)
            }

            if im.tableNick != "related" {
                Section(header: Text("軟鍵盤配置")) {
                    NavigationLink(destination: KeyboardPickerView(im: im, onSave: onRefresh)) {
                        LabeledContent("鍵盤佈局", value: keyboardName.isEmpty ? "—" : keyboardName)
                    }
                }
            }

            // Phonetic keyboard type (§8.5 — moved here because the pref only
            // affects the phonetic IM).
            if im.tableNick == "phonetic" {
                Section(header: Text("注音鍵盤類型")) {
                    Picker("鍵盤類型", selection: $phoneticKeyboardType) {
                        ForEach(0..<phoneticOptions.count, id: \.self) { i in
                            Text(phoneticLabels[i]).tag(phoneticOptions[i])
                        }
                    }
                    .onChange(of: phoneticKeyboardType) { newType in
                        updatePhoneticKeyboard(type: newType)
                    }
                }
            }

            // Array10 phone-numpad auto-commit setting
            if im.tableNick == "array10" {
                Section(header: Text("電話鍵盤設定")) {
                    Picker(selection: $autoCommit) {
                        ForEach(0..<autoCommitOpts.count, id: \.self) { i in
                            Text(autoCommitLabels[i]).tag(autoCommitOpts[i])
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("自動上屏")
                            Text("輸入字根符合設定數則自動送出組字")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }

            // §13.3 — shown only for the user-built custom IM
            if im.tableNick == "custom" {
                Section(header: Text("字根對應設定")) {
                    Toggle(isOn: $acceptNumberIndex) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("數字字根對應")
                            Text("允許使用數字為輸入法字根")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                    Toggle(isOn: $acceptSymbolIndex) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("符號字根對應")
                            Text("允許使用符號為輸入法字根")
                                .font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }

            Section(header: Text(im.tableNick == "related" ? "聯想詞庫" : "字根資料表")) {
                if im.tableNick == "related" {
                    NavigationLink(destination: RelatedListView(isEmbedded: true)) {
                        Label("瀏覽 / 編輯聯想詞庫", systemImage: "text.bubble")
                    }
                } else {
                    NavigationLink(destination: RecordListView(tableName: im.tableNick,
                                                               imLabel: im.label)) {
                        Label("瀏覽 / 編輯資料表", systemImage: "tablecells")
                    }
                }
            }

            if im.tableNick == "related" {
                Section {
                    Button(role: .destructive) {
                        showClearRelatedAlert = true
                    } label: {
                        HStack {
                            Spacer()
                            Text("清除聯想詞庫")
                            Spacer()
                        }
                    }
                }
            }

            if im.tableNick != "related" {
                Section {
                    Button(role: .destructive) {
                        showRemoveAlert = true
                    } label: {
                        HStack {
                            Spacer()
                            if isRemoving {
                                ProgressView()
                            } else {
                                Text("移除輸入法")
                            }
                            Spacer()
                        }
                    }
                    .disabled(isRemoving)
                }
            }
        }
        .navigationTitle(im.label)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showSharePicker = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .disabled(isExporting)
            }
        }
        .confirmationDialog("匯出格式", isPresented: $showSharePicker, titleVisibility: .visible) {
            if im.tableNick != "related" {
                Button(".lime（文字）") { exportAndShare(format: .txt) }
            }
            Button(".limedb（資料庫）") { exportAndShare(format: .db) }
            Button("取消", role: .cancel) {}
        }
        .sheet(isPresented: $showShareSheet, onDismiss: { shareURL = nil }) {
            if let url = shareURL {
                ShareSheet(activityItems: [url])
            }
        }
        .overlay {
            if isExporting {
                ZStack {
                    Color.black.opacity(0.3).ignoresSafeArea()
                    ProgressView("匯出中…")
                        .padding(24)
                        .background(RoundedRectangle(cornerRadius: 12)
                            .fill(Color(.systemBackground))
                            .shadow(radius: 8))
                }
            }
        }
        .task {
            if im.tableNick == "related" {
                let n = await manageImController.countRelated()
                totalRecord = "\(n)"
            } else {
                async let keyboards = manageImController.loadKeyboards(forIM: im.tableNick)
                async let count = manageImController.countRecords(table: im.tableNick)
                let (kb, n) = await (keyboards, count)
                keyboardName = kb.keyboards.first(where: { $0.code == kb.selected })?.desc ?? kb.selected
                totalRecord = "\(n)"
            }
        }
        .alert("移除輸入法", isPresented: $showRemoveAlert) {
            Button("移除", role: .destructive) {
                isRemoving = true
                Task {
                    _ = await manageImController.clearTable(tableNick: im.tableNick)
                    isRemoving = false
                    onRefresh?()
                    dismiss()
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("此操作將清除「\(im.label)」的所有對應資料，無法還原。確定繼續？")
        }
        .alert("清除聯想詞庫", isPresented: $showClearRelatedAlert) {
            Button("清除", role: .destructive) {
                Task {
                    _ = await manageRelatedController.clearRelated()
                    totalRecord = "0"
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("此操作將清除所有聯想詞資料，無法還原。確定繼續？")
        }
    }

    // MARK: - Phonetic helper

    /// Mirrors Android's LIMEPreference.onSharedPreferenceChanged — maps the
    /// picker value to a `keyboard.code` and writes `im.keyboard` for phonetic
    /// so the keyboard extension's `refreshPhoneticKeyboardPrefs()` picks up the
    /// new layout on next onStartInput.
    ///
    /// Picker value → keyboard.code routing (matches Android LIMEPreference.java:209-235):
    ///   "standard"       → "phonetic"
    ///   "et_41" / "eten" → "phoneticet41"
    ///   "eten26"         → "limenum" or "lime" (number_row_in_english toggle)
    ///   "eten26_symbol"  → "et26"
    ///   "hsu"            → "limenum" or "lime"
    ///   "hsu_symbol"     → "hsu"
    private func updatePhoneticKeyboard(type: String) {
        Task {
            await Task.detached(priority: .userInitiated) {
                let server = DBServer.shared
                let numberRow = sharedDefaults.bool(forKey: "number_row_in_english")
                let targetCode: String
                switch type {
                case "standard":           targetCode = "phonetic"
                case "et_41", "eten":      targetCode = "phoneticet41"
                case "eten26":             targetCode = numberRow ? "limenum" : "lime"
                case "eten26_symbol":      targetCode = "et26"
                case "hsu":                targetCode = numberRow ? "limenum" : "lime"
                case "hsu_symbol":         targetCode = "hsu"
                default:                   targetCode = type
                }
                guard let kbList = server.getKeyboardConfigList(),
                      let kb = kbList.first(where: { $0.code == targetCode }) else { return }
                server.setImConfigKeyboard("phonetic", kb)
            }.value
            // Refresh the 鍵盤佈局 label after the DB write so the detail page
            // reflects the new layout immediately.
            let result = await manageImController.loadKeyboards(forIM: im.tableNick)
            await MainActor.run {
                keyboardName = result.keyboards.first(where: { $0.code == result.selected })?.desc ?? result.selected
            }
        }
    }

    // MARK: - Export helpers

    private enum ExportFormat { case db, txt }

    private func exportAndShare(format: ExportFormat) {
        isExporting = true
        Task {
            let url: URL?
            if im.tableNick == "related" {
                url = await setupController.exportRelatedAsLimedb()
            } else if format == .db {
                url = await setupController.exportIMAsLimedb(tableNick: im.tableNick)
            } else {
                url = await setupController.exportIMAsText(tableNick: im.tableNick)
            }
            isExporting = false
            if let url {
                shareURL = url
                showShareSheet = true
            }
        }
    }
}
