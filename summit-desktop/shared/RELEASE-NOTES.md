# Release notes template

The **release body is the changelog**. The apps render it when an update is
offered, so this is player-facing text, not internal notes.

Modelled on the Fisch update logs: a feature title, an icon per section, `###`
sub-headings for individual items, terse scannable bullets, and the
`old → **new**` pattern for anything that changed value.

Validate a draft before publishing:

```
node summit-desktop/shared/check-release-notes.js notes.md
```

Add `--render` to print the exact HTML a reader will see.

---

## The template

Copy this. Delete the sections and comments you have nothing for — every `>`
line is a comment **only if you delete it**, because otherwise it renders as
visible text.

```
type: update

# v3.1.0 — Interface & Alerts

## 🌟 New
- **<feature>** — what the user can now do
- <feature> — what the user can now do

## 🎨 Redesign
- **<what changed>** — what it looks or feels like now

## ⚖️ Changes
### <Item or Area>
- <Setting>: <old> → **<new>**
- <Setting>: <old> → **<new>**
  - <sub-detail, 2-space indent>
### <Item or Area>
- <Setting>: <old> → **<new>**

## 🐛 Bugfixes & Quality of Life
- Fixed <the symptom the user actually saw>
- Added <new small thing>
- Reduced <a problem rather than fixing it outright>

> <one-line reminder, if there is something they must do>
```

Keep only the sections that apply. A fixes-only release needs one heading and
a list.

---

## The `old → **new**` pattern

Their signature move, and worth copying for anything numeric. Bold the new
value so the eye lands on what changed:

```
- Lure Speed: 95% → **100%**
- Duration: 30s → **60s**
- Infernal: 7× → **10×**
- Luck: +10% → **+35%**
```

This is just bold plus a literal `→`, so it renders reliably. Use it for
versions, timeouts, limits, counts and rates. For prose changes, a plain
`Fixed …` / `Now …` line reads better.

---

## Nesting

Two spaces per level, up to four deep. This is for *detail belonging to the
line above it*:

```
- Infernal Melody 1 Adjustments:
  - Duration: 30s → **60s**
  - Scorched: 20% → **60%**
- Masterline
  - **Cinder Block Rod** is now blacklisted
  - **Remembrance** is no longer blacklisted
    - Copied Remembrance will not have the Dark Fish passive
```

Use `###` rather than nesting when a group has a *name* — it reads better and
survives a long list. Reserve nesting for two or three lines of detail.

---

## Writing rules

- **One line per bullet.** These get scanned, not read.
- **Bold the subject**, then `—` and the consequence:
  `- **Session Deleted** alerts now say the chair deleted it`
- **Write the symptom, not the internals.** "Fixed notifications showing the
  wrong delegate's name" beats "fixed a bug in LiveEventService". Your users
  have never heard of `LiveEventService`.
- **Mixed tense, like Fisch.** Present for what exists now, past for fixes:
  - `Now has **+25% Luck**`
  - `Fixed the badge count growing forever`
- **No marketing adjectives.** "Massive", "revolutionary", "game-changing" tell
  the reader nothing. Say what changed.
- **A closing line is fine.** Fisch ends with a promo code; the equivalent here
  is a callout, e.g. `> Restart the app after installing to finish the update.`

---

## Supported syntax

Verified against the real renderer in `update-ui.js`.

| Works | Example |
| --- | --- |
| Section headings `##`, sub-headings `###` | `## ⚖️ Changes` / `### Cinderstring` |
| Nested bullets, 2 spaces per level | see above |
| Bold, italic | `- **Stop Server** button` |
| Inline code | `- Port is fixed at `8080`` |
| Links | `- See the [guide](https://…)` |
| Blockquote, for a callout | `> Restart the app after installing` |
| The arrow pattern | `95% → **100%**` |
| Emoji in headings | `## 🐛 Bugfixes` |

### Do not use

These do not error — they render as broken literal text, which looks worse
than not using them at all.

| Avoid | Because |
| --- | --- |
| Tables | Collapse into one mangled paragraph |
| `---` horizontal rules | Render as literal dashes |
| Images `![]()` | Render as a stray `!` plus a link |
| Code fences ` ``` ` | Render as stray backticks |
| Raw HTML | Shown escaped, as source text |
| Nesting past 4 levels | Flattened at the cap |
| Tab indentation | Use 2 spaces per level |

Ordered lists (`1.`, `2.`) work but render as bullets with the numbers
dropped. Prefer a flat bullet list.

---

## Worked example — v3.1.0

```
# v3.1.0 — Interface & Alerts

## 🌟 New
- **Desktop notifications** — the apps alert you natively, only when unfocused
- **Taskbar badge and window flash** on an incoming alert
- **Stop Server button** in the chair console hero panel

## 🎨 Redesign
- **Chair console rebuilt** — dark liquid-glass theme, clearer server controls
- **Delegate launcher rebuilt** — new connect flow that remembers recent chairs
- **Sidebar session card** — frosted card, click to expand for details
- **Modals are properly frosted** — the blur no longer flattens in dark mode

## ⚖️ Changes
### Notifications
- Recipients: everyone → **only the delegate concerned**
- Marked as read: on click → **as soon as it is shown**
### Speakers
- Picker: type the name → **search and filter**
- Empty search results: silent → **"No delegates match your search."**

## 🐛 Bugfixes & Quality of Life
- Fixed notifications showing one delegate's name to every other delegate
- Fixed the notification history deleting your newest alerts instead of your oldest
- Fixed the badge count growing forever as you used the app
- Fixed speaker alerts that could never be marked as read
- Fixed the app icon failing to load, and the name shown on notification headers
- Fixed a window that could restore at 160x28 pixels on launch
- Fixed the session log freezing while the server was running

> The upgrade screen now shows a changelog. If a session server is running,
> stop it first — updating restarts the app and disconnects every delegate.
```

---

## Two deliberate deviations from Fisch

1. **The version stays in the title.** Fisch can call theirs "Boat Racing
   Update" because players already have the game. SciVerse users download a
   specific installer, so `# v3.1.0 — Interface & Alerts` tells them which one
   to grab.
2. **`type: update` / `type: upgrade` on the very first line.** That is the
   marker this project uses to tell a routine update from a major one. It is
   consumed by the app and never shown to readers, so it costs nothing.
