---
plan: Rewrite Root Cause + Proposed Next Solution sections of SAFARI_EXTRA_SPACE.md
status: draft for review (plan mode)
target file: docs/SAFARI_EXTRA_SPACE.md
---

# Plan — Rewrite RC and Proposed Next Solution in SAFARI_EXTRA_SPACE.md

## Context

`docs/SAFARI_EXTRA_SPACE.md` documents an extra rounded top area visible
above LimeIME's candidate bar in Safari, Google search, and Gemini, but
not above Apple's built-in phonetic keyboard. The geometry proof in the
existing doc already establishes that LimeIME's root view, candidate bar,
and composing label are flush at `y = 0` inside the extension window.

The current "Root Cause Analysis" section names the right general region
("host-owned area outside LimeIME's input view") but does not point at a
concrete iOS / WebKit class. The current "Proposed Next Solution" section
asks for one more host-context probe but does not commit to a specific
classification table or to closing the bug as host-owned where appropriate.

External research across five facets (App Extension Programming Guide,
WKWebView form-assistant, iOS 18/26 Apple Intelligence accessory bars,
host-layout reports for third-party keyboards, and `UITextInputAssistantItem`
suppression behavior) converged on a small, well-cited set of actors. We
also have a strong in-repo cross-reference: `IPAD_ASSIST_BAR.md` §8.1 already
proves that `UIInputViewController.inputAssistantItem` setters are silently
ignored from the extension side — the assistant bar belongs to the host text
view's responder chain, not the keyboard extension.

This plan proposes a targeted rewrite of two sections only:

1. **Root Cause Analysis** — replace the generic host-owned wording with the
   specific concrete actors and cite the evidence (Apple docs, Apple
   Developer Forums threads, in-repo proofs).
2. **Proposed Next Solution** — narrow the next move to a one-shot
   classification probe with a concrete decision table that closes
   non-LimeIME cases as "won't fix from extension side" and points at the
   right Feedback Assistant references.

No other sections of `SAFARI_EXTRA_SPACE.md` are changed. The geometry log,
"What We Tried", "How iOS Measures Our Keyboard Input View Height", and
"Is The Extra Padding Caused By Background Color?" remain as-is.

---

## Critical files

- `docs/SAFARI_EXTRA_SPACE.md` (only file edited)
- Cross-references to keep / add:
  - `docs/IPAD_ASSIST_BAR.md` §8.1 (inputAssistantItem ignored)
  - `docs/IOS_POPUP_COMPOSING.md` (window-attached overlay infeasibility)
  - `docs/CANDI_LAYOUT.md` (candidate-bar geometry, already linked)

No source code changes in this plan. The probe in §"Proposed Next Solution"
is a separate follow-up implementation step that will land later as a
DEBUG-only logging change in `KeyboardViewController.swift`.

---

## External research synthesis (raw)

Five parallel `document-specialist` agents ran web searches against Apple
developer docs, Apple Developer Forums, Apple Community threads, WWDC
session pages, OpenRadar, and reference open-source iOS apps. The
conclusions and citations below are what the rewrite is grounded in.

### Facet A — UIInputViewController layout & system QuickType strip

- **Apple App Extension Programming Guide** is explicit: *"A custom
  keyboard can draw only within the primary view of its
  `UIInputViewController` object. ... It is not possible to display key
  artwork above the top edge of a custom keyboard's primary view, as the
  system keyboard does on iPhone when you tap and hold a key in the top
  row."* — `https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html`
- iOS does **not** inject a system QuickType / predictive-text strip
  above a third-party keyboard. Third-party keyboards must draw their own
  prediction inside `UIInputViewController.view`. `requestSupplementaryLexicon`
  only provides dictionary words — it does not draw a system bar.
- `UIInputViewController` properties (`needsInputModeSwitchKey`,
  `hasDictationKey`, `hasFullAccess`, `requestSupplementaryLexicon`,
  `textInputMode`) do not affect the top inset.
- **Verdict:** the QuickType-strip hypothesis is **ruled out**. Source:
  `https://developer.apple.com/documentation/uikit/uiinputviewcontroller`,
  `https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard`.

