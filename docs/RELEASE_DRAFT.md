# Lime-Sam v0.4.1

**版本標籤：** `v0.4.1`
**APK：** `LIME-SAM.apk`
**套件名稱：** `tw.idv.sam.lime`
**目標 SDK：** 36 | **最低 SDK：** 21
**前一版本：** v0.4

v0.4.1 是 KeePass 同步與鍵盤欄位安全修正版，保留主分支 KeePass 功能與套件名稱 `tw.idv.sam.lime`。

---

## 更新內容

### 修正

- KeePass 同步按鈕加入同步中狀態、防重複點擊與本機資料庫提示。
- KeePass 新增/刪除後立即重建 SQLite entry cache，避免 `.kdbx` 已更新但 cache 未更新。
- `content://` 本機 `.kdbx` 改以內容 SHA-256 偵測變更，外部編輯後可讓 SQLite cache 失效重建。
- KeePass 鍵盤支援 `持卡人`、`號碼`、`exp_date`、`CVV` 自訂欄位，其中 `exp_date` 在鍵盤顯示為「過期」。
- 指定卡片欄位不參與 KeePass 搜尋，但會在解鎖後顯示為鍵盤按鈕。
- KeePass SQLite cache 內的密碼與指定卡片欄位皆加密保存；同步 `.kdbx` 時不會把 SQLite 加密 cache payload 寫回原 `.kdbx`。
- 修正 Android Studio / 文件中舊套件名稱殘留，主分支套件名稱維持 `tw.idv.sam.lime`。

### 版本

- `versionName`: `0.4.1`
- `versionCode`: `5`

---
