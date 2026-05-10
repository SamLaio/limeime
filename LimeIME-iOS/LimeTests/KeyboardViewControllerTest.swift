import XCTest
@testable import LimeIME

final class KeyboardViewControllerTest: XCTestCase {

    private struct KeyboardLayoutFixture: Decodable {
        let rows: [KeyboardRowFixture]
    }

    private struct KeyboardRowFixture: Decodable {
        let isBottomRow: Bool
        let keys: [KeyboardKeyFixture]
    }

    private struct KeyboardKeyFixture: Decodable {
        let code: Int
        let widthPercent: Double
    }

    func testEmojiKeyboardKeyCodesUseReservedCrossPlatformRange() {
        XCTAssertEqual(LimeKeyCode.emojiPanel.rawValue, -201)
        XCTAssertEqual(LimeKeyCode.emojiABC.rawValue, -202)
        XCTAssertEqual(LimeKeyCode.emojiCategoryRecent.rawValue, -203)
        XCTAssertEqual(LimeKeyCode.emojiCategoryFlags.rawValue, -211)
    }

    func testEnglishLayoutHasEmojiLauncherOnBottomRowAndChineseSwitchAboveIt() {
        let rows = LimeKeyLayout.english.rows
        let bottomCodes = rows.last?.keys.map(\.code) ?? []
        let allNonBottomCodes = rows.dropLast().flatMap { $0.keys.map(\.code) }

        XCTAssertTrue(bottomCodes.contains(LimeKeyCode.emojiPanel.rawValue))
        XCTAssertFalse(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue))
        XCTAssertTrue(allNonBottomCodes.contains(LimeKeyCode.switchToIM.rawValue))
    }

    func testIPhoneEnglishJsonLayoutsExposeEmojiLauncher() throws {
        for layoutID in ["lime_english", "lime_english_number"] {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            let bottomCodes = layout.rows.first(where: { $0.isBottomRow })?.keys.map(\.code) ?? []
            XCTAssertTrue(bottomCodes.contains(LimeKeyCode.emojiPanel.rawValue),
                          "\(layoutID) should expose the emoji launcher")
        }
    }

    func testIPhoneEnglishJsonLayoutsKeepChineseSwitchOnHomeRowAndFullSpaceWidth() throws {
        for layoutID in ["lime_english", "lime_english_number"] {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            let homeCodes = layout.rows[layout.rows.count - 3].keys.map(\.code)
            let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }))
            let bottomCodes = bottom.keys.map(\.code)
            let space = try XCTUnwrap(bottom.keys.first(where: { $0.code == 32 }))

            XCTAssertEqual(homeCodes.first, LimeKeyCode.switchToIM.rawValue,
                           "\(layoutID) should place 中 as the leftmost asdf-row key")
            XCTAssertFalse(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue),
                           "\(layoutID) should not place 中 on the bottom row")
            XCTAssertEqual(space.widthPercent, 30.0, "\(layoutID) should keep the full-width space key")
        }
    }

    func testCandidateChevronExpansionAllowsEnglishSuggestionsWithoutComposingBuffer() {
        XCTAssertTrue(CandidateExpansionPolicy.shouldExpand(
            hasCandidatesShown: true,
            composing: "",
            hasChineseSymbolCandidatesShown: false
        ))
    }

    func testCandidateChevronExpansionStillRequiresVisibleCandidates() {
        XCTAssertFalse(CandidateExpansionPolicy.shouldExpand(
            hasCandidatesShown: false,
            composing: "",
            hasChineseSymbolCandidatesShown: false
        ))
    }

    private func loadKeyboardLayoutFixture(_ layoutID: String) throws -> KeyboardLayoutFixture {
        let testFileURL = URL(fileURLWithPath: #filePath)
        let iosRoot = testFileURL.deletingLastPathComponent().deletingLastPathComponent()
        let url = iosRoot
            .appendingPathComponent("LimeKeyboard/Layouts")
            .appendingPathComponent("\(layoutID).json")
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(KeyboardLayoutFixture.self, from: data)
    }
}
