# Safari / Gemini Extra Space Above Keyboard

This document tracks the intermittent extra top space / rounded host rectangle
seen above LimeIME's candidate bar in Safari, Google search, and Gemini input
fields.

The candidate-bar geometry itself is documented in
[CANDI_LAYOUT.md](CANDI_LAYOUT.md). This document is only about the iOS / host
keyboard-container area outside LimeIME's candidate-bar layout.

---

## Bug Statement

Some host input fields show an extra rounded top area above LimeIME's candidate
bar. It looks like a gap or a rounded rectangle sitting between the app content
and the keyboard.

Observed behavior:

- Safari URL bar can show no extra top space.
- Google search and Gemini input can show the extra top space again.
- Apple's built-in iOS phonetic keyboard does not show the same extra top space
  in the observed case.
- Closing and reopening the keyboard can change the visual result.
- Painting opaque LimeIME pixels can hide the symptom in one host path, then the
  same host/app or another input field can show it again.

Desired behavior:

- The top edge above LimeIME's candidate bar should remain consistently tight.
- The keyboard background should keep the iOS keyboard's blended bottom/action
  row appearance.

Current conclusion:

The visible extra top space is system / host padding outside LimeIME's keyboard
input view area. The DEBUG geometry log proves LimeIME's root input view,
candidate bar, and composing label are already flush at `y = 0` inside the
extension window. The built-in Apple phonetic keyboard not showing the same gap
suggests the behavior is specific to the third-party keyboard hosting path, not
to the text field alone.

---

## Geometry Proof

A bad case was measured with temporary DEBUG logging:

```text
view.frame=(x:0.0,y:0.0,w:440.0,h:313.3)
view.bounds=(x:0.0,y:0.0,w:440.0,h:313.3)
viewInWindow=(x:0.0,y:0.0,w:440.0,h:313.3)
window.bounds=(x:0.0,y:0.0,w:440.0,h:313.3)
candidateBar.frame=(x:0.0,y:0.0,w:440.0,h:59.3)
candidateBarInWindow=(x:0.0,y:0.0,w:440.0,h:59.3)
keyboardView.frame=(x:0.0,y:59.3,w:440.0,h:254.0)
composingLabel=(x:28.0,y:0.0,w:367.0,h:27.0)
heightConstraint=313.4
barHeightConstraint=59.4
```

Interpretation:

- `viewInWindow.y == 0`: LimeIME's root input view starts at the top of its
  keyboard extension window.
- `candidateBar.frame.y == 0`: the candidate bar starts at the top of LimeIME's
  root view.
- `candidateBarInWindow.y == 0`: the candidate bar is flush with the extension
  window.
- `composingLabel.frame.y == 0`: the composing keyname / reverse lookup label is
  not shifted downward inside the candidate bar.
- `keyboardView.frame.y == candidateBar.height`: the key grid starts immediately
  below the candidate bar.

Therefore the extra space is not caused by LimeIME Auto Layout placing the
candidate bar too low. There is no internal LimeIME top offset to remove.

---

## What We Tried And Why It Failed

### 1. Candidate layout tuning

An early attempt treated the issue as candidate-bar internal layout and adjusted
candidate button / pill geometry.

Result: failed and reverted. It affected candidate spacing and expanded-panel
parity, but the measured top coordinates showed the candidate bar was already
flush.

### 2. DEBUG geometry logging

Temporary logging compared the root view, window, candidate bar, key grid, and
composing label frames.

Result: useful, then removed. It proved the extra space is not inside the
candidate bar or inside the extension window's measured layout.

### 3. Solid candidate bar background

Changing `CandidateBarView.backgroundColor` from `.clear` to a solid palette
color appeared to cover the rounded host area in some cases.

Result: failed. It made the candidate area look like a flat strip and did not
solve all host paths. It was only masking pixels inside our bounds, not changing
the host's decision to reserve or draw the top area.

### 4. Root view solid background

Setting the root keyboard `view.backgroundColor` to an opaque keyboard-like
color appeared to fix the Safari URL bar path.

Result: failed. Google search and Gemini input still showed the extra top space.
This is the key evidence that background color is not the root cause. If iOS
were measuring or adding the padding because of our background color, a root
opaque background would consistently change the result. It did not.

### 5. `UIVisualEffectView` blur/material backdrop

A full-size material blur view was added behind the candidate bar and key grid
to preserve the iOS keyboard material look.

Result: failed. A blur is translucent and can still reveal host-rendered shapes.
It also introduced unsafe behavior in the hosted keyboard path.

### 6. Opaque custom backdrop subview

A plain opaque `UIView` backdrop was added behind the whole keyboard, with a
color chosen to resemble the bottom globe/mic action-row background.

Result: unsafe. On device, the keyboard extension hit an uncaught UIKit / host
exception involving `UIPeripheralHost` and `_fallbackTraitCollection`. Adding
extra root-level backdrop views inside the private keyboard host is risky.

