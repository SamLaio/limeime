import UIKit

// Full keyboard view: renders keys from a LimeKeyLayout.
// Phase 2: UIButton-based; Phase 3 can switch to UICollectionView for more flexibility.

// MARK: - KeyboardPalette

struct KeyboardPalette {
    let background:      UIColor
    let normalKey:       UIColor
    let modifierKey:     UIColor
    let pressedKey:      UIColor
    /// Label color for normal (character) keys.
    let label:           UIColor
    /// Label color for modifier/function keys — may differ when modifierKey bg needs opposite contrast.
    let modifierLabel:   UIColor
    let secondaryLabel:  UIColor
    let candiBackground: UIColor
    let candiText:       UIColor
    /// Background tint drawn behind the currently-selected candidate cell.
    /// Mirrors Android `mDrawableSuggestHighlight` (drawable/ic_suggest_scroll_background_hl).
    let candiHighlight:  UIColor

    // Indices 0–5 match keyboard_theme values 0–5.
    // Theme 6 (系統設定) is resolved to 0 or 1 by KeyboardViewController.
    // Colors ported exactly from Android LimeStudio/app/src/main/res/values/colors.xml.
    static let palettes: [KeyboardPalette] = [
        // 0 淺色 (Light) — dark modifier bg #E1E1E1, dark label
        KeyboardPalette(
            background:      h(0xC8C8C8),
            normalKey:       h(0xFAFAFA),
            modifierKey:     h(0xE1E1E1),
            pressedKey:      h(0xFBFBFB),
            label:           h(0x0F0F0F),
            modifierLabel:   h(0x0F0F0F),
            secondaryLabel:  h(0x717171),
            candiBackground: h(0xFAFAFA),
            candiText:       h(0x0F0F0F),
            candiHighlight:  h(0x4DB6AC)),
        // 1 深色 (Dark) — very dark modifier bg #141414, light label
        KeyboardPalette(
            background:      h(0x373737),
            normalKey:       h(0x212121),
            modifierKey:     h(0x141414),
            pressedKey:      h(0x212121),
            label:           h(0xF7F7F7),
            modifierLabel:   h(0xF7F7F7),
            secondaryLabel:  h(0x6B6B6B),
            candiBackground: h(0x141414),
            candiText:       h(0xCFD8DC),
            candiHighlight:  h(0x4DB6AC)),
        // 2 粉紅 (Pink) — modifier bg #F173AC (dark pink), white label
        KeyboardPalette(
            background:      h(0xFAD5E5),
            normalKey:       h(0xF49AC1),
            modifierKey:     h(0xF173AC),
            pressedKey:      h(0xF173AC),
            label:           h(0xFFFFFF),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xC74A72),
            candiBackground: h(0xFEF3F7),
            candiText:       h(0x000000),
            candiHighlight:  h(0xF49AC1)),
        // 3 科技藍 (Tech Blue) — normal label #314453 (dark), modifier bg #6699CC needs white
        KeyboardPalette(
            background:      h(0xC5DBEC),
            normalKey:       h(0x9BC5E4),
            modifierKey:     h(0x6699CC),
            pressedKey:      h(0x6699CC),
            label:           h(0x314453),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xD8E7F3),
            candiText:       h(0x000000),
            candiHighlight:  h(0x9BC5E4)),
        // 4 時尚紫 (Fashion Purple) — modifier bg #8F53A1 (dark purple), white label
        KeyboardPalette(
            background:      h(0xB0ACD5),
            normalKey:       h(0xB28ABF),
            modifierKey:     h(0x8F53A1),
            pressedKey:      h(0x8F53A1),
            label:           h(0xEEEEEE),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xEFEDFF),
            candiText:       h(0x000000),
            candiHighlight:  h(0xB28ABF)),
        // 5 放鬆綠 (Relax Green) — modifier bg #009444 (dark green), white label
        KeyboardPalette(
            background:      h(0x8DC63F),
            normalKey:       h(0x39B54A),
            modifierKey:     h(0x009444),
            pressedKey:      h(0x009444),
            label:           h(0x003A17),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xF2F5D5),
            candiText:       h(0x000000),
            candiHighlight:  h(0x39B54A)),
    ]

    /// Convenience: build a UIColor from a 24-bit RGB hex literal (e.g. 0xFAD5E5).
    static func h(_ rgb: UInt32) -> UIColor {
        UIColor(red:   CGFloat((rgb >> 16) & 0xFF) / 255,
                green: CGFloat((rgb >>  8) & 0xFF) / 255,
                blue:  CGFloat( rgb        & 0xFF) / 255,
                alpha: 1)
    }
}

