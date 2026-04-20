import UIKit

// Horizontal scrolling candidate bar above the keyboard.
// Mirrors Android's CandidateView.java (horizontal ListView).

protocol CandidateBarViewDelegate: AnyObject {
    func candidateBarView(_ view: CandidateBarView, didSelect mapping: Mapping)
    func candidateBarViewDidRequestMore(_ view: CandidateBarView)
}

final class CandidateBarView: UIView {

    weak var delegate: CandidateBarViewDelegate?

    // MARK: - Subviews
    private let scrollView  = UIScrollView()
    private let stackView   = UIStackView()
    private let moreButton  = UIButton(type: .system)
    private let moreSep     = UIView()          // fixed separator left of chevron

    // MARK: - Theme
    var theme: Int = 0 {
        didSet { guard oldValue != theme else { return }; applyTheme() }
    }
    private var palette: KeyboardPalette {
        KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
    }

    // MARK: - Feedback
    var feedbackVibration: Bool = false
    var vibrateLevel: Int = 40  // 10–20→.light, 40→.medium, 60–80→.heavy

    private var impactFeedback: UIImpactFeedbackGenerator {
        let style: UIImpactFeedbackGenerator.FeedbackStyle
        switch vibrateLevel {
        case ..<30:   style = .light
        case 30..<50: style = .medium
        default:      style = .heavy
        }
        return UIImpactFeedbackGenerator(style: style)
    }

    // MARK: - State
    private var candidates:  [Mapping] = []


    /// Currently-highlighted candidate index, or -1 when no cell should be drawn highlighted.
    /// Mirrors Android `CandidateView.mSelectedIndex` — associated lists (related phrases,
    /// Chinese punctuation, English suggestions) always leave this at -1.
    private var selectedIndex: Int = -1
    /// Read-only access to the current selection index for arrow-key navigation.
    var currentSelectedIndex: Int { selectedIndex }
    /// Number of candidates currently displayed.
    var candidateCount: Int { candidates.count }
    /// Button for each entry in `candidates`, in order. Used by `setSelectedIndex` to re-style
    /// individual cells without rebuilding the whole stack (preserves scroll offset).
    private var candidateButtons: [UIButton] = []

    /// Tags used to locate the two labels inside a selkey-prefixed button so
    /// `applyHighlightStyle` can update their colors on a selection change.


    // MARK: - Layout constants
    /// Mirrors Android `font_size` scaler from LIMEPreferenceManager.getFontSize().
    /// Applied to candidate/selkey/composing fonts. Set by KeyboardViewController.
    var fontScale: CGFloat = 1.1 {
        didSet { guard oldValue != fontScale else { return }; rebuildButtons() }
    }
    private let baseCandidateFontSize: CGFloat = 22
    private let baseComposingCodeFontSize: CGFloat = 16
    private var candidateFont: UIFont     { UIFont.systemFont(ofSize: baseCandidateFontSize * fontScale, weight: .regular) }
    private var composingCodeFont: UIFont { UIFont.monospacedSystemFont(ofSize: baseComposingCodeFontSize * fontScale, weight: .regular) }
    private let candidateHPad:   CGFloat = 10
    private let dividerWidth:    CGFloat = 1

