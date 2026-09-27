# Shared shell theme and bridge

`theme.css` is the single source of truth for the Chair and Delegate launcher
UI. Both apps look and behave the same way, so the design system lives here
once. `notif-bridge.js` and `notifications.js` do the same job for desktop
notifications.

## How it ships

Each app has generated copies, produced by `scripts/sync-theme.js`:

| Shared file | Copied to | Why there |
|---|---|---|
| `theme.css` | `src/theme.css` | renderer asset |
| `notif-bridge.js` | `src/notif-bridge.js` | injected into server pages |
| `notifications.js` | `electron/notifications.js` | required by the main process |

```
npm run theme    # copy the shared files into the app
npm start        # syncs, then runs the app
npm run dist     # syncs, then builds the installer
```

The CI release workflow calls `npm run dist`, so installers always ship the
current copies.

The generated copies are committed as well, on purpose: it means an app folder
still builds if it is ever copied out on its own, and the diff shows reviewers
what actually changed.

**Edit the files in `shared/`, never the copies.** If you hand-edit a copy, the
next sync will silently overwrite it.

If `shared/` is missing, the script logs a warning and leaves the existing
copies alone rather than failing the build.

## Desktop notifications

The website does fire desktop notifications, through the browser Notification
API — but only when `document.hidden` is true, which is never the case for a
visible app window. So nothing reached the desktop. The app replaces that path:

1. `notif-bridge.js` is injected into every page served by the session server
   in both apps, via the `did-finish-load` + `executeJavaScript` hook each
   already had. It watches the SSE `notif.changed` event *and* polls
   `/api/notifications` on the same 5-second cadence the website uses,
   deduplicates by id, and forwards each new notification over IPC.
2. `notifications.js` raises the actual OS notification in the main process.

Policy, in `notifications.js`:

- **Only when the window is not focused.** The in-app toast already covers the
  focused case, so a desktop popup on top of it would just be noise.
- An unread count on the taskbar (`app.setBadgeCount`) plus `flashFrame`, both
  cleared when the window gains focus — the in-app bell carries the list.
- `VOTING` and `SPEAKER` are marked `critical` and never auto-dismiss, since
  those are the ones you must not miss. Everything else uses the OS default.
- Clicking navigates to the notification's link when it has one. Speaker alerts
  are deliberately link-free on the server, so clicking only focuses the
  window; that is the intended design, not a gap.
- The user's toggle lives in each app's Settings modal
  (`desktopNotifications`, default on) and is persisted in the existing
  `settings.json` / `delegate-store.json`.

Both sources are needed. Batch sends used to publish only the first delegate's
payload, so most delegate notifications only ever arrived via the poll.

This is poll-based, not push: with the app closed nothing is listening, so no
notification is delivered.

## Editing the UI

- `shared/theme.css` — tokens, glass panels, buttons, inputs, toggles, modals,
  settings rows, scrollbars. Anything both apps use.
- `chair/src/console.css` — Chair-only: status hero, join address, log, system
  info.
- `delegate/src/app.css` — Delegate-only: connect form, waiting/error states,
  recent chairs.

Load order matters. `index.html` links the legacy `styles.css` (splash, update
overlay, loader) **before** the theme, so the theme wins where they overlap.

## Icons

`src/bootstrap-icons.css` and `src/fonts/` are vendored copies of the website's
icon font (`src/main/resources/static/` in the Spring Boot app) with the font
URLs rewritten from `/fonts/...` to `fonts/...` so they resolve over `file://`.
Keep them in step with the website's copy if the icon set changes there.
