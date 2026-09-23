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

let mainWindow = null;
let splashWindow = null;
let splashTimer = null;
let loginOverrideJs = '';

// Native splash: logo + name for a fixed brand moment before the app opens.
const SPLASH_MS = 14000;

function createSplash() {
  splashWindow = new BrowserWindow({
    width: 440,
    height: 600,
    title: 'SciVerse Summit Delegate',
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
    width: 1100,
    height: 750,
    title: 'SciVerse Summit Delegate',
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

  // Inject the custom /login design only on the server's login page.
  // did-finish-load fires on every full navigation, including the redirect
  // to the chair server and the ?error reload after a failed attempt.
  mainWindow.webContents.on('did-finish-load', () => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    const url = mainWindow.webContents.getURL();
    if (isServerLogin(url) && loginOverrideJs) {
      mainWindow.webContents.executeJavaScript(loginOverrideJs).catch(() => {});
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) {
      shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

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
    autoUpdater.channel = process.platform === 'linux' ? 'delegate-linux' : 'delegate';
    autoUpdater.checkForUpdates().catch(() => {});
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
