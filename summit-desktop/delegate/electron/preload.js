'use strict';

/* Preload: minimal bridge. The delegate launcher is static and needs no
 * privileged APIs today; version is exposed for a future skew warning. */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('summitAPI', {
  getVersion: () => ipcRenderer.invoke('app:version'),
  onUpdateChecking: (cb) => { const l = () => cb(); ipcRenderer.on('update:checking', l); return () => ipcRenderer.removeListener('update:checking', l); },
  onUpdateAvailable: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:available', l); return () => ipcRenderer.removeListener('update:available', l); },
  onUpdateNotAvailable: (cb) => { const l = () => cb(); ipcRenderer.on('update:not-available', l); return () => ipcRenderer.removeListener('update:not-available', l); },
  onUpdateProgress: (cb) => { const l = (_e, p) => cb(p); ipcRenderer.on('update:progress', l); return () => ipcRenderer.removeListener('update:progress', l); },
  onUpdateDownloaded: (cb) => { const l = (_e, info) => cb(info); ipcRenderer.on('update:downloaded', l); return () => ipcRenderer.removeListener('update:downloaded', l); },
  onUpdateError: (cb) => { const l = (_e, msg) => cb(msg); ipcRenderer.on('update:error', l); return () => ipcRenderer.removeListener('update:error', l); },
  downloadUpdate: () => ipcRenderer.invoke('update:download'),
  restartToUpdate: () => ipcRenderer.invoke('update:restart'),
});