### 7. Phone-only top cover strip / increased keyboard height

A small top cover strip was added above the candidate bar, and the root height
constraint was increased so LimeIME would theoretically own more pixels above
the previous top edge.

Result: failed. Gemini still showed the extra space. This strongly suggests the
problem area is not the top of the old LimeIME view. It is a host-owned area
above or around the input view, or the host inserts its own accessory/suggestion
container above the third-party keyboard for that focused field.

---

## Root Cause Analysis

The root cause is not candidate-bar layout, not the height constraint, and
not the keyboard background color. The geometry log proves the candidate
bar and composing label are at `y = 0` inside the extension window, and
the failed root-background experiment proves background color does not
change iOS's allocation decision.

The extra rounded top space is system / host UI drawn ABOVE LimeIME's
keyboard extension window, in a layer the extension fundamentally cannot
reach. There are several actors, and which one appears depends on the
host context and iOS version.

External research grounding this analysis is preserved in
[SAFARI_EXTRA_SPACE_PLAN.md](SAFARI_EXTRA_SPACE_PLAN.md) under
"External research synthesis (raw)".

### Actors that draw above the third-party keyboard window

1. **WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done bar)**
   — owned by `WKContentView`, the private first responder inside any
   `WKWebView` (Safari page content, Gemini web app, Google search box).
   Rendered by the host app process above the keyboard. Appears for any
   focused HTML `<input>` / `<textarea>`. Has rounded top corners on
   modern iOS, which matches the visible artifact. A keyboard extension
   has zero API to suppress or restyle it. Removing it is only possible
   from the host app side, by swizzling `WKContentView.inputAccessoryView`
   to `nil`, which Safari / Gemini / Google do not do. The Safari URL bar
   is a native `UITextField`, not a `WKWebView` input — it does not show
   this bar, which explains why the URL-bar path can look flush while
   Gemini / Google `<input>` paths do not.
   - Source: rdar://27763084; Apple Developer Forums thread 81650;
     `https://www.technetexperts.com/hide-ios-input-bar/`.

2. **iOS 26 Liquid Glass keyboard host insets** — starting in iOS 26,
   host apps that adopt the Liquid Glass design (Messages, Notes, Safari,
   etc.) embed third-party keyboards inside a rounded container with
   grey margins on the left, right, and top. The margin region is owned
   by the `UIInputSetHostView` window stack and is not drawable by the
   extension. This affects both web and native hosts on iOS ≥ 26.
   - Source: Apple Developer Forums thread 800838.

3. **iOS 26 Quick Actions / App Shortcuts bar** — a new system rounded-
   rectangle bar above the keyboard in iOS 26 web form contexts, showing
   passwords / Apple Pay / location / checklist icons. System-owned; not
   developer-disable-able from the extension or the host.
   - Source: Apple Community threads 256177528, 256173518.

4. **iPad `UITextInputAssistantItem` shortcut bar** — the host text
   view's shortcut bar above the keyboard on iPad. Not relevant on
   iPhone. [IPAD_ASSIST_BAR.md](IPAD_ASSIST_BAR.md) §8.1 already
   documents that setting
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
- **Password AutoFill strip on credential fields.** iOS blocks
  third-party keyboards from being selected for fields recognized for
  password AutoFill (Apple Developer Forums thread 94889), so LimeIME
  would not be active there at all — this case cannot produce the
  observed artifact.
- **Background color of the extension.** Already proven irrelevant.

### Why Apple's phonetic keyboard does not show the same gap

Apple's built-in keyboards are not hosted as third-party extensions.
They run on a private hosting path that can integrate predictive UI,
accessory chrome, and the Quick Actions strip directly with the keyboard
surface. Third-party extensions render in a separate keyboard window
that the host chrome cannot merge with, so the same accessory chrome
that visually blends above Apple's keyboard appears as a distinct
rounded rectangle above LimeIME.

### Conclusion

The artifact is host- and OS-owned, not LimeIME-owned. There is no
keyboard-extension API that removes WKWebView's form-assistant
accessory, the iOS 26 host inset, or the iOS 26 Quick Actions bar.
Candidate-bar geometry, background fills, cover strips, and
`UIVisualEffectView` backdrops are the wrong tool for this category of
bug. They were tried and failed for exactly that reason.

---

## How iOS Measures Our Keyboard Input View Height

Short answer: iOS measures LimeIME's keyboard input view height from Auto Layout
constraints / fitting size, not from background color.

In LimeIME, the relevant height request is in `applyHeight()`:

```swift
let totalHeight = candidateBarHeight + keysHeight
let c = view.heightAnchor.constraint(equalToConstant: totalHeight)
c.priority = UILayoutPriority(rawValue: 999)
c.isActive = true
keyboardHeightConstraint = c
```

When size-related settings change, LimeIME updates this constraint's constant.
iOS then lays out the extension root view inside the system keyboard host.

What can affect the measured input view height:

