# SciVerse Summit — Desktop shells (Electron)

Separate from the web app. **Nothing in `summit-v3.0.3-alpha/` is touched by this folder** —
no shared code, no build coupling. The web jar is only *copied in* at Chair-build time.

```
summit-desktop/
  assets/            shared artwork (logo source)
  chair/             Chair build: launcher + bundled Spring Boot server
    electron/        main.js (spawn/stop server, LAN address, updater) + preload.js
    src/             launcher UI (static, renders with no server running)
    build/           icon.ico for electron-builder (copied from old Tauri icons)
    server/          STAGING (git-ignored): summit.jar + jlink mini-runtime
  delegate/          Delegate build: connect UI + custom login (no server inside)
    electron/        main.js (login-override injection + updater) + preload.js
    src/             connect UI + login-override.js (injected only on /login)
    build/           icon.ico for electron-builder
```

## Prerequisites (one time)

1. Node 18+ (`node --version` → v24 used here).
2. A JDK for `jlink` (any 17+; Temurin is the sanctioned source, Oracle JDK 26 used here).
3. In each app: `npm install` (pulls `electron`, `electron-builder`, `electron-updater`).
4. Icons: `build/icon.ico` in each app (reused from the former Tauri scaffold,
   generated from `assets/logo.png`).

## Chair build

1. Copy the current jar (rebuild the web app first if it changed):
   `chair/server/summit.jar` ← `summit-v3.0.3-alpha/summit-0.0.1-SNAPSHOT.jar`
2. Build the mini-runtime into `chair/server/jre/` (`bin\java.exe` must exist):
   ```
   jlink --add-modules java.se,jdk.zipfs,jdk.unsupported,jdk.crypto.cryptoki,jdk.management,jdk.net,jdk.localedata --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output chair\server\jre
   ```
   (Validated: boots the real jar, serves :8080. `jdk.zipfs` is required for the
   Spring Boot nested-jar loader.)
3. Smoke test (optional but recommended): run
   `chair\server\jre\bin\java.exe -jar chair\server\summit.jar` with
   `SPRING_DATASOURCE_URL=jdbc:h2:file:<tmp>/data/presentationdb;AUTO_SERVER=TRUE`
   and check port 8080 answers.
4. `cd chair && npm run dist` → NSIS installer in `chair/dist/`
   (`SciVerse Summit Chair Setup 0.1.0.exe`, ~207MB: Electron + jar + runtime).

At runtime the H2 database is relocated out of the install dir into per-user
app data via the `SPRING_DATASOURCE_URL` env override
(`<userData>/data/presentationdb`, same `;AUTO_SERVER=TRUE` suffix as
`application.properties`). Server stdout/stderr streams into the launcher log.

## Delegate build

1. `cd delegate && npm run dist` → lightweight installer in `delegate/dist/`
   (~91MB, Electron only, no Java inside).

The launcher collects the chair's address, polls until the server answers, then
navigates to it. After navigation, `electron/main.js` injects
`src/login-override.js` on exactly the server's `/login` page
(`did-finish-load` hook) — same design as before.

## Auto-updates

- Both `package.json` files have `build.publish` (GitHub provider) with
  placeholder owner/repo **`REPLACE_USER`/`REPLACE_REPO`** — replace with your
  GitHub repo in `repository.url` + `build.publish` (both apps) before release.
- `electron-updater` checks on launch (packaged builds only) and installs
  silently on next start. No code changes needed.
- Release: bump `version` in each `package.json`, build both apps, upload each
  `dist/*.exe` **plus its `latest.yml` + `.blockmap`** to the same GitHub Release.
- Chairs and delegates update silently on next launch.

## Design notes

- **Loading UI is local**: both launchers are static files — they render before
  (chair) or without ever (delegate) running `java -jar`.
- **IPC is minimal**: `electron/preload.js` exposes only `window.summitAPI`
  (`startServer`/`stopServer`/`lanAddress`/`onLog` for chair, `getVersion` for
  delegate). Context isolation stays on; no raw `ipcRenderer` in renderers.
- **Delegate login override** (`delegate/src/login-override.js`) replaces only the
  `/login` page body with a same-origin `<form method="post" action="/login">`
  carrying the exact `username`/`password` contract from `SecurityConfig`
  (CSRF disabled). Sessions/cookies/redirects behave natively. Chair build has
  no override — original Thymeleaf login stays.
- **Future hardening** (needs a web-app change, deliberately NOT done here):
  add `GET /api/version` to Spring Boot so the delegate shell can warn on
  shell/server version skew.
