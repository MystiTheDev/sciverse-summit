'use strict';

/* SciVerse Summit — Chair shell (Electron).
 *
 * Spawns the bundled Spring Boot server (`jre/bin/java.exe -jar summit.jar`)
 * as a child process, shows a local launcher UI until the server is healthy,
 * then hands off to the real web UI. The Java process is killed when the
 * app quits.
 *
 * H2 data is relocated out of the install dir (which may be read-only) into
 * the per-user app-data dir via the SPRING_DATASOURCE_URL env override, which
 * Spring Boot maps onto spring.datasource.url (default in
 * application.properties: jdbc:h2:file:./data/presentationdb;AUTO_SERVER=TRUE).
 */

const { app, BrowserWindow, ipcMain, shell, Menu, Tray, nativeImage, Notification } = require('electron');
const path = require('path');
const fs = require('fs');
const dgram = require('dgram');
const { spawn } = require('child_process');
const { autoUpdater } = require('electron-updater');
const { probeServer } = require('./probe');
const { createNotificationService } = require('./notifications');

const PORT = 8080;

// One instance only: a second launch must surface the existing (possibly
// tray-hidden) console instead of starting a competing server on :8080.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => showMainWindow());
}

// Windows taskbar groups icons by AppUserModelId — must match build.appId,
// or the taskbar (and notifications) fall back to the stock Electron icon.
app.setAppUserModelId('SciVerse Summit');

let mainWindow = null;
let splashWindow = null;
let splashTimer = null;
let serverProc = null;
let tray = null;
let quitting = false;

/* ── Settings store (JSON in userData) ──────────────────── */
const SETTINGS_DEFAULTS = {
  autoStart: false,
  launchAtLogin: false,
  tray: true,
  rememberBounds: true,
  logSize: 'medium',
  logWrap: true,
  clearOnStart: true,
  desktopNotifications: true,
};
let settings = Object.assign({}, SETTINGS_DEFAULTS);

function settingsFile() {
  return path.join(app.getPath('userData'), 'settings.json');
}

function loadSettings() {
  try {
    settings = Object.assign({}, SETTINGS_DEFAULTS, JSON.parse(fs.readFileSync(settingsFile(), 'utf8')));
  } catch (e) {
    settings = Object.assign({}, SETTINGS_DEFAULTS);
  }
  applyLaunchAtLogin();
}

function applyLaunchAtLogin() {
  try { app.setLoginItemSettings({ openAtLogin: !!settings.launchAtLogin }); } catch (e) { /* unsupported */ }
}

function saveSettings() {
  try { fs.writeFileSync(settingsFile(), JSON.stringify(settings, null, 2)); } catch (e) { /* non-fatal */ }
  applyLaunchAtLogin();
}

function dataDir() {
  return path.join(app.getPath('userData'), 'data');
}

// Native splash: logo + name for a fixed brand moment before the app opens.
const SPLASH_MS = 14000;

function createSplash() {
  splashWindow = new BrowserWindow({
    width: 440,
    height: 600,
    title: 'SciVerse Summit Chair',
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
  splashWindow.on('closed', () => {
    splashWindow = null;
  });
}

function resourceBase() {
  // Packaged: server/ lives under process.resourcesPath via extraResources.
  // Dev: server/ sits next to electron/ and src/.
  return app.isPackaged ? process.resourcesPath : path.join(__dirname, '..');
}

function serverPaths() {
  const base = resourceBase();
  // server/jre/ is staged per-OS at build time (Temurin JRE on every
  // platform); only the binary name differs.
  const javaBin = process.platform === 'win32' ? 'java.exe' : 'java';
  return {
    jar: path.join(base, 'server', 'summit.jar'),
    java: path.join(base, 'server', 'jre', 'bin', javaBin),
  };
}

/** Per-user H2 URL, e.g. jdbc:h2:file:C:/Users/<u>/AppData/Roaming/.../data/presentationdb;AUTO_SERVER=TRUE */
function userDataDbUrl() {
  const dir = dataDir();
  fs.mkdirSync(dir, { recursive: true });
  const dbFile = path.join(dir, 'presentationdb').replace(/\\/g, '/');
  return `jdbc:h2:file:${dbFile};AUTO_SERVER=TRUE`;
}

function sendLog(line) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('server:log', line);
  }
}