protocol KeyboardViewDelegate: AnyObject {
    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef)
    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef)
    /// Called when a key with a non-empty `popupKeyboard` is long-pressed.
    /// `sourceRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect)
    /// Called on touchDown for non-modifier keys — host should show a key-preview popup.
    /// `keyRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect)
    /// Called on touchUp/cancel — host should dismiss the key preview.
    func keyboardViewDismissPreview(_ view: KeyboardView)
}

final class KeyboardView: UIView, UIInputViewAudioFeedback {
    /// Required by UIInputViewAudioFeedback so UIDevice.current.playInputClick() actually plays.
    /// The system only plays the click sound when the visible input view returns true here.
    var enableInputClicksWhenVisible: Bool { true }


    weak var delegate: KeyboardViewDelegate?

    private var layout: LimeKeyLayout
    private var isShiftOn: Bool = false
    private var rowViews: [UIView] = []
    private var repeatTimer: Timer?
    private var repeatKeyDef: KeyDef?
    private weak var globeButton: UIButton?

    // MARK: - Feedback settings (spec §15)
    var feedbackVibration: Bool = false
    var feedbackSound:     Bool = false
    var vibrateLevel: Int = 40  // 10–20→.light, 40→.medium, 60–80→.heavy

    private var impactFeedback: UIImpactFeedbackGenerator {
        let style: UIImpactFeedbackGenerator.FeedbackStyle
        switch vibrateLevel {
        case ..<30:  style = .light
        case 30..<50: style = .medium
        default:     style = .heavy
        }
        return UIImpactFeedbackGenerator(style: style)
    }

    // Layout constants (portrait).
    // Android key_height = 46dip portrait, 36dip landscape.
    // iOS row heights are scaled slightly larger to match modern iPhone proportions.
    private let rowHeightPortrait:       CGFloat = 52
    private let bottomRowHeightPortrait: CGFloat = 54
    private let rowHeightLandscape:      CGFloat = 36   // matches Android 36dip landscape
    private let bottomRowHeightLandscape:CGFloat = 38
    private let keyCornerRadius: CGFloat = 6
    private let keyShadowOpacity: Float  = 0.3
    // Gap between adjacent keys (horizontal) and between key and row edge (vertical).
    // Used in both makeRow layout and styleKeyContent aspect-ratio check.
    private let keyHGap: CGFloat = 5   // horizontal gap between keys
    private let keyVGap: CGFloat = 2   // vertical inset top/bottom

    /// Set by KeyboardViewController in viewWillLayoutSubviews; triggers a full rebuild.
    var isLandscape: Bool = false {
        didSet {
            guard isLandscape != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            buildKeys()
        }
    }