- LimeIME's root `view.heightAnchor` constraint.
- Constraint priorities and satisfiability inside the extension view.
- System maximum/minimum rules for third-party keyboards.
- Trait and size-class changes that cause LimeIME to compute a different
  `candidateBarHeight` or `keysHeight`.

What does not affect the measured input view height:

- `backgroundColor`.
- Clear vs opaque pixels.
- `UIVisualEffectView` vs plain color.
- Candidate text, button highlight, or composing-label drawing.

Background color changes only what LimeIME paints inside the height iOS already
allocated. It is not an input to iOS height measurement.

---

## Is The Extra Padding Caused By Background Color?

No.

The strongest evidence is the failed root-background experiment:

- With an opaque root background, Safari URL bar could show no extra space.
- With the same general approach, Google/Gemini still showed the extra space.

If background color were the reason iOS decided to add the padding, the result
would be deterministic for the same keyboard build. Instead, the result follows
the focused host input context.

Background color is only a reveal/mask factor:

- Clear or translucent areas can reveal host-owned rounded shapes.
- Opaque areas can cover host pixels inside LimeIME's own bounds.
- Opaque areas cannot cover padding outside LimeIME's input view.

So background color can make the symptom easier or harder to see, but it does
not cause iOS to allocate the extra top padding.

---

## What We Still Need To Learn

The unanswered question is not "where is the candidate bar?" We know it is at
`y = 0` inside the extension.

The unanswered question is:

Why does iOS / the host app choose a keyboard-host stack with extra top padding
for Gemini or Google search, but not always for Safari URL bar?

Related question:

Why does the built-in Apple phonetic keyboard avoid this padding in the same
kind of input context, while LimeIME does not?

Possible iOS decision inputs:

- `UITextInputTraits`, such as keyboard type, return key type, autocorrection,
  spell checking, smart insert/delete, secure text entry, and text content type.
- Host-owned input accessory views or suggestion containers above the keyboard.
- Browser/search UI overlays that are visually grouped with the keyboard but are
  not part of the third-party keyboard extension view.
- Differences between URL/search fields and normal multiline/editable web fields.
- Private `UIPeripheralHost` / keyboard-host heuristics for third-party keyboards.
- The private system-keyboard path versus the public third-party keyboard
  extension path.

We probably cannot fully know the private heuristic from public API alone, but
we can collect enough evidence to classify each host path as either:

- inside LimeIME's input view, fixable by LimeIME layout; or
- outside LimeIME's input view, not directly drawable or removable by LimeIME.

The existing geometry log already puts the observed bad case in the second
category unless a future log shows different bounds.

---

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
| --- | --- | --- |
| Candidate bar `y > 0` or composing label `y > 0` in any future log | LimeIME internal layout regressed | Fix candidate / composing constraints. Bug is in LimeIME. |
| Candidate bar `y == 0`, composing label `y == 0`, AND `keyboardType` is `.default` / `.emailAddress` etc. on iOS ≤ 25 in Safari / Gemini / Google web form | WKWebView form-assistant `inputAccessoryView` (Prev / Next / Done) | Host-owned. Not fixable from the extension. Document and stop. |
| Same geometry on iOS ≤ 25 in Safari URL bar | Native `UITextField` — different accessory or none | If URL bar still shows extra space, capture and re-classify; otherwise expected behavior. |
| iOS ≥ 26 in any host (web or native) | iOS 26 Liquid Glass host insets and / or Quick Actions bar | Host-owned. Not fixable from the extension. File Feedback Assistant referencing forum thread 800838. |
| iPad-only artifact above keyboard | `UITextInputAssistantItem` shortcut bar | See [IPAD_ASSIST_BAR.md](IPAD_ASSIST_BAR.md) §8.1 — extension-side suppression is silently ignored. Not fixable from the extension. |

### Step 3 — Close-out

For every row except the first, the action is the same: stop iterating
on LimeIME layout for this case, mark the bug as host / OS-owned, and
either file Feedback Assistant or accept the behavior. Specifically:

- File or reference an existing Feedback Assistant ticket referencing
  rdar://27763084 (allow disabling default `inputAccessoryView` on
  WKWebView) for the iOS ≤ 25 case.
- File Feedback Assistant referencing Apple Developer Forums thread
  800838 (iOS 26 Liquid Glass extra grey margin around third-party
  keyboard extensions) for the iOS ≥ 26 case.
- Add a one-line link in `IOS_STATUS.md` (or equivalent) noting the
  artifact and the version gating, so future contributors do not re-open
  the candidate-bar branch of this investigation.

### Hard rule

No more candidate-layout edits, no more cover strips, no more root-view
backdrops, and no more `UIVisualEffectView` experiments for this bug
unless a future log shows the candidate bar or composing label moving
inside LimeIME's own bounds (the first row of the decision table).
Every prior visual workaround has failed for the same structural
reason: the artifact lives in a window above LimeIME's input view that
the extension does not own.
