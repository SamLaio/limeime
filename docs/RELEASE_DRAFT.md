# LIME 2026 — 版本 v6.0.0

**版本標籤：** `6.0.0-2026`
**APK：** `LIMEHD2026-6.0.0.apk`
**套件名稱：** `net.toload.main.hd2026`
**目標 SDK：** 36 | **最低 SDK：** 21

本次發行距離前一個正式版本約 8 年，定位為「長週期整體升級版」，重點在於平台現代化、相容性重建與程式架構整理。

---

## 更新內容

### 現代化升級
- 更新專案以支援 Android Studio Otter，並升級 Gradle 建置系統
- 目標 API 36，向下相容至 API 21
- 以現代相依套件取代所有內附 JAR 函式庫（Dropbox SDK、Google API client 等）
- 以 zip4j 取代內建 zip，確保備份功能向下相容（Android < 14.0）
- 建立單元測試基礎設施（採用 Mockito 4.11.x），補齊過去版本缺乏測試的狀態

### 架構重構
- 重構核心架構，強化關注點分離
- 重構 UI 層為 MVC 架構
- 將 `IM` 更名為 `IMConfig`，並將所有 SQL 邏輯整合至 `LimeDB`
- 重構核心資料結構：`Mapping`、`Record` 及 `Related`

### 平台相容與穩定性更新
- 重建新版 Android（API 31 至 36）上的輸入、觸覺回饋與沉浸式顯示行為
- 重新整理備份/還原流程與檔案選取相容路徑，降低不同裝置型態（手機/平板）差異造成的失敗率
- 改善語音輸入提交流程、候選字顯示及鍵盤標籤定位一致性
- 調整系統列與虛擬鍵盤區域的視覺整合，提升新舊 Android 版本外觀一致性
- 強化碼表載入期間的輸入法切換穩定度

### 資料庫
- 資料庫升級至版本 102
- 新增注音（HS）及倉頡（WB）鍵盤項目

### 介面改善
- 統一使用 CandidateInInputView 顯示候選字
- 細調 UI 主題樣式
- 調整直式鍵盤標籤位置
- 補齊各螢幕密度缺少的 drawable PNG 圖片

### 清理
- 移除 AndroidManifest 中不必要的權限
- 移除失效下載連結（Openfoundry 及 Google Code 均已於 2025 年關閉）
- 更新行列及行列10下載連結與項目數量
- 一般程式碼清理

---

## 相容性

| API 等級 | Android 版本 | 狀態 |
|-----------|----------------|--------|
| 21 | 5.0 Lollipop | 最低支援版本 |
| 31 | 12 | 新版行為相容基線 |
| 33 | 13 | 觸覺回饋與輸入流程穩定 |
| 35 | 15 | 全面屏與系統列整合 |
| 36 | 16 | 目標 SDK（主要驗證版本） |

## 安裝說明

本版本使用新套件名稱（`net.toload.main.hd2026`），可與舊版 LIME HD 並存安裝。

## 文件

- [架構總覽](LIMEIME_ARCHITECTURE.md)
- [UI 架構](UI_ARCHITECTURE.md)
- [重構架構](REFACTORING_ARCHITECTURE.md)
- [測試計畫](TEST_PLAN.md)
- [測試覆蓋報告](TEST_COVERAGE_REPORT.md)
- [API 相容性評估](API_COMPATIBILITY_REVIEW.md)
- [全面屏評估](EDGE_TO_EDGE_REVIEW.md)
- [權限評估](PERMISSION_REVIEW.md)