    /// Multiplier on row height (mirrors Android keySizeScale from getKeyboardSize()).
    /// Values: 0.8=特小 0.9=小 1.0=一般 1.1=大 1.2=特大. Set by KeyboardViewController from keyboard_size pref.
    var keySizeScale: CGFloat = 1.1 {
        didSet {
            guard keySizeScale != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            shiftKeyButton = nil
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// 0=none, 1=above keyboard, 2=below keyboard.
    var showArrowKey: Int = 0 {
        didSet {
            guard showArrowKey != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            shiftKeyButton = nil
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// When true (iPad only), each key row is split into left and right halves with a gap.
    var splitMode: Bool = false {
        didSet {
            guard splitMode != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            shiftKeyButton = nil
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// Double all row heights on iPad (spec: "if detected in ipad, double the size").
    private var idiomMultiplier: CGFloat {
        UIDevice.current.userInterfaceIdiom == .pad ? 1.5 : 1.0
    }

    private var rowHeight:       CGFloat { (isLandscape ? rowHeightLandscape       : rowHeightPortrait)       * keySizeScale * idiomMultiplier }
    private var bottomRowHeight: CGFloat { (isLandscape ? bottomRowHeightLandscape  : bottomRowHeightPortrait) * keySizeScale * idiomMultiplier }

    // MARK: - Theme
    /// Resolved theme index 0–5. Set by KeyboardViewController from resolvedKeyboardTheme.
    var theme: Int = 0 {
        didSet { guard oldValue != theme else { return }; applyTheme() }
    }
    private var palette: KeyboardPalette {
        KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
    }
    private var normalKeyColor:   UIColor { palette.normalKey }
    private var modifierKeyColor: UIColor { palette.modifierKey }
    private var pressedKeyColor:  UIColor { palette.pressedKey }

    private let keySingleLabelFont     = UIFont.systemFont(ofSize: 22, weight: .regular)

    private let keyLabelFont     = UIFont.systemFont(ofSize: 16, weight: .light)
    private let keySublabelFont  = UIFont.systemFont(ofSize: 22, weight: .regular)
    private let keyLabelFontLand     = UIFont.systemFont(ofSize: 16, weight: .light)
    private let keySublabelFontLand  = UIFont.systemFont(ofSize: 22, weight: .regular)

    // MARK: - Init
    init(layout: LimeKeyLayout) {
        self.layout = layout
        super.init(frame: .zero)
        backgroundColor = palette.background
        buildKeys()
    }
    required init?(coder: NSCoder) { fatalError("not used") }

    // MARK: - Layout switch
    func setLayout(_ newLayout: LimeKeyLayout) {
        layout = newLayout
        rowViews.forEach { $0.removeFromSuperview() }
        rowViews.removeAll()
        globeButton = nil
        shiftKeyButton = nil
        buildKeys()
        updateShiftKeyIcon()
    }

    /// Apply the current theme palette: update background and rebuild all key buttons.
    func applyTheme() {
        backgroundColor = palette.background
        rowViews.forEach { $0.removeFromSuperview() }
        rowViews.removeAll()
        globeButton = nil
        shiftKeyButton = nil
        buildKeys()
        updateShiftKeyIcon()
    }

    /// Sum of all row heights for the current layout, including the arrow row if shown.
    /// Use this in KeyboardViewController.applyHeight() instead of a flat constant.
    var preferredHeight: CGFloat {
        let base = layout.rows.reduce(0) { $0 + ($1.isBottomRow ? bottomRowHeight : rowHeight) }
        return base + (showArrowKey != 0 ? rowHeight : 0)
    }

    // MARK: - Shift state

    /// Three-state shift: off / one-shot / caps-lock (mirrors Android mCapsLock + isShifted).
    enum ShiftState { case off, on, capsLock }

    private(set) var shiftState: ShiftState = .off
    /// Weak ref to the shift key button — stored during buildKeys for icon updates.
    private weak var shiftKeyButton: UIButton?

    /// Update shift state and refresh the shift key icon.
    /// Call from KeyboardViewController.setShift(_:capsLock:).
    func setShiftState(_ state: ShiftState) {
        guard state != shiftState else { return }
        shiftState = state
        isShiftOn  = state != .off
        updateShiftKeyIcon()
    }

    private func updateShiftKeyIcon() {
        guard let btn = shiftKeyButton else { return }
        let iconName: String
        switch shiftState {
        case .off:      iconName = "shift"
        case .on:       iconName = "shift.fill"
        case .capsLock: iconName = "capslock.fill"
        }
        let cfg = UIImage.SymbolConfiguration(pointSize: 20, weight: .regular)
        btn.setImage(UIImage(systemName: iconName, withConfiguration: cfg), for: .normal)
        // Tint the shift key to show active state
        btn.tintColor = shiftState == .off ? palette.modifierLabel : .systemBlue
    }

    func setShift(_ on: Bool) {
        isShiftOn = on
    }

    /// Show or hide the globe key based on needsInputModeSwitchKey (spec §10).
    func setGlobeKeyVisible(_ visible: Bool) {
        globeButton?.isHidden = !visible
    }

    // MARK: - Build
    private func buildKeys() {
        var prevRow: UIView? = nil

        // Collect the rows to render, injecting the arrow row at position 0 (above) or at the end (below).
        var renderRows: [(row: KeyRow, index: Int, isArrow: Bool)] = []
        if showArrowKey == 1 {
            renderRows.append((arrowKeyRow, -1, true))
        }
        for (i, row) in layout.rows.enumerated() {
            renderRows.append((row, i, false))
        }
        if showArrowKey == 2 {
            renderRows.append((arrowKeyRow, -1, true))
        }

        for entry in renderRows {
            let rh = (!entry.isArrow && entry.row.isBottomRow) ? bottomRowHeight : rowHeight
            let rowView = splitMode
                ? makeSplitRow(row: entry.row, rowHeight: rh)
                : makeRow(row: entry.row, rowIndex: entry.index, rowHeight: rh)
            addSubview(rowView)
            rowViews.append(rowView)

            rowView.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                rowView.leadingAnchor.constraint(equalTo: leadingAnchor),
                rowView.trailingAnchor.constraint(equalTo: trailingAnchor),
                rowView.heightAnchor.constraint(equalToConstant: rh),
            ])

            if let prev = prevRow {
                rowView.topAnchor.constraint(equalTo: prev.bottomAnchor).isActive = true
            } else {
                rowView.topAnchor.constraint(equalTo: topAnchor).isActive = true
            }
            prevRow = rowView
        }

        if let last = prevRow {
            last.bottomAnchor.constraint(equalTo: bottomAnchor).isActive = true
        }
    }

    /// A row of four arrow keys used when showArrowKey != 0.
    private var arrowKeyRow: KeyRow {
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.arrowLeft.rawValue,  widthPercent: 25, icon: "arrow.left",  isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowUp.rawValue,    widthPercent: 25, icon: "arrow.up",    isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowDown.rawValue,  widthPercent: 25, icon: "arrow.down",  isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowRight.rawValue, widthPercent: 25, icon: "arrow.right", isRepeatable: true, isModifier: true),
        ], isBottomRow: false)
    }

    /// Renders a row split into left and right halves with a gap — iPad split-keyboard mode.
    private func makeSplitRow(row: KeyRow, rowHeight: CGFloat) -> UIView {
        let rowView = UIView()
        rowView.backgroundColor = .clear

        let keys = row.keys
        guard !keys.isEmpty else { return rowView }

        // Find split index: first key where cumulative widthPercent >= 50% of total
        let total = keys.reduce(0) { $0 + $1.widthPercent }
        var cumulative: CGFloat = 0
        var splitIndex = keys.count / 2
        for (i, k) in keys.enumerated() {
            cumulative += k.widthPercent
            if cumulative >= total / 2 {
                splitIndex = i + 1
                break
            }
        }

        let leftKeys  = Array(keys[..<splitIndex])
        let rightKeys = Array(keys[splitIndex...])
        let splitGapFraction: CGFloat = 0.06  // 6% gap in the middle

        func addHalf(_ halfKeys: [KeyDef], leading: Bool) {
            guard !halfKeys.isEmpty else { return }
            let halfPercent = halfKeys.reduce(0) { $0 + $1.widthPercent }
            let halfFraction = (halfPercent / total) * (1 - splitGapFraction)

            let contentView = UIView()
            contentView.backgroundColor = .clear
            contentView.translatesAutoresizingMaskIntoConstraints = false
            rowView.addSubview(contentView)
            NSLayoutConstraint.activate([
                contentView.topAnchor.constraint(equalTo: rowView.topAnchor),
                contentView.bottomAnchor.constraint(equalTo: rowView.bottomAnchor),
                contentView.widthAnchor.constraint(equalTo: rowView.widthAnchor, multiplier: halfFraction),
            ])
            if leading {
                contentView.leadingAnchor.constraint(equalTo: rowView.leadingAnchor).isActive = true
            } else {
                contentView.trailingAnchor.constraint(equalTo: rowView.trailingAnchor).isActive = true
            }

            var prevBtn: UIButton? = nil
            for keyDef in halfKeys {
                let btn = makeKeyButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: halfPercent)
                contentView.addSubview(btn)
                btn.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    btn.topAnchor.constraint(equalTo: contentView.topAnchor, constant: keyVGap),
                    btn.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -keyVGap),
                    btn.widthAnchor.constraint(equalTo: contentView.widthAnchor,
                                               multiplier: keyDef.widthPercent / halfPercent,
                                               constant: -keyHGap),
                ])
                if let prev = prevBtn {
                    btn.leadingAnchor.constraint(equalTo: prev.trailingAnchor, constant: keyHGap).isActive = true
                } else {
                    btn.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: keyHGap / 2).isActive = true
                }
                prevBtn = btn
            }
        }

        addHalf(leftKeys,  leading: true)
        addHalf(rightKeys, leading: false)
        return rowView
    }

    private func makeRow(row: KeyRow, rowIndex: Int, rowHeight: CGFloat) -> UIView {
        let rowView = UIView()
        rowView.backgroundColor = .clear

        // Total width percent for this row. When < 100, the keys are narrower than the
        // full row — center them with equal left/right whitespace via a content container.
        let totalPercent = row.keys.reduce(0) { $0 + $1.widthPercent }
        let widthMultiplier = min(1.0, totalPercent / 100.0)

        let contentView = UIView()
        contentView.backgroundColor = .clear
        contentView.translatesAutoresizingMaskIntoConstraints = false
        rowView.addSubview(contentView)
        NSLayoutConstraint.activate([
            contentView.centerXAnchor.constraint(equalTo: rowView.centerXAnchor),
            contentView.topAnchor.constraint(equalTo: rowView.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: rowView.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: rowView.widthAnchor, multiplier: widthMultiplier),
        ])

        var prevButton: UIButton? = nil

        for (_, keyDef) in row.keys.enumerated() {
            let btn = makeKeyButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
            contentView.addSubview(btn)

            btn.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                btn.topAnchor.constraint(equalTo: contentView.topAnchor, constant: keyVGap),
                btn.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -keyVGap),
                // Each key spans its proportional share of the content view width minus keyHGap,
                // so adjacent keys are separated by keyHGap pt of background.
                btn.widthAnchor.constraint(equalTo: contentView.widthAnchor,
                                           multiplier: keyDef.widthPercent / totalPercent,
                                           constant: -keyHGap),
            ])

            if let prev = prevButton {
                btn.leadingAnchor.constraint(equalTo: prev.trailingAnchor, constant: keyHGap).isActive = true
            } else {
                btn.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: keyHGap / 2).isActive = true
            }
            prevButton = btn
        }

        return rowView
    }

    private func makeKeyButton(keyDef: KeyDef, rowHeight: CGFloat, totalPercent: CGFloat) -> UIButton {
        // Space key: custom touch tracking avoids UISwipeGestureRecognizer conflicts and
        // prevents keyDown from firing didPress(space) before swipe/long-press is resolved.
        if keyDef.code == LimeKeyCode.space.rawValue {
            return makeSpaceButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
        }

        let btn = KeyButton(keyDef: keyDef)

        // Keyboard dismiss key (code -3):
        //   - single tap (touchUpInside): dismiss keyboard
        //   - long press: show options menu (globe preview, spec §10)
        // MUST use touchUpInside so the long-press GR can fire before the keyboard is dismissed.
        if keyDef.code == LimeKeyCode.done.rawValue {
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(specialLongPressed(_:)))
            lp.minimumPressDuration = 0.5
            btn.addGestureRecognizer(lp)
        }

        // Shift key: store reference for icon updates.
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftKeyButton = btn
        }

        // Legacy globe key (code -200): long-press also shows options menu.
        if keyDef.code == LimeKeyCode.globe.rawValue {
            globeButton = btn
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(specialLongPressed(_:)))
            lp.minimumPressDuration = 0.5
            btn.addGestureRecognizer(lp)
        }

        // Popup keyboard: long-press shows a mini keyboard panel (e.g. accent variants, punctuation)
        if !keyDef.popupKeyboard.isEmpty {
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(popupKeyLongPressed(_:)))
            lp.minimumPressDuration = 0.4
            btn.addGestureRecognizer(lp)
        }

        applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)

        btn.addTarget(self, action: #selector(keyDown(_:event:)), for: .touchDown)
        btn.addTarget(self, action: #selector(keyUp(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        // Done, globe, and popup keys fire didPress on touchUpInside (deferred so long-press can intercept)
        if keyDef.code == LimeKeyCode.done.rawValue || keyDef.code == LimeKeyCode.globe.rawValue
            || !keyDef.popupKeyboard.isEmpty {
            btn.addTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        }

        return btn
    }

    @objc private func popupKeyLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began, let keyBtn = gr.view as? KeyButton else { return }
        keyBtn.wasLongPressed = true
        let keyRect = keyBtn.convert(keyBtn.bounds, to: self)
        if feedbackVibration { impactFeedback.impactOccurred() }
        delegate?.keyboardView(self, didLongPressPopupKey: keyBtn.keyDef, sourceRect: keyRect)
    }

    /// Build the space key as a SpaceKeyButton so tap/swipe/long-press are mutually exclusive.
    private func makeSpaceButton(keyDef: KeyDef, rowHeight: CGFloat, totalPercent: CGFloat) -> UIButton {
        let btn = SpaceKeyButton(keyDef: keyDef)
        btn.restoreColor = normalKeyColor
        applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)

        btn.onTap = { [weak self] in
            guard let self else { return }
            if self.feedbackVibration { self.impactFeedback.impactOccurred() }
            if self.feedbackSound     { UIDevice.current.playInputClick() }
            self.delegate?.keyboardView(self, didPress: keyDef)
        }
        btn.onLongPress = { [weak self] in
            guard let self else { return }
            self.delegate?.keyboardView(self, didLongPress: keyDef)
        }
        btn.onSwipeLeft = { [weak self] in
            guard let self else { return }
            let kd = KeyDef(code: LimeKeyCode.prevIM.rawValue, isModifier: true)
            self.delegate?.keyboardView(self, didPress: kd)
        }
        btn.onSwipeRight = { [weak self] in
            guard let self else { return }
            let kd = KeyDef(code: LimeKeyCode.nextIM.rawValue, isModifier: true)
            self.delegate?.keyboardView(self, didPress: kd)
        }
        return btn
    }

    /// Apply background color, corner radius and shadow to any key button.
    private func applyButtonStyle(_ btn: UIButton, keyDef: KeyDef,
                                  rowHeight: CGFloat, totalPercent: CGFloat) {
        btn.backgroundColor = keyDef.isModifier ? modifierKeyColor : normalKeyColor
        btn.layer.cornerRadius = keyCornerRadius
        btn.layer.masksToBounds = false
        btn.layer.shadowColor = UIColor.black.cgColor
        btn.layer.shadowOffset = CGSize(width: 0, height: 1)
        btn.layer.shadowOpacity = keyShadowOpacity
        btn.layer.shadowRadius = 0
        styleKeyContent(btn: btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
    }

    /// Renders key content.
    /// Layout rule (mirrors Android keyLabel \n rendering):
    /// Mirrors Android LIMEKeyboardBaseView.adjustCase(): uppercase the label when shift is active,
    /// but only for short labels (≤3 chars) that start with a lowercase letter.
    /// Long modifier labels like "ABC", "中文", "Done" are left unchanged.
    private func adjustCase(_ label: String) -> String {
        guard isShiftOn, label.count <= 3,
              let first = label.unicodeScalars.first,
              CharacterSet.lowercaseLetters.contains(first) else { return label }
        return label.uppercased()
    }

    ///   • Tall key  (height ≥ width): label small top,  sublabel large bottom — vertical stack
    ///   • Wide key  (width  > height): label small left, sublabel large right  — horizontal stack
    private func styleKeyContent(btn: UIButton, keyDef: KeyDef,
                                 rowHeight: CGFloat, totalPercent: CGFloat) {
        let keyLabel = keyDef.isModifier ? palette.modifierLabel : palette.label
        if !keyDef.icon.isEmpty {
            // SF Symbol icon key — dismiss key uses a larger point size for legibility
            let iconSize: CGFloat = keyDef.icon == "keyboard.chevron.compact.down" ? 28 : 20
            let config = UIImage.SymbolConfiguration(pointSize: iconSize, weight: .regular)
            let img = UIImage(systemName: keyDef.icon, withConfiguration: config)
            btn.setImage(img, for: .normal)
            btn.tintColor = keyLabel
        } else if !keyDef.sublabel.isEmpty {
            // Actual button dimensions matching makeRow constraints:
            //   width  = screenWidth × (widthPct/totalPct) − keyHGap
            //   height = rowHeight − 2×keyVGap
            let screenWidth    = UIScreen.main.bounds.width
            let estimatedWidth = screenWidth * (keyDef.widthPercent / totalPercent) - keyHGap
            let usableHeight   = rowHeight - 2 * keyVGap
            // Tall: height ≥ width  →  vertical stack (primary top, sublabel bottom)
            // Wide: width > height  →  horizontal stack (primary left, sublabel right)
            let isTall = usableHeight >= estimatedWidth

            let displayLabel = adjustCase(keyDef.label)
            let container = makeDualLabelView(primary: displayLabel, sub: keyDef.sublabel,
                                              isTall: isTall, labelColor: keyLabel)
            container.isUserInteractionEnabled = false
            container.translatesAutoresizingMaskIntoConstraints = false
            btn.addSubview(container)
            NSLayoutConstraint.activate([
                container.centerXAnchor.constraint(equalTo: btn.centerXAnchor),
                container.centerYAnchor.constraint(equalTo: btn.centerYAnchor),
                container.widthAnchor.constraint(lessThanOrEqualTo: btn.widthAnchor, constant: -4),
            ])
        } else {
            // Single label key
            btn.setTitle(adjustCase(keyDef.label), for: .normal)
            btn.titleLabel?.font = keySingleLabelFont
            btn.setTitleColor(keyLabel, for: .normal)
        }

        // Popup-keyboard indicator: small "…" pinned to bottom-right corner
        if !keyDef.popupKeyboard.isEmpty {
            let dot = UILabel()
            dot.text = "…"
            dot.font = UIFont.systemFont(ofSize: 11, weight: .medium)
            dot.textColor = palette.secondaryLabel
            dot.isUserInteractionEnabled = false
            dot.translatesAutoresizingMaskIntoConstraints = false
            btn.addSubview(dot)
            NSLayoutConstraint.activate([
                dot.trailingAnchor.constraint(equalTo: btn.trailingAnchor, constant: -3),
                dot.bottomAnchor.constraint(equalTo: btn.bottomAnchor, constant: -2),
            ])
        }
    }

    /// Builds a two-part label view for keys that have both a primary label and a sublabel.
    /// - `isTall`: true → vertical (primary small top, sublabel large bottom)
    ///             false → horizontal (primary small left, sublabel large right)
    private func makeDualLabelView(primary: String, sub: String,
                                   isTall: Bool, labelColor: UIColor) -> UIView {
        let stack = UIStackView()
        stack.alignment = .center

        let primaryLbl = UILabel()
        primaryLbl.text = primary
        primaryLbl.textColor = palette.secondaryLabel
        primaryLbl.setContentHuggingPriority(.required, for: .horizontal)
        primaryLbl.setContentHuggingPriority(.required, for: .vertical)

        let subLbl = UILabel()
        subLbl.text = sub
        subLbl.textColor = labelColor
        subLbl.setContentHuggingPriority(.required, for: .horizontal)
        subLbl.setContentHuggingPriority(.required, for: .vertical)

        if isTall {
            // Vertical: primary (keyboard key char) small at top, sublabel (BPMF char) large below
            stack.axis = .vertical
            stack.spacing = 0
            primaryLbl.font = keyLabelFont
            subLbl.font     = keySublabelFont
            
            stack.addArrangedSubview(primaryLbl)
            stack.addArrangedSubview(subLbl)
        } else {
            // Horizontal: primary (keyboard key char) small on left, sublabel (BPMF char) on right
            stack.axis = .horizontal
            stack.spacing = 3
            primaryLbl.font = keyLabelFontLand
            subLbl.font     = keySublabelFontLand
            
            stack.addArrangedSubview(primaryLbl)
            stack.addArrangedSubview(subLbl)
        }
        return stack
    }

    // MARK: - Touch handling
    @objc private func keyDown(_ btn: UIButton, event: UIEvent) {
        guard let keyBtn = btn as? KeyButton else { return }
        keyBtn.wasLongPressed = false   // reset each new touch cycle
        btn.backgroundColor = pressedKeyColor

        let keyDef = keyBtn.keyDef

        // Haptic / audio feedback (spec §15)
        if feedbackVibration { impactFeedback.impactOccurred() }
        if feedbackSound     { UIDevice.current.playInputClick() }

        // Show key preview — delegate positions it in UIInputViewController.view (above keyboard)
        if keyDef.icon.isEmpty && !keyDef.isModifier
            && keyDef.code != LimeKeyCode.space.rawValue {
            let keyRect = btn.convert(btn.bounds, to: self)
            delegate?.keyboardView(self, showPreviewFor: keyDef, keyRect: keyRect)
        }

        // Keyboard dismiss key (code -3) and globe key (code -200): defer didPress to
        // touchUpInside so the long-press GR can fire before the action runs (spec §10).
        // Globe key must not fire advanceToNextInputMode() immediately on touchDown or the
        // keyboard switches before the long-press menu can appear.
        // All popup-keyboard keys: also deferred so the long-press popup can appear
        // before the primary action fires (prevents double-insert on non-modifier popup keys).
        let deferToTouchUp = keyDef.code == LimeKeyCode.done.rawValue
                          || keyDef.code == LimeKeyCode.globe.rawValue
                          || !keyDef.popupKeyboard.isEmpty
        if !deferToTouchUp {
            delegate?.keyboardView(self, didPress: keyDef)
        }

        // Start repeat timer for repeatable keys
        if keyDef.isRepeatable {
            repeatKeyDef = keyDef
            repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: false) { [weak self] _ in
                self?.startRepeating()
            }
        }
    }

    /// Fires `didPress` for the keyboard dismiss key on touchUpInside (see keyDown comment).
    /// Suppressed if the key was long-pressed (wasLongPressed flag) to prevent dismissing
    /// the keyboard immediately after the long-press options menu appears.
    @objc private func keyboardKeyTapped(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton, !keyBtn.wasLongPressed else { return }
        delegate?.keyboardView(self, didPress: keyBtn.keyDef)
    }

    @objc private func keyUp(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton else { return }
        let isModifier = keyBtn.keyDef.isModifier
        btn.backgroundColor = isModifier ? modifierKeyColor : normalKeyColor
        delegate?.keyboardViewDismissPreview(self)
        stopRepeating()
    }

    private func startRepeating() {
        repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self, let keyDef = self.repeatKeyDef else { return }
            self.delegate?.keyboardView(self, didPress: keyDef)
        }
    }

    private func stopRepeating() {
        repeatTimer?.invalidate()
        repeatTimer = nil
        repeatKeyDef = nil
    }

    /// Long-press handler for keyboard dismiss key and legacy globe key.
    @objc private func specialLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began, let keyBtn = gr.view as? KeyButton else { return }
        // Mark so touchUpInside (keyboardKeyTapped) does NOT fire dismiss/action after long press.
        keyBtn.wasLongPressed = true
        delegate?.keyboardView(self, didLongPress: keyBtn.keyDef)
    }
}

