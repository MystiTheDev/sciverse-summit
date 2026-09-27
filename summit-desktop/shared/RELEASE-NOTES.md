# Release notes template

The **release body is the changelog**. The apps render it when an update is
offered, so this is player-facing text, not internal notes.

Style borrowed from the Fisch update logs: an icon per section, short scannable
bullets, no prose. The renderer is deliberately limited, so stick to the
[supported syntax](#supported-syntax) below.

---

## The template

Copy this, delete the sections you have nothing for, and delete every comment
line (they start with `>` and will render as visible text if you leave them in).

```
type: update

# v3.1.0 — <optional codename>
<27 September 2026>

## 🌟 NEW
- **<feature>** — <one line, what the user can now do>
- <feature> — <one line>

## 🎨 REDESIGN
- **<what changed>** — <one line>
- <what changed> — <one line>

## 🔔 NOTIFICATIONS
- <fix or addition> — <one line>

## ⚡ IMPROVEMENTS
- <change to existing behaviour> — <one line>

## 🐛 FIXES
- Fixed <the actual symptom, not the cause>
- Fixed <symptom>

## 🗑️ REMOVED
- <dead code or feature that went away>
```

Keep only the sections that apply. A release that is purely fixes needs one
heading and a list.

---

## Writing rules

- **One line per bullet.** These get scanned, not read. If a bullet needs two
  lines, it is really two bullets.
- **Lead with `**bold**` for the subject**, then `—` and the consequence:
  `- **Session Deleted** alerts now say the chair deleted it`
- **Write the symptom, not the internals.** "Fixed notifications showing the
  wrong delegate's name" beats "fixed a bug in LiveEventService". The user has
  never heard of `LiveEventService`.
- **Past tense for fixes, present tense for capabilities.**
  - Fixes: `Fixed the badge count growing forever`
  - New: `A Stop button now ends the session server`
- **No marketing adjectives.** "Massive", "revolutionary", "game-changing" tell
  the reader nothing. Say what changed.
- **Date format:** `27 September 2026` or `27 Sep 2026`. Not `09/27/2026`.

---

## Supported syntax

Verified against the actual renderer in `shared/update-ui.js`.

| Works | Example |
| --- | --- |
| Section headings, `##` | `## 🐛 FIXES` |
| Bold, italic | `- **Stop Server** button` |
| Inline code | `- Port is now fixed at `8080`` |
| Links | `- See the [guide](https://…)` |
| Blockquote, for a caveat | `> Requires restarting the app` |
| Flat bullet lists | `- one` / `- two` |

### Do not use

These do not error — they render as broken literal text, which looks worse than
not using them.

| Avoid | Because |
| --- | --- |
| Tables | Collapse into one mangled paragraph |
| `---` horizontal rules | Render as literal dashes |
| Images `![]()` | Render as a stray `!` plus a link |
| Code fences ` ``` ` | Render as stray backticks |
| Nested lists | Flattened — the sub-level is lost |
| Raw HTML | Shown escaped, as source text |

Ordered lists (`1.`, `2.`) work but render as bullets; the numbers are dropped.
Prefer a flat bullet list.

Validate a draft before publishing:

```
node summit-desktop/shared/check-release-notes.js path-to-notes.md
```

---

## Worked example — v3.1.0

```
# v3.1.0 — Interface & Alerts
27 September 2026

## 🎨 REDESIGN
- **Chair console rebuilt** — dark liquid-glass theme, clearer server controls
- **Delegate launcher rebuilt** — new connect flow with recent chairs kept for you
- **Sidebar session card** — frosted card with click-to-expand details
- **Modals are properly frosted** — the blur no longer flattens in dark mode

## 🔔 NOTIFICATIONS
- **Desktop notifications** — the apps now alert you natively, only when unfocused
- **Taskbar badge and window flash** on an incoming alert
- **Session Deleted** alerts now say the chair deleted the session, not that it ended

## ⚡ IMPROVEMENTS
- **Speaker queue search** — type to filter delegates instead of scrolling
- The upgrade screen now shows a changelog for every update

## 🐛 FIXES
- Fixed notifications showing one delegate's name to every other delegate
- Fixed the notification history deleting your newest alerts instead of your oldest
- Fixed the badge count growing forever as you used the app
- Fixed speaker alerts that could never be marked as read
- Fixed the app icon failing to load, and the app name on notification headers
- Fixed a window that could restore at 160x28 pixels on launch
- Fixed the session log not updating while the server was running
```