function createWindow() {
  createSplash();

  const bounds = settings.rememberBounds && readBounds();

  mainWindow = new BrowserWindow({
    width: bounds ? bounds.width : 1280,
    height: bounds ? bounds.height : 820,
    x: bounds ? bounds.x : undefined,
    y: bounds ? bounds.y : undefined,
    minWidth: 1040,
    minHeight: 660,
    title: 'SciVerse Summit Chair',
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

  // Fixed-timer handoff: close the splash and reveal the app after SPLASH_MS.
  splashTimer = setTimeout(() => {
    splashTimer = null;
    try {
      if (splashWindow && !splashWindow.isDestroyed()) splashWindow.close();
    } catch { /* already gone */ }
    splashWindow = null;
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.show();
  }, SPLASH_MS);

  // Server UI links that open a new window -> system browser instead.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) {
      shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  // Floating "Back to Console" button on local server pages (chair only).
  mainWindow.webContents.on('did-finish-load', () => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    try {
      if (isLocalServerPage(mainWindow.webContents.getURL())) {
        mainWindow.webContents.executeJavaScript(CONSOLE_BTN_JS).catch(() => {});
        if (notifBridgeJs) {
          mainWindow.webContents.executeJavaScript(notifBridgeJs).catch(() => {});
        }
      }
    } catch { /* navigation race — next load retries */ }
  });

  // The in-app bell carries the unread list, so once the window is actually
  // being looked at, the taskbar count has done its job.
  mainWindow.on('focus', () => {
    notifier.clearBadge();
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Closing the window would stop a live session, so with "minimise to tray"
  // on it hides to the tray instead. Tray off => a real close. If the tray
  // could not be created (no usable icon), fall back to closing for real --
  // hiding a window with no tray to restore it from would strand the user.
  mainWindow.on('close', (e) => {
    if (!quitting && settings.tray && tray) {
      e.preventDefault();
      mainWindow.hide();
      if (tray && process.platform === 'darwin') mainWindow.show();
      notifyMinimisedToTray();
    }
  });

  if (settings.rememberBounds) {
    const save = () => {
      if (!mainWindow || mainWindow.isDestroyed() || mainWindow.isMinimized()) return;
      if (!mainWindow.isVisible()) return;
      const b = mainWindow.getNormalBounds();
      if (b.width < 900 || b.height < 600) return;
      writeBounds(b);
    };
    mainWindow.on('resized', save);
    mainWindow.on('moved', save);
    mainWindow.on('close', save);
  }
}

function boundsFile() {
  return path.join(app.getPath('userData'), 'window-bounds.json');
}

function readBounds() {
  try {
    const b = JSON.parse(fs.readFileSync(boundsFile(), 'utf8'));
    // Validate strictly: a stale/tiny/off-screen rect would otherwise create
    // an unusable window on next launch.
    if (!b || typeof b.width !== 'number' || typeof b.height !== 'number') return null;
    if (b.width < 900 || b.height < 600) return null;
    const area = require('electron').screen.getDisplayMatching(b).workArea;
    const onScreen = b.x < area.x + area.width - 80 && b.x + b.width > area.x + 80
      && b.y < area.y + area.height - 40 && b.y + b.height > area.y;
    if (!onScreen) return null;
    b.width = Math.min(b.width, area.width);
    b.height = Math.min(b.height, area.height);
    return b;
  } catch (e) {
    return null;
  }
}

function writeBounds(b) {
  try { fs.writeFileSync(boundsFile(), JSON.stringify(b)); } catch (e) { /* non-fatal */ }
}

/* ── Native notifications ───────────────────────────────── */
const notifier = createNotificationService({
  isEnabled: () => !!settings.desktopNotifications,
  getWindow: () => mainWindow,
});

/* ── Tray ───────────────────────────────────────────────── */
function createTray() {
  if (tray) return tray;
  let image = null;
  for (const rel of [path.join('..', 'build', 'icon.png'), path.join('..', 'src', 'logo.png')]) {
    try {
      const candidate = nativeImage.createFromPath(path.join(__dirname, rel));
      if (candidate && !candidate.isEmpty()) { image = candidate; break; }
    } catch (e) { /* try the next candidate */ }
  }
  if (!image) return null;
  try { image = image.resize({ width: 16, height: 16 }); } catch (e) { /* noop */ }

  tray = new Tray(image);
  tray.setToolTip('SciVerse Summit Chair');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Show Console', click: () => showMainWindow() },
    { type: 'separator' },
    {
      label: 'Stop Session Server',
      click: async () => {
        await stopServerProc();
        if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('server:log', 'Server stopped from the tray.');
      },
    },
    { type: 'separator' },
    { label: 'Quit SciVerse Summit', click: () => { quitting = true; app.quit(); } },
  ]));
  tray.on('click', () => showMainWindow());
  return tray;
}

function showMainWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

/* Closing the window looks like quitting, but the app is still there and a
 * live session keeps running. Say so once, otherwise people assume the server
 * died and try to restart it. Once per launch: it teaches the behaviour, then
 * it is just noise. */
