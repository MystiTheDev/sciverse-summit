# Shared shell theme

`theme.css` is the single source of truth for the Chair and Delegate launcher
UI. Both apps look and behave the same way, so the design system lives here
once.

## How it ships

Each app has a generated copy at `summit-desktop/<app>/src/theme.css`, produced
by `scripts/sync-theme.js`:

```
npm run theme    # copy shared/theme.css -> src/theme.css
npm start        # syncs, then runs the app
npm run dist     # syncs, then builds the installer
```

The CI release workflow calls `npm run dist`, so installers always ship the
current theme.

The generated copy is committed as well, on purpose: it means an app folder
still builds if it is ever copied out on its own, and the diff shows reviewers
what actually changed.

**Edit `shared/theme.css`, never the copies.** If you hand-edit a copy, the
next sync will silently overwrite it.

If `shared/theme.css` is missing, the script logs a warning and leaves the
existing copy alone rather than failing the build.

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
