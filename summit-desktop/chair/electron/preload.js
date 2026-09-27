'use strict';

/* Preload: exposes a minimal, auditable bridge to the launcher UI.
 * contextIsolation stays on; the renderer never gets raw ipcRenderer. */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('summitAPI', {
  startServer: () => ipcRenderer.invoke('server:start'),
  stopServer: () => ipcRenderer.invoke('server:stop'),
  serverStatus: () => ipcRenderer.invoke('server:status'),
  backToConsole: () => ipcRenderer.invoke('app:console'),
  lanAddress: () => ipcRenderer.invoke('server:lan-address'),
  probeServer: (host, port, path, timeout) => ipcRenderer.invoke('server:probe', { host: host, port: port, path: path, timeout: timeout }),
  getVersion: () => ipcRenderer.invoke('app:version'),
  onLog: (cb) => {
    const listener = (_event, line) => cb(line);
    ipcRenderer.on('server:log', listener);
    return () => ipcRenderer.removeListener('server:log', listener);
  },
  onServerExit: (cb) => {
    const l = () => cb();
    ipcRenderer.on('server:exited', l);
    return () => ipcRenderer.removeListener('server:exited', l);
  },
  getSettings: () => ipcRenderer.invoke('settings:get'),
  setSettings: (s) => ipcRenderer.invoke('settings:set', s),
  getSystemInfo: () => ipcRenderer.invoke('system:info'),
  openDataFolder: () => ipcRenderer.invoke('data:open'),
  resetData: () => ipcRenderer.invoke('data:reset'),
  checkForUpdates: () => ipcRenderer.invoke('update:check'),
  onManualUpdateResult: (cb) => { const l = (_e, r) => cb(r); ipcRenderer.on('update:manual', l); return () => ipcRenderer.removeListener('update:manual', l); },
  onUpdateChecking: (cb) => { const l = () => cb(); ipcRenderer.on('update:checking', l); return () => ipcRenderer.removeListener('update:checking', l); },
  onUpdateAvailable: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:available', l); return () => ipcRenderer.removeListener('update:available', l); },
  onUpdateNotAvailable: (cb) => { const l = () => cb(); ipcRenderer.on('update:not-available', l); return () => ipcRenderer.removeListener('update:not-available', l); },
  onUpdateProgress: (cb) => { const l = (_e, p) => cb(p); ipcRenderer.on('update:progress', l); return () => ipcRenderer.removeListener('update:progress', l); },
  onUpdateDownloaded: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:downloaded', l); return () => ipcRenderer.removeListener('update:downloaded', l); },
  onUpdateError: (cb) => { const l = (_e, msg) => cb(msg); ipcRenderer.on('update:error', l); return () => ipcRenderer.removeListener('update:error', l); },
  downloadUpdate: () => ipcRenderer.invoke('update:download'),
  restartToUpdate: () => ipcRenderer.invoke('update:restart'),
});