### Facet B — WKWebView form-assistant `inputAccessoryView`

- Safari, Gemini web app, and Google search render HTML inputs inside a
  `WKWebView`. The first responder in WKWebView is the private
  `WKContentView`, which carries an `inputAccessoryView` (Prev / Next /
  Done toolbar). It is hosted in `UIInputSetHostView` inside
  `UIRemoteKeyboardWindow`, *between* the host content and the keyboard
  extension's own window — i.e. outside the extension's drawable area.
- Removable only from the host app side: subclass `WKWebView` and override
  `inputAccessoryView` to return `nil` (iOS 13+, public-API safe) or
  swizzle `WKContentView` (private API). A keyboard extension has zero
  ability to suppress it. Open-source proof:
  `holzschu/a-shell` `WkWebViewExtension.swift`.
- Safari's URL bar is a native `UITextField`, not a `WKWebView` input —
  it has a different (often empty) accessory. This explains why the URL
  bar can look flush while Gemini / Google `<input>` paths do not.
- **Verdict:** WKWebView `inputAccessoryView` is the **most likely
  primary cause** for iOS ≤ 25 in WKWebView form fields. Sources:
  rdar://27763084 (`https://openradar.appspot.com/27763084`),
  `https://www.technetexperts.com/hide-ios-input-bar/`,
  `https://blog.opendigerati.com/the-eccentric-ways-of-ios-safari-with-the-keyboard-b5aa3f34228d`,
  `https://cbess.blogspot.com/2019/09/remove-update-or-replace-wkwebview.html`,
  Apple Developer Forums thread 81650.

### Facet C — iOS 17 / 18 / 26 Apple-Intelligence accessory bars

- **Writing Tools (iOS 18.1+):** floating strip triggered by text
  *selection*, not always-on. Not the cause of a persistent extra space.
  Source: WWDC24 session 10168 (`https://developer.apple.com/videos/play/wwdc2024/10168/`).
- **Genmoji / Image Playground (iOS 18.2+):** dedicated panel, not a
  keyboard accessory bar. Ruled out.
- **iOS 26 Quick Actions / App Shortcuts bar:** new system-drawn
  rounded-rectangle strip above the keyboard in web form contexts,
  showing password / Apple Pay / location / checklist icons. Not
  developer-disable-able. Sources: Apple Community thread 256177528
  (`https://discussions.apple.com/thread/256177528`), thread 256173518
  (`https://discussions.apple.com/thread/256173518`), MacRumors thread
  2465255 (`https://forums.macrumors.com/threads/what-is-the-odd-bar-above-keyboard-merged.2465255/`).
- **iOS 26 keyboard host corner-radius / Liquid Glass change:** the host
  container that wraps third-party keyboard extensions now uses a larger
  rounded radius. Confirmed by Simon Støvring's KeyboardToolbar fix post
  (`https://mastodon.social/@simonbs/114708393305551029`) and Apple
  Developer Forums thread 802159
  (`https://developer.apple.com/forums/thread/802159`).
- **Verdict:** iOS 26 Quick Actions bar + Liquid Glass host insets are
  the **most likely cause on iOS ≥ 26**.

### Facet D — Third-party-keyboard host artifacts vs Apple keyboards

- **Apple Developer Forums thread 800838**
  (`https://developer.apple.com/forums/thread/800838`) — confirmed in
  iOS 26: third-party keyboard extensions in Liquid-Glass-redesigned
  host apps (Messages, Notes, Safari) receive *grey margins on the left,
  right, and top* that the extension cannot draw over. Owned by the
  private `UIInputSetHostView` window stack. No public API to override.
  No Apple response on the thread (26 boosts as of fetch).
- **Globe button** is a *bottom-area* floating icon on iPhone X+ —
  rendered in the home-indicator strip *below* the keyboard, not above.
  Not the cause of a top artifact. Sources: App Extension Programming
  Guide; Apple Developer Forums thread 90937
  (`https://developer.apple.com/forums/thread/90937`), thread 92030
  (`https://developer.apple.com/forums/thread/92030`).
