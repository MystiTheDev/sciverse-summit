'use strict';

/* Preload: minimal bridge. Exposes only what the delegate UI needs. */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('summitAPI', {
  getVersion: () => ipcRenderer.invoke('app:version'),
  getSettings: () => ipcRenderer.invoke('settings:get'),
  setSettings: (s) => ipcRenderer.invoke('settings:set', s),
  getRecent: () => ipcRenderer.invoke('recent:list'),
  addRecent: (h) => ipcRenderer.invoke('recent:add', h),
  clearRecent: () => ipcRenderer.invoke('recent:clear'),
  getLastHost: () => ipcRenderer.invoke('recent:last'),
  probeServer: (host, port, path, timeout) => ipcRenderer.invoke('server:probe', { host: host, port: port, path: path, timeout: timeout }),
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