let trayNoticeShown = false;
function notifyMinimisedToTray() {
  if (trayNoticeShown) return;
  trayNoticeShown = true;
  if (!tray) return;
  if (!Notification || !Notification.isSupported()) return;

  const live = !!serverProc;
  const notice = new Notification({
    title: 'Still running in the tray',
    body: live
      ? 'Your session server is still running, so your delegates stay connected. Click here to reopen the console, or right-click the tray icon to stop the server.'
      : 'The console stays open in the background. Click here to bring it back.',
  });
  notice.on('click', () => showMainWindow());
  notice.show();
}

function stopServerProc() {
  return new Promise((resolve) => {
    if (!serverProc) {
      resolve('not-running');
      return;
    }
    const proc = serverProc;
    serverProc = null;
    proc.once('exit', () => resolve('stopped'));
    try {
      proc.kill();
    } catch {
      resolve('stopped');
      return;
    }
    // Safety net in case the JVM ignores the first signal.
    setTimeout(() => {
      try {
        if (!proc.killed) proc.kill('SIGKILL');
      } catch { /* already gone */ }
      resolve('stopped');
    }, 8000).unref();
  });
}

ipcMain.handle('server:start', async () => {
  if (serverProc) return 'already-running';
  const { jar, java } = serverPaths();
  if (!fs.existsSync(jar)) {
    throw new Error(`Server jar not found: ${jar}`);
  }
  if (!fs.existsSync(java)) {
    throw new Error(`Bundled Java not found: ${java}`);
  }
  const env = { ...process.env, SPRING_DATASOURCE_URL: userDataDbUrl() };
  sendLog(`DB -> ${env.SPRING_DATASOURCE_URL}`);
  serverProc = spawn(java, ['-jar', jar], { env });
  serverProc.stdout.on('data', (d) => sendLog(String(d).trimEnd()));
  serverProc.stderr.on('data', (d) => sendLog(String(d).trimEnd()));
  serverProc.on('exit', (code, signal) => {
    sendLog(`Server process exited (code=${code} signal=${signal})`);
    serverProc = null;
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('server:exited');
  });
  serverProc.on('error', (err) => {
    sendLog('ERROR spawning server: ' + err.message);
    serverProc = null;
  });
  return 'started';
});

ipcMain.handle('server:stop', async () => stopServerProc());

ipcMain.handle('server:status', async () => (serverProc ? 'running' : 'stopped'));

// Back-to-console: the web UI is server-rendered and untouched — instead the
// shell injects a floating button on local server pages that returns here.
ipcMain.handle('app:console', async () => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    // skipSplash: the in-app loader plays on first open only — coming back
    // from the Console button must land straight on the console.
    mainWindow.loadFile(path.join(__dirname, '..', 'src', 'index.html'), {
      query: { skipSplash: '1' },
    });
    return 'console';
  }
  return 'no-window';
});

function isLocalServerPage(urlString) {
  try {
    const u = new URL(urlString);
    if (u.protocol !== 'http:') return false;
    const host = u.hostname;
    const port = u.port || '80';
    return (host === '127.0.0.1' || host === 'localhost') && port === '8080';
  } catch {
    return false;
  }
}

// Shared notification bridge, injected into every server page. Idempotent:
// the script guards itself, so re-injecting on each load is harmless.
let notifBridgeJs = '';
try {
  notifBridgeJs = fs.readFileSync(path.join(__dirname, '..', 'src', 'notif-bridge.js'), 'utf8');
} catch (err) {
  console.error('Could not load notif-bridge.js:', err.message);
}

// Floating "Back to Console" button, injected into server pages only.
// Same no-touch approach as the delegate login override: the jar's
// templates are never modified.
const CONSOLE_BTN_JS = `(function () {
  if (document.getElementById('svConsoleBtn') || !document.body) return;
  var b = document.createElement('button');
  b.id = 'svConsoleBtn';
  b.type = 'button';
  b.title = 'Back to the launcher console (stop the server)';
  b.textContent = '\\u25C9 Console';
  b.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;'
    + 'border:none;border-radius:999px;padding:10px 18px;font-weight:700;font-size:0.85rem;'
    + 'cursor:pointer;color:#fff;background:linear-gradient(135deg,#06b6d4,#7c3aed);'
    + "font-family:'Segoe UI',system-ui,sans-serif;"
    + 'box-shadow:0 10px 28px rgba(124,58,237,0.45);';
  b.addEventListener('click', function () {
    if (window.summitAPI && window.summitAPI.backToConsole) window.summitAPI.backToConsole();
  });
  document.body.appendChild(b);
})();`;