- **`UIInputSetHostView`** is private. Apple's stated position (Apple
  Frameworks Engineer, forum thread 712543,
  `https://developer.apple.com/forums/thread/712543`): *"the SDK has no
  public class named `UIInputSetHostView`, that indicates that its
  behavior is entirely up to the system, and that you should not be
  trying to interact with it or modify that behavior."*
- **Verdict:** the rounded host container *is* the iOS 26 cause; on
  iOS ≤ 25 there is no widely-reported symmetric host-owned top strip
  that affects only third-party keyboards in portrait iPhone, which
  pushes the iOS-≤-25 cause back onto WKWebView's accessory view.

### Facet E — `UITextInputAssistantItem` / traits / mitigations

- **`UIResponder.inputAssistantItem.leadingBarButtonGroups` /
  `.trailingBarButtonGroups`** set on `self` from inside
  `UIInputViewController` are *silently ignored* on the on-screen iPad
  shortcut bar. The bar reads from the *host text view's* responder
  chain, not the keyboard extension's. **Already proven in this repo**
  at `docs/IPAD_ASSIST_BAR.md` §8.1 with device-confirmed evidence
  across iOS 15–17. iPhone has no shortcut bar at all, so this API is
  irrelevant to the Safari / Gemini iPhone case.
- **Password AutoFill / `textContentType: .oneTimeCode`, `.password`,
  `.username`:** Apple Developer Forums thread 94889
  (`https://developer.apple.com/forums/thread/94889`) — for fields
  recognized as credential-AutoFill targets, iOS *blocks third-party
  keyboards from being selected* (iOS 11+). Therefore the Password
  AutoFill bar cannot be the cause of an artifact LimeIME sees: LimeIME
  would not be the active keyboard there.
- **Other `UITextInputTraits`** (`keyboardType`, `returnKeyType`,
  `autocorrectionType`, `spellCheckingType`, `smartDashes/QuotesType`,
  `smartInsertDeleteType`, `secureTextEntry`) are read by the extension
  from `textDocumentProxy`. None of them are documented to insert or
  suppress host accessory bars. They do influence which accessory the
  *host* shows: `keyboardType == .URL` (Safari URL bar) suppresses the
  WKWebView form-assistant Prev / Next bar; `.default` (Gemini textarea)
  and `.webSearch` (Google search) do not.
- **Info.plist keys** (`RequestsOpenAccess`, `IsASCIICapable`,
  `PrimaryLanguage`) do not affect host accessory rendering.
- **Verdict:** there is no extension-side `UITextInputAssistantItem` or
  trait knob that suppresses the visible artifact. The only reachable
  signal is *classification* — read `keyboardType` from `textDocumentProxy`
  to confirm which host path (URL bar vs WKWebView form input) the
  artifact correlates with.

### Convergence

The five facets agree on the same conclusion from independent angles:
the artifact is a system / host surface above the keyboard extension
window that the extension cannot reach. The two dominant actors are:

- iOS ≤ 25 in WKWebView form fields → WKWebView `inputAccessoryView`.
- iOS ≥ 26 in any Liquid-Glass-redesigned host → `UIInputSetHostView`
  rounded host insets + Quick Actions bar.

The rewrite below incorporates these findings.

| Candidate cause | Verdict | Evidence |
| --- | --- | --- |
| Candidate-bar internal layout | Ruled out | Geometry log: candidate `y == 0`, composingLabel `y == 0` |
| Background color of extension | Ruled out | Failed root-background experiment in §"What We Tried" |
| System QuickType strip injected above third-party keyboards | Ruled out | App Extension Programming Guide: "A custom keyboard can draw only within the primary view" — system does not inject a prediction strip above third-party extensions |
| WKWebView `inputAccessoryView` (Prev / Next / Done bar) on form fields | **Most likely on iOS ≤ 25 in Gemini / Google `<input>` / `<textarea>`** | rdar://27763084; technetexperts hide-ios-input-bar; Apple Developer Forums thread 81650; owned by `WKContentView` — not reachable from a keyboard extension |
| iPad shortcut bar via `UITextInputAssistantItem` | Real on iPad, not the iPhone Safari/Gemini/Google case | `docs/IPAD_ASSIST_BAR.md` §8.1 — extension-side setter silently ignored; iPhone has no shortcut bar |
| iOS 18 Password / OTP AutoFill strip | Not for credential fields with LimeIME active | Apple Developer Forums thread 94889 — credential fields block third-party keyboards; LimeIME would not be the active keyboard there |
| iOS 26 Liquid Glass keyboard host insets (grey margin around extension) | **Most likely on iOS ≥ 26 in any host** | Apple Developer Forums thread 800838 — host owns rounded container; extension cannot draw into it |
| iOS 26 Quick Actions / App Shortcuts bar (key / card / location / checklist) | **Likely additional contributor on iOS ≥ 26 in web form contexts** | Apple Community threads 256177528, 256173518; user-visible rounded strip; not developer-disable-able |
| iOS 18 Writing Tools accessory | Conditional (selection-triggered) — not the always-on cause | WWDC24 session 10168; appears only when text is selected |
| Genmoji / Image Playground picker | Not a top accessory bar at all | Opens dedicated panel, not relevant |

