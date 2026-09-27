'use strict';

/* Native notification handling for the desktop app.
 *
 * The website fires desktop notifications through the browser Notification
 * API, but only when `document.hidden` is true — never the case for a visible
 * app window. This module is the app-side replacement: the injected bridge
 * hands over each new notification and we raise a real OS notification.
 *
 * Policy (see the plan):
 *   - Only when the window is not focused. The in-app toast already covers the
 *     focused case, so a desktop popup on top of it would just be noise.
 *   - An unread count on the taskbar, plus a window flash.
 *   - Respect the user's setting, and the OS's own Focus Assist / DND, which
 *     Windows applies to Notification for us.
 */

const { app, Notification, nativeImage } = require('electron');
const path = require('path');

/** How chatty each kind is, mapped to a native urgency hint. */
const URGENCY = {
  VOTING: 'critical',
  SPEAKER: 'critical',
  SESSION_ENDED: 'critical',
  DELEGATE_LEFT: 'normal',
  MOTION: 'normal',
  RESOLUTION: 'normal',
  DELEGATE_JOINED: 'low',
  GENERAL: 'low',
};

let icon = null;
let unread = 0;

function appIcon() {
  if (icon !== null) return icon;
  for (const rel of [path.join('..', 'build', 'icon.png'), path.join('..', 'src', 'logo.png')]) {
    try {
      const candidate = nativeImage.createFromPath(path.join(__dirname, rel));
      if (candidate && !candidate.isEmpty()) {
        icon = candidate.resize({ width: 64, height: 64 });
        return icon;
      }
    } catch (e) { /* try the next candidate */ }
  }
  icon = false;
  return false;
}

/**
 * @param {object} opts
 * @param {() => boolean} opts.isEnabled  user's desktop-notification setting
 * @param {() => object|null} opts.getWindow  the app window, or null
 * @param {(n: object) => void} [opts.onOpen] invoked when a notification is clicked
 */
function createNotificationService(opts) {
  const isEnabled = opts.isEnabled;
  const getWindow = opts.getWindow;
  const onOpen = opts.onOpen || function () {};

  function clearBadge() {
    unread = 0;
    const win = getWindow();
    if (win && !win.isDestroyed()) {
      try { win.flashFrame(false); } catch (e) { /* not supported everywhere */ }
    }
    try { app.setBadgeCount(0); } catch (e) { /* unsupported platform */ }
  }

  function bumpBadge() {
    unread += 1;
    try { app.setBadgeCount(unread); } catch (e) { /* unsupported */ }
    const win = getWindow();
    if (win && !win.isDestroyed() && !win.isFocused()) {
      try { win.flashFrame(true); } catch (e) { /* not supported everywhere */ }
    }
  }

  /**
   * @param {{id:*, type?:string, title?:string, message?:string, link?:string}} n
   */
  function show(n) {
    if (!n || !isEnabled()) return false;
    if (!Notification || !Notification.isSupported()) return false;

    const win = getWindow();

    // The in-app toast already covers a focused window.
    if (win && !win.isDestroyed() && win.isFocused()) {
      return false;
    }

    const type = String(n.type || 'GENERAL').toUpperCase();
    const notice = new Notification({
      title: n.title || 'SciVerse Summit',
      body: n.message || '',
      urgency: URGENCY[type] || 'normal',
      timeoutType: type === 'VOTING' || type === 'SPEAKER' ? 'never' : 'default',
      silent: false,
    });

    const iconImage = appIcon();
    if (iconImage) notice.icon = iconImage;

    notice.on('click', () => {
      const w = getWindow();
      if (w && !w.isDestroyed()) {
        if (w.isMinimized()) w.restore();
        w.show();
        w.focus();
        // Speaker alerts are deliberately link-free, so there is nothing to
        // navigate to; just bringing the window forward is the right action.
        if (n.link) {
          try { w.webContents.loadURL(n.link); } catch (e) { /* navigation raced a teardown */ }
        }
      }
      onOpen(n);
    });

    notice.show();
    bumpBadge();
    return true;
  }

  return { show, clearBadge, isSupported: function () { return !!(Notification && Notification.isSupported()); } };
}

module.exports = { createNotificationService, URGENCY };