ipcMain.handle('server:lan-address', async () => {
  // Best-effort LAN address (no traffic is sent).
  return new Promise((resolve, reject) => {
    const sock = dgram.createSocket('udp4');
    sock.on('error', (err) => {
      sock.close();
      reject(err);
    });
    sock.connect(80, '8.8.8.8', () => {
      try {
        const addr = sock.address().address;
        sock.close();
        resolve(addr);
      } catch (err) {
        sock.close();
        reject(err);
      }
    });
  });
});

ipcMain.handle('app:version', async () => app.getVersion());

/* ── Readiness probe for the local server ───────────────── */
ipcMain.handle('server:probe', async (_e, req) => {
  const { host, port, path, timeout } = (req || {});
  return probeServer(host, port, path || '/login', timeout);
});

/* ── Settings ──────────────────────────────────────────── */
ipcMain.handle('settings:get', async () => settings);

ipcMain.handle('settings:set', async (_e, next) => {
  if (next && typeof next === 'object') {
    settings = Object.assign({}, SETTINGS_DEFAULTS, next);
    saveSettings();
  }
  return settings;
});

/* ── System info for the console's System panel ─────────── */
let cachedJavaVersion = null;
// `java -version` writes to stderr, so both streams are captured. Runs async so
// it never stalls the main process.
function javaVersion() {
  if (cachedJavaVersion) return Promise.resolve(cachedJavaVersion);
  const { java } = serverPaths();
  return new Promise((resolve) => {
    let out = '';
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      const m = out.match(/version "([^"]+)"/);
      cachedJavaVersion = m ? 'Java ' + m[1] : 'Java runtime';
      resolve(cachedJavaVersion);
    };
    let child;
    try {
      child = spawn(java, ['-version'], { windowsHide: true });
    } catch (e) {
      cachedJavaVersion = 'Not detected';
      return resolve(cachedJavaVersion);
    }
    const timer = setTimeout(finish, 6000);
    child.stdout.on('data', (d) => { out += String(d); });
    child.stderr.on('data', (d) => { out += String(d); });
    child.on('error', () => { cachedJavaVersion = 'Not detected'; resolve(cachedJavaVersion); });
    child.on('close', () => { clearTimeout(timer); finish(); });
  });
}

ipcMain.handle('system:info', async () => ({
  appVersion: app.getVersion(),
  electron: process.versions.electron,
  chrome: process.versions.chrome,
  platform: process.platform,
  arch: process.arch,
  port: PORT,
  dataDir: dataDir(),
  // Async: a synchronous spawn here would block the main process (and with it
  // window painting) for as long as the JVM takes to answer.
  java: await javaVersion(),
}));

ipcMain.handle('data:open', async () => {
  try {
    fs.mkdirSync(dataDir(), { recursive: true });
    await shell.openPath(dataDir());
    return true;
  } catch (e) {
    return false;
  }
});

ipcMain.handle('data:reset', async () => {
  // The JVM holds the H2 files open, so only clear a stopped server.
  if (serverProc) return { ok: false, reason: 'running' };
  try {
    const dir = dataDir();
    if (!fs.existsSync(dir)) return { ok: false, reason: 'missing' };
    for (const entry of fs.readdirSync(dir)) {
      fs.rmSync(path.join(dir, entry), { recursive: true, force: true });
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, reason: e.message };
  }
});

/* ── Native notification bridge ─────────────────────────── */
ipcMain.handle('notif:show', async (_e, payload) => notifier.show(payload || {}));

ipcMain.handle('notif:enabled', async () => !!settings.desktopNotifications);

ipcMain.handle('notif:set-enabled', async (_e, value) => {
  settings.desktopNotifications = !!(value && value.enabled);
  saveSettings();
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
    autoUpdater.channel = 'chair';
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
  loadSettings();
  createWindow();
  wireUpdater(mainWindow);
  if (settings.tray) createTray();

  // Auto-updates from GitHub Releases (MystiTheDev/sciverse-summit) —
  // ask-first, no OS notification, no silent download. Windows follows the
  // `chair` channel, Linux follows `chair-linux` (separate feed files, one repo).
  if (app.isPackaged) {
    autoUpdater.channel = 'chair'; // same on all platforms: the builder appends -linux itself
    autoUpdater.checkForUpdates().catch(() => {});
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('before-quit', async () => {
  quitting = true;
  await stopServerProc();
});

// With "minimise to tray" on, closing the last window must NOT end the app.
app.on('window-all-closed', () => {
  if (settings.tray) return;
  if (process.platform !== 'darwin') app.quit();
});
