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

const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const dgram = require('dgram');
const { spawn } = require('child_process');
const { autoUpdater } = require('electron-updater');

let mainWindow = null;
let splashWindow = null;
let splashTimer = null;
let serverProc = null;

// Native splash: logo + name for a fixed brand moment before the app opens.
const SPLASH_MS = 14000;

function createSplash() {
  splashWindow = new BrowserWindow({
    width: 440,
    height: 600,
    title: 'SciVerse Summit Chair',
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
  const dir = path.join(app.getPath('userData'), 'data');
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

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    title: 'SciVerse Summit Chair',
    autoHideMenuBar: true,
    show: false,
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
      }
    } catch { /* navigation race — next load retries */ }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
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
  serverProc.stderr.on('data', (d) => sendLog('ERR ' + String(d).trimEnd()));
  serverProc.on('exit', (code, signal) => {
    sendLog(`Server process exited (code=${code} signal=${signal})`);
    serverProc = null;
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

ipcMain.handle('update:download', async () => {
  autoUpdater.downloadUpdate().catch(() => {});
});
ipcMain.handle('update:restart', async () => {
  autoUpdater.quitAndInstall();
});

function wireUpdater(win) {
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;
  autoUpdater.on('checking-for-update', () => { if (win && !win.isDestroyed()) win.webContents.send('update:checking'); });
  autoUpdater.on('update-available', (info) => { if (win && !win.isDestroyed()) win.webContents.send('update:available', info); });
  autoUpdater.on('update-not-available', () => { if (win && !win.isDestroyed()) win.webContents.send('update:not-available'); });
  autoUpdater.on('download-progress', (p) => { if (win && !win.isDestroyed()) win.webContents.send('update:progress', p); });
  autoUpdater.on('update-downloaded', (info) => { if (win && !win.isDestroyed()) win.webContents.send('update:downloaded', info); });
  autoUpdater.on('error', (err) => { if (win && !win.isDestroyed()) win.webContents.send('update:error', String((err && err.message) || err)); });
}

app.whenReady().then(() => {
  createWindow();
  wireUpdater(mainWindow);

  // Auto-updates from GitHub Releases (MystiTheDev/sciverse-summit) —
  // ask-first, no OS notification, no silent download. Windows follows the
  // `chair` channel, Linux follows `chair-linux` (separate feed files, one repo).
  if (app.isPackaged) {
    autoUpdater.channel = process.platform === 'linux' ? 'chair-linux' : 'chair';
    autoUpdater.checkForUpdates().catch(() => {});
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('before-quit', async () => {
  await stopServerProc();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
