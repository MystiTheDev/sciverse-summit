'use strict';

/* SciVerse Summit — Delegate shell (Electron).
 *
 * No server here: the launcher collects the chair's address, waits until the
 * chair's server answers, then navigates to it. The custom login page is
 * injected after navigation (see ../src/login-override.js) so the Thymeleaf
 * templates are never touched — same design as the former Tauri scaffold.
 */

const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { autoUpdater } = require('electron-updater');
const { probeServer } = require('./probe');
const { createNotificationService } = require('./notifications');

// Windows taskbar groups icons by AppUserModelId — must match build.appId,
// or the taskbar (and notifications) fall back to the stock Electron icon.
app.setAppUserModelId('SciVerse Summit');

let mainWindow = null;
let splashWindow = null;
let splashTimer = null;
let loginOverrideJs = '';

/* ── Settings + recents (JSON in userData) ──────────────── */
const SETTINGS_DEFAULTS = {
  remember: true,
  autoConnect: false,
  launchAtLogin: false,
  glint: true,
  desktopNotifications: true,
};
let settings = Object.assign({}, SETTINGS_DEFAULTS);
let storeCache = null;

function storeFile() {
  return path.join(app.getPath('userData'), 'delegate-store.json');
}

function readStore() {
  if (storeCache) return storeCache;
  try { storeCache = JSON.parse(fs.readFileSync(storeFile(), 'utf8')) || {}; } catch (e) { storeCache = {}; }
  return storeCache;
}

function saveStore() {
  try {
    fs.writeFileSync(storeFile(), JSON.stringify({
      settings: settings,
      recents: readStore().recents || [],
      lastHost: readStore().lastHost || '',
    }, null, 2));
  } catch (e) { /* non-fatal */ }
}

function applyLaunchAtLogin() {
  try { app.setLoginItemSettings({ openAtLogin: !!settings.launchAtLogin }); } catch (e) { /* unsupported */ }
}

function loadStore() {
  settings = Object.assign({}, SETTINGS_DEFAULTS, readStore().settings);
  applyLaunchAtLogin();
}

// Native splash: a wide panel that hands over to the connect screen. The page
// owns its own minimum hold and exit; this is only a failsafe in case it never
// reports back, so it is generous and never fires in normal use.
const SPLASH_FALLBACK_MS = 15000;