The two main actors are therefore version-gated:

- **iOS ≤ 25, Safari / WKWebView form fields** → WKWebView `inputAccessoryView`
  (Prev / Next / Done) is the dominant rounded strip above the keyboard. Safari
  URL bar uses a native `UITextField`, which is why the URL-bar path can look
  fine while Gemini and Google search do not.
- **iOS ≥ 26, any Liquid-Glass-redesigned host** → the host-owned rounded
  container plus the new Quick Actions bar add an extra grey rounded inset
  around / above the third-party extension. This is system-wide and not
  WKWebView-specific.

In all variants, the surface is **outside `view.window`** of the keyboard
extension and outside the layout we control with Auto Layout constraints.

---

## Proposed rewrite — "Root Cause Analysis" section

> Replaces lines roughly 149–203 of `SAFARI_EXTRA_SPACE.md`. Existing geometry
> proof and "What We Tried" sections above remain unchanged; this section is
> reworked in place.

```markdown
## Root Cause Analysis

The root cause is not candidate-bar layout, not the height constraint, and
not the keyboard background color. The geometry log proves the candidate
bar and composing label are at `y = 0` inside the extension window, and the
failed root-background experiment proves background color does not change
iOS's allocation decision.

The extra rounded top space is system / host UI drawn ABOVE LimeIME's
keyboard extension window, in a layer the extension fundamentally cannot
reach. There are several actors, and which one appears depends on the
host context and iOS version.

### Actors that draw above the third-party keyboard window

1. **WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done bar)**
   — owned by `WKContentView`, the private first responder inside any
   `WKWebView` (Safari page content, Gemini web app, Google search box).
   Rendered by the host app process above the keyboard. Appears for any
   focused HTML `<input>` / `<textarea>`. Has rounded top corners on modern
   iOS, which matches the visible artifact. A keyboard extension has zero
   API to suppress or restyle it. Removing it is only possible from the
   host app side, by swizzling `WKContentView.inputAccessoryView` to `nil`,
   which Safari / Gemini / Google do not do. The Safari URL bar is a native
   `UITextField`, not a `WKWebView` input — it does not show this bar,
   which explains why the URL-bar path can look flush while Gemini /
   Google `<input>` paths do not.
   - Source: rdar://27763084; Apple Developer Forums thread 81650;
     `https://www.technetexperts.com/hide-ios-input-bar/`.

2. **iOS 26 Liquid Glass keyboard host insets** — starting in iOS 26,
   host apps that adopt the Liquid Glass design (Messages, Notes, Safari,
   etc.) embed third-party keyboards inside a rounded container with grey
   margins on the left, right, and top. The margin region is owned by the
   `UIInputSetHostView` window stack and is not drawable by the extension.
   This affects both web and native hosts on iOS ≥ 26.
   - Source: Apple Developer Forums thread 800838.

3. **iOS 26 Quick Actions / App Shortcuts bar** — a new system rounded-
   rectangle bar above the keyboard in iOS 26 web form contexts, showing
   passwords / Apple Pay / location / checklist icons. System-owned; not
   developer-disable-able from the extension or the host.
   - Source: Apple Community threads 256177528, 256173518.