    // MARK: - Init
    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }
    required init?(coder: NSCoder) { super.init(coder: coder); setup() }

    // MARK: - Setup
    private func applyTheme() {
        backgroundColor = palette.candiBackground
        moreButton.tintColor = palette.candiText
        moreSep.backgroundColor = palette.candiText.withAlphaComponent(0.2)
        rebuildButtons()
    }

    private func setup() {
        backgroundColor = palette.candiBackground

        // Fixed chevron pinned to the right edge of the bar
        moreButton.setImage(UIImage(systemName: "chevron.down"), for: .normal)
        moreButton.tintColor = palette.candiText
        moreButton.contentEdgeInsets = UIEdgeInsets(top: 0, left: 10, bottom: 0, right: 10)
        moreButton.isHidden = true
        moreButton.addTarget(self, action: #selector(moreTapped), for: .touchUpInside)
        moreButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(moreButton)

        moreSep.backgroundColor = palette.candiText.withAlphaComponent(0.2)
        moreSep.isHidden = true
        moreSep.translatesAutoresizingMaskIntoConstraints = false
        addSubview(moreSep)

        // Scroll view occupies the bar to the left of the fixed chevron
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scrollView)

        stackView.axis = .horizontal
        stackView.alignment = .center
        stackView.spacing = 0
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            // chevron flush to trailing edge — square so its width matches the bar
            // height exactly. Keeps the reserved zone consistent with the expanded
            // panel's collapse chevron (candidateBarHeight × candidateBarHeight).
            moreButton.trailingAnchor.constraint(equalTo: trailingAnchor),
            moreButton.topAnchor.constraint(equalTo: topAnchor),
            moreButton.bottomAnchor.constraint(equalTo: bottomAnchor),
            moreButton.widthAnchor.constraint(equalTo: moreButton.heightAnchor),

            // thin separator just left of the chevron
            moreSep.trailingAnchor.constraint(equalTo: moreButton.leadingAnchor),
            moreSep.centerYAnchor.constraint(equalTo: centerYAnchor),
            moreSep.widthAnchor.constraint(equalToConstant: dividerWidth),
            moreSep.heightAnchor.constraint(equalToConstant: 20),

            // scroll view fills the rest
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: moreSep.leadingAnchor),
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),
        ])
    }

    // MARK: - Public API

    /// Rotate the fixed chevron to indicate expanded (↑) or collapsed (↓) state.
    func setChevronExpanded(_ expanded: Bool) {
        let name = expanded ? "chevron.up" : "chevron.down"
        moreButton.setImage(UIImage(systemName: name), for: .normal)
    }

    /// Retained for API compatibility. The composing code is now rendered
    /// as the first candidate entry in the bar, so there is no dedicated
    /// left-edge label to update.
    func setComposingCode(_ code: String) { _ = code }

    /// Replace the candidate list with new results.
    ///
    /// - Parameters:
    ///   - mappings: the new candidate list.
    ///   - selectedIndex: the initial highlighted index. Pass `-1` (default) for associated
    ///     lists (related phrases, Chinese punctuation, English suggestions) so no cell is
    ///     drawn highlighted — matches Android `CandidateView.setSuggestions` rule.
    func setCandidates(_ mappings: [Mapping], selectedIndex: Int = -1) {
        candidates = mappings
        self.selectedIndex = (selectedIndex >= 0 && selectedIndex < mappings.count) ? selectedIndex : -1
        rebuildButtons()
        // layoutIfNeeded must come BEFORE setContentOffset(.zero).
        // During the layout pass UIScrollView internally adjusts contentOffset to
        // account for contentSize changes; calling setContentOffset after ensures
        // our zero value always wins and is not overwritten by UIScrollView's
        // internal adjustment. scrollSelectedIntoView is intentionally omitted
        // here — a fresh candidate list always starts at offset 0.
        scrollView.layoutIfNeeded()
        scrollView.setContentOffset(.zero, animated: false)
    }

    /// Append additional candidates to the existing list WITHOUT clearing buttons or
    /// resetting scroll. Used by background follow-up fetches so the user’s scroll
    /// position is undisturbed.
    ///
    /// If `mappings` starts with the same items already displayed the method only
    /// adds the net-new tail. If `mappings` is not a superset of the current list
    /// (e.g. the composing code changed) it falls back to a full `setCandidates`.
    func appendCandidates(_ mappings: [Mapping], selectedIndex: Int = -1) {
        // Preserve scroll position across the stage-2 upgrade.
        // setCandidates now calls layoutIfNeeded() BEFORE setContentOffset(.zero),
        // so when it returns layout is fully settled and there are no pending async
        // scroll adjustments. Overriding the .zero immediately after is safe.
        let preservedX = scrollView.contentOffset.x
        setCandidates(mappings, selectedIndex: selectedIndex)
        guard preservedX > 0 else { return }
        let maxX = max(0, scrollView.contentSize.width - scrollView.bounds.width)
        guard maxX > 0 else { return }
        scrollView.setContentOffset(CGPoint(x: min(preservedX, maxX), y: 0), animated: false)
    }

    /// Update the highlighted index without rebuilding buttons (preserves scroll offset).
    /// Pass `-1` to clear the highlight.
    func setSelectedIndex(_ index: Int) {
        let clamped = (index >= 0 && index < candidates.count) ? index : -1
        guard clamped != selectedIndex else { return }
        let old = selectedIndex
        selectedIndex = clamped
        if old >= 0 && old < candidateButtons.count {
            applyHighlightStyle(button: candidateButtons[old], index: old, mapping: candidates[old])
        }
        if clamped >= 0 && clamped < candidateButtons.count {
            applyHighlightStyle(button: candidateButtons[clamped], index: clamped, mapping: candidates[clamped])
        }
        scrollSelectedIntoView(animated: true)
    }

    /// Mirrors Android `candidate_switch` pref (CandidateView.java:314).
    /// true  = free drag-scroll (scrollView follows finger continuously)
    /// false = paged: drag is suppressed, on release snap to next/prev candidate page
    var candidateSwitch: Bool = true {
        didSet {
            guard oldValue != candidateSwitch else { return }
            scrollView.isScrollEnabled = candidateSwitch
            if candidateSwitch {
                if let gr = pagingPanGesture { scrollView.removeGestureRecognizer(gr) }
                pagingPanGesture = nil
                pagingStartOffsetX = 0
            } else {
                let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePagingPan(_:)))
                scrollView.addGestureRecognizer(pan)
                pagingPanGesture = pan
            }
        }
    }
    private weak var pagingPanGesture: UIPanGestureRecognizer?
    private var pagingStartOffsetX: CGFloat = 0

    @objc private func handlePagingPan(_ gr: UIPanGestureRecognizer) {
        switch gr.state {
        case .began:
            pagingStartOffsetX = scrollView.contentOffset.x
        case .ended, .cancelled:
            let dx = gr.translation(in: scrollView).x
            // Ignore tiny drags — tap/select will handle them.
            guard abs(dx) > 20 else { return }
            if dx < 0 { scrollNextPage() } else { scrollPrevPage() }
        default:
            break
        }
    }

    /// Scroll right by one visible-width page, aligned to the first candidate
    /// that would fall at/beyond the left edge of the new viewport.
    private func scrollNextPage() {
        let w = scrollView.bounds.width
        let maxX = max(0, scrollView.contentSize.width - w)
        let target = min(pagingStartOffsetX + w, maxX)
        scrollView.setContentOffset(CGPoint(x: alignedOffset(for: target, preferLeft: true), y: 0), animated: true)
    }

    /// Scroll left by one visible-width page, aligned to a candidate boundary.
    private func scrollPrevPage() {
        let w = scrollView.bounds.width
        let target = max(0, pagingStartOffsetX - w)
        scrollView.setContentOffset(CGPoint(x: alignedOffset(for: target, preferLeft: false), y: 0), animated: true)
    }

    /// Snap a scroll offset to the nearest candidate button x-position so pages
    /// align on candidate boundaries (mirrors Android scrollPrev/scrollNext which
    /// use mWordX[] to snap to word boundaries).
    private func alignedOffset(for target: CGFloat, preferLeft: Bool) -> CGFloat {
        guard !candidateButtons.isEmpty else { return target }
        var best: CGFloat = 0
        var bestDist: CGFloat = .greatestFiniteMagnitude
        for btn in candidateButtons {
            let x = btn.frame.minX
            let d = abs(x - target)
            if d < bestDist { bestDist = d; best = x }
        }
        return best
    }

    // MARK: - Private

    private func rebuildButtons() {
        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        candidateButtons.removeAll(keepingCapacity: true)

        for (index, mapping) in candidates.enumerated() {
            let btn = makeCandidateButton(mapping: mapping, index: index)
            stackView.addArrangedSubview(btn)
            candidateButtons.append(btn)
            applyHighlightStyle(button: btn, index: index, mapping: mapping)
        }

        // Show/hide the fixed chevron depending on whether there are candidates
        let hasCandidates = !candidates.isEmpty
        moreButton.isHidden = !hasCandidates
        moreSep.isHidden    = !hasCandidates
    }

    private func makeCandidateButton(mapping: Mapping, index: Int) -> UIButton {
        let btn = UIButton(type: .system)
        btn.tag = index
        btn.contentEdgeInsets = UIEdgeInsets(top: 0, left: candidateHPad, bottom: 0, right: candidateHPad)
        btn.addTarget(self, action: #selector(candidateTapped(_:)), for: .touchUpInside)

        // Composing-code record (mixed-mode raw-code entry): styled grey/monospace
        // so the user can visually distinguish it as "commit the raw English letters".
        // Mirrors Android mColorComposingCode.
        let isComposingCode = mapping.isComposingCodeRecord

        btn.setTitle(mapping.word, for: .normal)
        if isComposingCode {
            btn.titleLabel?.font = composingCodeFont
            btn.setTitleColor(palette.candiText.withAlphaComponent(0.5), for: .normal)
        } else {
            btn.titleLabel?.font = candidateFont
            btn.setTitleColor(palette.candiText, for: .normal)
        }
        return btn
    }

    /// Paint selection-dependent background and text color on a single candidate button.
    /// Mirrors Android `CandidateView.doDraw` highlight branch + per-record-type color switch.
    private func applyHighlightStyle(button: UIButton, index: Int, mapping: Mapping) {
        let isSelected = (index == selectedIndex && selectedIndex >= 0)
        let isComposingCode = mapping.isComposingCodeRecord
        // Themes 0 (Light) and 1 (Dark) use the iOS system keyboard key-cap style pill
        // (white on light, elevated gray on dark) — matches native QuickType bar look.
        // Coloured themes keep their Android palette highlight colour.
        let highlightColor: UIColor
        if theme <= 1 {
            highlightColor = UIColor(dynamicProvider: { t in
                t.userInterfaceStyle == .dark
                    ? UIColor(white: 0.23, alpha: 1)   // elevated on #141414 dark background
                    : UIColor.white                    // white pill on #FAFAFA light background
            })
        } else {
            highlightColor = palette.candiHighlight
        }

        if isSelected {
            button.backgroundColor = highlightColor
            button.layer.cornerRadius = 6
            button.layer.masksToBounds = true
        } else {
            button.backgroundColor = .clear
            button.layer.cornerRadius = 0
        }

        if isComposingCode {
            // Selected composing-code gets full opacity (mirrors mColorComposingCodeHighlight).
            let color = isSelected ? palette.candiText : palette.candiText.withAlphaComponent(0.5)
            button.setTitleColor(color, for: .normal)
        } else {
            button.setTitleColor(palette.candiText, for: .normal)
        }
    }

    /// Scroll so the highlighted cell is visible. No-op when selection is cleared.
    /// Mirrors Android's scrollNext/scrollPrev behavior on selection change.
    private func scrollSelectedIntoView(animated: Bool) {
        guard selectedIndex >= 0, selectedIndex < candidateButtons.count else { return }
        let btn = candidateButtons[selectedIndex]
        // Force a layout so btn.frame is valid immediately after rebuildButtons.
        stackView.layoutIfNeeded()
        let rect = btn.convert(btn.bounds, to: scrollView)
        if !scrollView.bounds.contains(rect) {
            scrollView.scrollRectToVisible(rect, animated: animated)
        }
    }

    @objc private func candidateTapped(_ sender: UIButton) {
        let index = sender.tag
        guard index < candidates.count else { return }
        if feedbackVibration { impactFeedback.impactOccurred() }
        // Flash the highlight on the tapped cell before the commit animates.
        setSelectedIndex(index)
        delegate?.candidateBarView(self, didSelect: candidates[index])
    }

    @objc private func moreTapped() {
        if feedbackVibration { impactFeedback.impactOccurred() }
        delegate?.candidateBarViewDidRequestMore(self)
    }
}
