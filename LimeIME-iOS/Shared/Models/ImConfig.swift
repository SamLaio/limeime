import Foundation

/// An input method configuration row from the `im` table in lime.db.
/// Mirrors Android's ImConfig.java.
struct ImConfig {
    let id: Int64
    let imName: String
    let tableNick: String
    let label: String
    let keyboardId: String
    let keyboardLandscapeId: String
    var enabled: Bool
    var sortOrder: Int
}
