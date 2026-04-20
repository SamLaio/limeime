// SetupTabView.swift
// LimeIME-iOS
//
// App Setup tab — keyboard activation guide, status detection, about.
// Spec §4.

import SwiftUI
import UIKit

// MARK: - SetupTabView

struct SetupTabView: View {

    @Environment(\.scenePhase) private var scenePhase

    // Detection state:
    // `keyboardSeenAt` is the heartbeat the keyboard writes on every load/appear.
    // `sessionOpenedAt` is the moment this Settings app became active — we only
    // trust heartbeats newer than this to avoid the one-way-latch bug where a
    // keyboard the user has since disabled still reports "enabled forever".
    @State private var keyboardSeenAt: TimeInterval = 0
    @State private var fullAccessEnabled = false
    @State private var sessionOpenedAt: TimeInterval = Date().timeIntervalSince1970

    // Probe field: typing here with LimeIME selected triggers the heartbeat.
    @State private var probeText: String = ""
    @FocusState private var probeFocused: Bool
    @State private var pollTimer: Timer?

    private let groupSuite = "group.net.toload.limeime"

    var body: some View {
        NavigationView {
            List {
                // MARK: Status banner + probe
                Section(header: Text("啟用狀態")) {
                    statusBanner
                    probeField
                }

                // MARK: Step 1
                Section(header: Text("步驟 1 — 新增鍵盤")) {
                    Text("前往「設定 → 一般 → 鍵盤 → 鍵盤 → 新增鍵盤」，選擇 LimeIME。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                // MARK: Step 2
                Section(header: Text("步驟 2 — 允許完整取用")) {
                    Text("點下方按鈕進入 LimeIME 的系統設定頁，選「鍵盤」，然後開啟「允許完整取用」。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    Button("開啟 LimeIME 設定") { openAppSettings() }
                }

                // MARK: About
                Section(header: Text("關於")) {
                    LabeledContent("版本", value: appVersion())
                    LabeledContent("授權", value: "GPL-3.0")
                    Link("原始碼 (GitHub)",
                         destination: URL(string: "https://github.com/lime-ime/limeime")!)
                }
            }
            .navigationTitle("LimeIME 設定")
            .onAppear {
                markSessionOpened()
                refreshStatus()
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    markSessionOpened()
                    refreshStatus()
                }
            }
            .onChange(of: probeFocused) { focused in
                focused ? startPolling() : stopPolling()
            }
            .onChange(of: probeText) { _ in refreshStatus() }
        }
    }

    // MARK: - Status banner

    private var statusBanner: some View {
        Group {
            switch detectionState {
            case .fullyEnabled:
                Label("LimeIME 鍵盤已啟用（\(lastSeenText)）", systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
            case .enabledNoFullAccess:
                Label("鍵盤已啟用，但尚未允許完整取用（\(lastSeenText)）",
                      systemImage: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
            case .unknown:
                Label("尚未偵測到 LimeIME 鍵盤 — 請依下列步驟新增，並於下方欄位輸入以確認",
                      systemImage: "questionmark.circle.fill")
                    .foregroundColor(.gray)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Probe field

    private var probeField: some View {
        TextField("在這裡輸入以偵測鍵盤", text: $probeText)
            .focused($probeFocused)
            .textInputAutocapitalization(.never)
            .disableAutocorrection(true)
            .font(.footnote)
    }

    // MARK: - Detection

    private enum DetectionState {
        case fullyEnabled, enabledNoFullAccess, unknown
    }

    private var detectionState: DetectionState {
        // Only trust heartbeats written after this Settings session became active.
        // Anything older could be a stale latch from a previous install/session.
        guard keyboardSeenAt >= sessionOpenedAt else { return .unknown }
        return fullAccessEnabled ? .fullyEnabled : .enabledNoFullAccess
    }

    private var lastSeenText: String {
        guard keyboardSeenAt > 0 else { return "從未偵測" }
        let delta = Date().timeIntervalSince1970 - keyboardSeenAt
        if delta < 5 { return "最後偵測：剛剛" }
        if delta < 60 { return "最後偵測：\(Int(delta)) 秒前" }
        return "最後偵測：\(Int(delta / 60)) 分鐘前"
    }

    private func refreshStatus() {
        let suite = UserDefaults(suiteName: groupSuite)
        keyboardSeenAt    = suite?.double(forKey: "keyboard_last_seen_at") ?? 0
        fullAccessEnabled = suite?.bool(forKey: "keyboard_has_full_access") ?? false
    }

    private func markSessionOpened() {
        sessionOpenedAt = Date().timeIntervalSince1970
    }

    // Poll every 0.5 s while the probe field is focused, so the banner flips as
    // soon as the user long-presses the globe and LimeIME writes its heartbeat —
    // even before they type a single character.
    private func startPolling() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            refreshStatus()
        }
    }

    private func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
    }

    // MARK: - Navigation

    /// Opens the app's own page in the system Settings app. This is the only
    /// deep link Apple guarantees; `App-Prefs:` URLs are private and unreliable.
    /// From the LimeIME settings page the user can tap "鍵盤" to reach the
    /// per-app keyboard screen where Full Access can be toggled.
    private func openAppSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Version

    private func appVersion() -> String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
        return "\(v) (\(b))"
    }
}