4. **iPad `UITextInputAssistantItem` shortcut bar** — the host text view's
   shortcut bar above the keyboard on iPad. Not relevant on iPhone.
   `docs/IPAD_ASSIST_BAR.md` §8.1 already documents that setting
   `self.inputAssistantItem.trailingBarButtonGroups` from inside
   `UIInputViewController` is silently ignored — the assistant item the
   on-screen bar reads belongs to the host text view's responder chain,
   not the extension.

### Actors that are NOT the cause

- **System QuickType / predictive-text strip.** Apple's App Extension
  Programming Guide is explicit that a custom keyboard can draw only
  within the primary view of its `UIInputViewController`. iOS does not
  inject a system QuickType strip above a third-party keyboard. Apple's
  built-in keyboard renders QuickType inside its own view, which is why
  it can blend visually; LimeIME provides its own candidate bar inside
  its own view.
- **Genmoji / Image Playground picker.** Opens a separate panel, not a
  top accessory.
- **iOS 18 Writing Tools accessory.** Selection-triggered, not always-on.
- **Password AutoFill strip on credential fields.** iOS blocks third-party
  keyboards from being selected for fields recognized for password
  AutoFill (Apple Developer Forums thread 94889), so LimeIME would not be
  active there at all — this case cannot produce the observed artifact.
- **Background color of the extension.** Already proven irrelevant.

### Why Apple's phonetic keyboard does not show the same gap

Apple's built-in keyboards are not hosted as third-party extensions. They
run on a private hosting path that can integrate predictive UI, accessory
chrome, and the Quick Actions strip directly with the keyboard surface.
Third-party extensions render in a separate keyboard window that the host
chrome cannot merge with, so the same accessory chrome that visually
blends above Apple's keyboard appears as a distinct rounded rectangle
above LimeIME.

### Conclusion

The artifact is host- and OS-owned, not LimeIME-owned. There is no
keyboard-extension API that removes WKWebView's form-assistant accessory,
the iOS 26 host inset, or the iOS 26 Quick Actions bar. Candidate-bar
geometry, background fills, cover strips, and `UIVisualEffectView`
backdrops are the wrong tool for this category of bug. They were tried
and failed for exactly that reason.
```

---

## Proposed rewrite — "Proposed Next Solution" section

> Replaces lines roughly 305–355 of `SAFARI_EXTRA_SPACE.md`. Drops the
> "phone-only top cover strip" / candidate-layout-edit options.

```markdown
## Proposed Next Solution

Do not change candidate-bar geometry, `stripHeight`, candidate insets, or
add new root subviews for this issue. The failure mode is host-owned. The
next step is a one-shot classification probe and a Feedback Assistant
report, not another visual workaround.

### Step 1 — One-shot DEBUG classification probe

Add temporary DEBUG-only logging in `KeyboardViewController` that fires
once per focus change. For each focused field, log:

- iOS context
  - `UIDevice.current.systemVersion` (major version determines whether
    iOS 26 Liquid Glass host insets are in play)
  - `traitCollection.userInterfaceIdiom`
  - `traitCollection.horizontalSizeClass`, `verticalSizeClass`
- Host text input traits (read from `textDocumentProxy`)
  - `keyboardType.rawValue` (`.URL` = 3, `.webSearch` = 9, `.default` = 0,
    `.emailAddress`, etc.)
  - `returnKeyType.rawValue`
  - `autocorrectionType.rawValue`
  - `smartDashesType.rawValue`, `smartQuotesType.rawValue`
- Geometry (already partially logged; keep these)
  - `view.frame`, `view.bounds`, `view.safeAreaInsets`
  - `view.window?.frame`, `view.window?.bounds`,
    `view.window?.safeAreaInsets`
  - `candidateBar.frame`, candidateBar-in-window
  - `keyboardView.frame`
  - root height-constraint constant
- Optional, diagnosis-only
  - class names of `view.window`'s immediate subviews / superviews
    (do not depend on private class names in production code)

Capture matched logs and screenshots for these scenarios:

- Safari URL bar (no extra space expected — native `UITextField`)
- Google search `<input>` (extra space expected — WKWebView accessory)
- Gemini textarea (extra space expected — WKWebView accessory)
- Apple's built-in phonetic keyboard in the same Gemini / Google context
  (visual baseline only — Apple's private hosting path; we cannot log
  internals there)

Run the same set on iOS 25 and iOS 26 if both are available, since the
expected actors differ by major version.

### Step 2 — Decision table

| Finding | Classification | Action |
|---|---|---|
| Candidate bar `y > 0` or composing label `y > 0` in any future log | LimeIME internal layout regressed | Fix candidate / composing constraints. Bug is in LimeIME. |
| Candidate bar `y == 0`, composing label `y == 0`, AND `keyboardType` is `.default` / `.emailAddress` etc. on iOS ≤ 25 in Safari / Gemini / Google web form | WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done) | Host-owned. Not fixable from the extension. Document and stop. |
| Same geometry on iOS ≤ 25 in Safari URL bar | Native `UITextField` — different accessory or none | If URL bar still shows extra space, capture and re-classify; otherwise expected behavior. |
| iOS ≥ 26 in any host (web or native) | iOS 26 Liquid Glass host insets and / or Quick Actions bar | Host-owned. Not fixable from the extension. File Feedback Assistant referencing forum thread 800838. |
| iPad-only artifact above keyboard | `UITextInputAssistantItem` shortcut bar | See `docs/IPAD_ASSIST_BAR.md` §8.1 — extension-side suppression is silently ignored. Not fixable from the extension. |

### Step 3 — Close-out

For every row except the first, the action is the same: stop iterating on
LimeIME layout for this case, mark the bug as host / OS-owned, and either
file Feedback Assistant or accept the behavior. Specifically:

- File or reference an existing Feedback Assistant ticket referencing
  rdar://27763084 (allow disabling default `inputAccessoryView` on
  WKWebView) for the iOS ≤ 25 case.
- File Feedback Assistant referencing Apple Developer Forums thread
  800838 (iOS 26 Liquid Glass extra grey margin around third-party
  keyboard extensions) for the iOS ≥ 26 case.
- Add a one-line link in `docs/IOS_STATUS.md` (or equivalent) noting the
  artifact and the version gating, so future contributors do not re-open
  the candidate-bar branch of this investigation.

### Hard rule

No more candidate-layout edits, no more cover strips, no more root-view
backdrops, and no more `UIVisualEffectView` experiments for this bug
unless a future log shows the candidate bar or composing label moving
inside LimeIME's own bounds (the first row of the decision table). Every
prior visual workaround has failed for the same structural reason: the
artifact lives in a window above LimeIME's input view that the extension
does not own.
```

---

## Verification

End-to-end checks once the doc rewrite is applied:

1. Open `docs/SAFARI_EXTRA_SPACE.md` and scan top-to-bottom. Confirm:
   - "Bug Statement", "Geometry Proof", "What We Tried And Why It Failed",
     "How iOS Measures Our Keyboard Input View Height", and "Is The Extra
     Padding Caused By Background Color?" sections are unchanged.
   - "Root Cause Analysis" now lists four concrete actors with citations
     and four explicit non-causes.
   - "Proposed Next Solution" includes the new decision table with rows
     for iOS ≤ 25 WKWebView, iOS ≥ 26 Liquid Glass, and iPad assistant
     bar; each row maps to a host-owned classification.
2. Cross-reference links resolve:
   - `docs/IPAD_ASSIST_BAR.md` exists (`grep` confirmed) and §8.1 is the
     "silently ignored" anchor.
   - `docs/IOS_POPUP_COMPOSING.md` exists.
   - `docs/CANDI_LAYOUT.md` exists.
3. No source files change in this plan. The DEBUG probe in §Step 1 is a
   separate, opt-in implementation step to be planned and landed after
   this rewrite is approved.

---

## Out of scope

- Implementing the DEBUG probe in `KeyboardViewController.swift`. That is
  a follow-up plan once the doc rewrite lands.
- Editing any other doc (`CANDI_LAYOUT.md`, `IPAD_ASSIST_BAR.md`,
  `IOS_STATUS.md`).
- Editing any source files.
- Filing the actual Feedback Assistant tickets.
