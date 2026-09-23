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
  getVersion: () => ipcRenderer.invoke('app:version'),
  onLog: (cb) => {
    const listener = (_event, line) => cb(line);
    ipcRenderer.on('server:log', listener);
    return () => ipcRenderer.removeListener('server:log', listener);
  },
  onUpdateChecking: (cb) => { const l = () => cb(); ipcRenderer.on('update:checking', l); return () => ipcRenderer.removeListener('update:checking', l); },
  onUpdateAvailable: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:available', l); return () => ipcRenderer.removeListener('update:available', l); },
  onUpdateNotAvailable: (cb) => { const l = () => cb(); ipcRenderer.on('update:not-available', l); return () => ipcRenderer.removeListener('update:not-available', l); },
  onUpdateProgress: (cb) => { const l = (_e, p) => cb(p); ipcRenderer.on('update:progress', l); return () => ipcRenderer.removeListener('update:progress', l); },
  onUpdateDownloaded: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:downloaded', l); return () => ipcRenderer.removeListener('update:downloaded', l); },
  onUpdateError: (cb) => { const l = (_e, msg) => cb(msg); ipcRenderer.on('update:error', l); return () => ipcRenderer.removeListener('update:error', l); },
  downloadUpdate: () => ipcRenderer.invoke('update:download'),
  restartToUpdate: () => ipcRenderer.invoke('update:restart'),
});