// MARK: - SpaceKeyButton
// Handles tap / swipe-left / swipe-right / long-press internally using raw touch tracking.
// This avoids the UISwipeGestureRecognizer + UIButton.touchDown conflict where a space
// character fires on touchDown before UIKit has a chance to recognise the swipe direction.
private final class SpaceKeyButton: KeyButton {
    var onTap:        (() -> Void)?
    var onLongPress:  (() -> Void)?
    var onSwipeLeft:  (() -> Void)?
    var onSwipeRight: (() -> Void)?
    /// Normal (unpressed) background color — set from the active palette when the button is created.
    var restoreColor: UIColor = .white

    private var touchBeganPoint: CGPoint = .zero
    private var longPressTimer:  Timer?
    private var actionFired = false  // swipe or long-press already handled for this touch

    private static let swipeThreshold:    CGFloat       = 30
    private static let longPressDuration: TimeInterval  = 0.5

    // Override all four touch methods WITHOUT calling super so that UIKit never sends
    // the .touchDown / .touchUpInside control events → keyDown/keyUp never fire for space.
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        touchBeganPoint = touch.location(in: self)
        actionFired = false
        backgroundColor = UIColor.systemGray5
        longPressTimer?.invalidate()
        longPressTimer = Timer.scheduledTimer(
            withTimeInterval: SpaceKeyButton.longPressDuration, repeats: false
        ) { [weak self] _ in
            guard let self, !self.actionFired else { return }
            self.actionFired = true
            self.resetBg()
            self.onLongPress?()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, !actionFired else { return }
        let dx = touch.location(in: self).x - touchBeganPoint.x
        if abs(dx) >= SpaceKeyButton.swipeThreshold {
            actionFired = true
            longPressTimer?.invalidate(); longPressTimer = nil
            resetBg()
            dx > 0 ? onSwipeRight?() : onSwipeLeft?()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate(); longPressTimer = nil
        resetBg()
        if !actionFired { onTap?() }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate(); longPressTimer = nil
        resetBg()
    }

    private func resetBg() {
        backgroundColor = restoreColor
    }
}

// MARK: - KeyButton: stores its KeyDef
private class KeyButton: UIButton {
    let keyDef: KeyDef
    /// Set to true when a UILongPressGestureRecognizer fires on this button.
    /// Used to suppress the subsequent touchUpInside (e.g. done key dismissing keyboard after long press).
    var wasLongPressed = false
    init(keyDef: KeyDef) {
        self.keyDef = keyDef
        super.init(frame: .zero)
    }
    required init?(coder: NSCoder) { fatalError("not used") }
}