function createSplash() {
  splashWindow = new BrowserWindow({
    // Sized for the shared two-column splash panel (1.85:1, max 1040 wide),
    // plus room for the darker surround so the panel reads as a card.
    width: 1120,
    height: 660,
    title: 'SciVerse Summit Delegate',
    icon: path.join(__dirname, '..', 'src', 'logo.png'),
    frame: false,
    transparent: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    center: true,
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  splashWindow.loadFile(path.join(__dirname, '..', 'src', 'splash.html'), {
    query: { v: app.getVersion() },
  });
  // The handle is captured locally: the splash can close itself first, and
  // only the window that actually closed may clear the module-level global.
  const win = splashWindow;
  win.on('closed', () => {
    if (splashWindow === win) splashWindow = null;
    revealMainWindow();
  });
  splashTimer = setTimeout(() => {
    splashTimer = null;
    try {
      if (win && !win.isDestroyed()) win.close();
    } catch { /* already gone */ }
    if (splashWindow === win) splashWindow = null;
    revealMainWindow();
  }, SPLASH_FALLBACK_MS);
}

/** Shows the connect screen once, after the splash has handed over. */
function revealMainWindow() {
  if (splashTimer) {
    clearTimeout(splashTimer);
    splashTimer = null;
  }
  if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) {
    mainWindow.show();
  }
}

/* ── Recents ────────────────────────────────────────────── */
function addRecent(host) {
  const list = (readStore().recents || [])
    .filter((r) => (r.host || r) !== host);
  list.unshift({ host: host, at: Date.now() });
  storeCache = Object.assign({}, readStore(), { recents: list.slice(0, 6), lastHost: host });
  saveStore();
  return storeCache.recents;
}

function clearRecents() {
  storeCache = Object.assign({}, readStore(), { recents: [] });
  saveStore();
  return [];
}

// One instance only: connecting from a second copy would be confusing.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

/* ── Native notifications ───────────────────────────────── */
const notifier = createNotificationService({
  isEnabled: () => !!settings.desktopNotifications,
  getWindow: () => mainWindow,
});

/**
 * True for pages served over http(s), i.e. anything belonging to the chair's
 * session server: the login screen, the delegate portal, and the chair's own
 * pages. The delegate's connect screen and settings are loaded from file://,
 * so they never match and the bridge stays off our own UI. New windows are
 * handed to the system browser by setWindowOpenHandler, so in-window
 * navigation stays on the session server.
 */
function isSessionServerPage(urlString) {
  try {
    const u = new URL(urlString);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

// Shared notification bridge, injected into every session-server page.
let notifBridgeJs = '';
try {
  notifBridgeJs = fs.readFileSync(path.join(__dirname, '..', 'src', 'notif-bridge.js'), 'utf8');
} catch (err) {
  console.error('Could not load notif-bridge.js:', err.message);
}

function isServerLogin(urlString) {
  try {
    const u = new URL(urlString);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
    return u.pathname.replace(/\/+$/, '') === '/login';
  } catch {
    return false;
  }
}

function createWindow() {
  createSplash();

  mainWindow = new BrowserWindow({
    width: 1000,
    height: 780,
    minWidth: 720,
    minHeight: 620,
    title: 'SciVerse Summit Delegate',
    icon: path.join(__dirname, '..', 'src', 'logo.png'),
    autoHideMenuBar: true,
    show: false,
    backgroundColor: '#0b1120',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(path.join(__dirname, '..', 'src', 'index.html'));

  // The loading screen (shared/loading.js) holds its minimum-visible
  // time from this moment. Timed from script parse it finished and was
  // removed while this window was still hidden, so it was never seen.
  mainWindow.on('show', () => {
    try {
      if (!mainWindow.isDestroyed()) mainWindow.webContents.send('window:shown');
    } catch { /* window closed before the renderer could be told */ }
  });

  // The splash page closes its own window once it has held for its minimum
  // and finished its exit, so the connect screen is revealed by the splash's
  // own lifecycle rather than by a fixed timer here.
  // Only tell the splash the app is up. It reveals the window itself once it
  // has held for its minimum and finished its exit. The handle is captured
  // locally because the splash can close itself first, nulling the global.
  const splashWin = splashWindow;
  mainWindow.webContents.once('did-finish-load', () => {
    try {
      if (splashWin && !splashWin.isDestroyed()) {
        splashWin.webContents.send('splash:done');
      }
    } catch { /* splash already gone */ }
  });

  // Inject the custom /login design only on the server's login page.
  // did-finish-load fires on every full navigation, including the redirect
  // to the chair server and the ?error reload after a failed attempt.
  mainWindow.webContents.on('did-finish-load', () => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    const url = mainWindow.webContents.getURL();
    if (isServerLogin(url) && loginOverrideJs) {
      mainWindow.webContents.executeJavaScript(loginOverrideJs).catch(() => {});
    }
    if (isSessionServerPage(url) && notifBridgeJs) {
      mainWindow.webContents.executeJavaScript(notifBridgeJs).catch(() => {});
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) {
      shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  mainWindow.on('focus', () => {
    notifier.clearBadge();
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

ipcMain.handle('app:version', async () => app.getVersion());

/* ── Readiness probe for the chair's server ────────────── */
ipcMain.handle('server:probe', async (_e, req) => {
  const { host, port, path, timeout } = (req || {});
  return probeServer(host, port, path || '/login', timeout);
});

/* ── Settings + recents ─────────────────────────────────── */
ipcMain.handle('settings:get', async () => settings);

ipcMain.handle('settings:set', async (_e, next) => {
  if (next && typeof next === 'object') {
    settings = Object.assign({}, SETTINGS_DEFAULTS, next);
    saveStore();
  }
  return settings;
});

ipcMain.handle('recent:add', async (_e, host) => {
  if (!host) return [];
  return settings.remember ? addRecent(String(host)) : [];
});

ipcMain.handle('recent:list', async () => (settings.remember ? (readStore().recents || []) : []));
ipcMain.handle('recent:clear', async () => clearRecents());
ipcMain.handle('recent:last', async () => readStore().lastHost || '');

/* ── Native notification bridge ─────────────────────────── */
ipcMain.handle('notif:show', async (_e, payload) => notifier.show(payload || {}));

ipcMain.handle('notif:enabled', async () => !!settings.desktopNotifications);

ipcMain.handle('notif:set-enabled', async (_e, value) => {
  settings.desktopNotifications = !!(value && value.enabled);
  saveStore();
  if (!settings.desktopNotifications) notifier.clearBadge();
  return settings.desktopNotifications;
});

ipcMain.handle('notif:clear-badge', async () => {
  notifier.clearBadge();
  return true;
});

/* ── Manual update check from the settings modal ───────── */
ipcMain.handle('update:check', async () => {
  if (!app.isPackaged) return 'dev-mode';
  try {
    autoUpdater.channel = 'delegate';
    await autoUpdater.checkForUpdates();
    return 'ok';
  } catch (err) {
    throw new Error(String((err && err.message) || err));
  }
});

ipcMain.handle('update:download', async () => {
  autoUpdater.downloadUpdate().catch(() => {});
});
ipcMain.handle('update:restart', async () => {
  autoUpdater.quitAndInstall();
});

function wireUpdater(win) {
  const send = (channel, payload) => {
    if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
  };
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;
  autoUpdater.on('checking-for-update', () => { send('update:checking'); send('update:manual', { state: 'checking' }); });
  autoUpdater.on('update-available', (info) => {
    send('update:available', info);
    send('update:manual', { state: 'available', version: info && info.version });
  });
  autoUpdater.on('update-not-available', (info) => {
    send('update:not-available');
    send('update:manual', { state: 'not-available', current: (info && info.version) || app.getVersion() });
  });
  autoUpdater.on('download-progress', (p) => { send('update:progress', p); });
  autoUpdater.on('update-downloaded', (info) => { send('update:downloaded', info); });
  autoUpdater.on('error', (err) => {
    const msg = String((err && err.message) || err);
    send('update:error', msg);
    send('update:manual', { state: 'error', message: msg });
  });
}

app.whenReady().then(() => {
  loadStore();
  try {
    loginOverrideJs = fs.readFileSync(
      path.join(__dirname, '..', 'src', 'login-override.js'),
      'utf8',
    );
  } catch (err) {
    console.error('Could not load login-override.js:', err.message);
  }

  createWindow();
  wireUpdater(mainWindow);

  // Auto-updates from GitHub Releases (MystiTheDev/sciverse-summit) —
  // ask-first, no OS notification, no silent download. Windows follows the
  // `delegate` channel, Linux follows `delegate-linux` (separate feed files, one repo).
  if (app.isPackaged) {
    autoUpdater.channel = 'delegate'; // same on all platforms: the builder appends -linux itself
    autoUpdater.checkForUpdates().catch(() => {});
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
