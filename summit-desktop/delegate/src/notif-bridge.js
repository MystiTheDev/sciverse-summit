/* SciVerse Summit — native notification bridge (shared by Chair and Delegate).
 *
 * Injected into every page the app loads from a session server. The website
 * already has a full notification system, but its browser Notification API
 * path is gated on `document.hidden`, which is never true for a visible app
 * window, so nothing reached the desktop. This bridge watches the same
 * notification feed and hands each new item to the main process, which shows a
 * real native notification instead.
 *
 * Two sources, both needed:
 *   - SSE `notif.changed`, for instant delivery.
 *   - A 5s poll of /api/notifications, matching the website's own cadence.
 * The poll is not redundant: batch sends (voting, motion outcome, session
 * ended) previously published only the first delegate's payload, so most
 * delegate notifications only ever arrived via the poll.
 *
 * Strictly a no-op when window.summitAPI is absent, so the same page running
 * in an ordinary browser is unaffected.
 */
(function () {
  'use strict';

  var api = window.summitAPI;
  if (!api || typeof api.showDesktopNotification !== 'function') return;

  var POLL_MS = 5000;
  var SEEN_KEY = 'summitNativeNotifSeen';

  // Per-page + per-session dedup, so a notification surfaces once even as the
  // user moves between pages of the same origin.
  var seen = Object.create(null);
  try {
    var stored = window.sessionStorage.getItem(SEEN_KEY);
    if (stored) {
      var parsed = JSON.parse(stored);
      for (var k in parsed) if (Object.prototype.hasOwnProperty.call(parsed, k)) seen[k] = true;
    }
  } catch (e) { /* private mode or storage disabled */ }

  function remember(id) {
    seen[id] = true;
    try {
      // Keep the ledger bounded: only recent ids matter.
      var keys = Object.keys(seen);
      if (keys.length > 500) {
        keys.slice(0, keys.length - 500).forEach(function (old) { delete seen[old]; });
      }
      window.sessionStorage.setItem(SEEN_KEY, JSON.stringify(seen));
    } catch (e) { /* non-fatal */ }
  }

  function alreadySeen(id) {
    return id != null && seen[id] === true;
  }

  function currentUser() {
    var el = document.getElementById('svCurrentUser');
    if (el && el.textContent) return el.textContent.trim();
    var meta = document.querySelector('meta[name="current-user"]');
    if (meta && meta.content) return meta.content.trim();
    return '';
  }

  function forward(n) {
    if (!n || n.id == null) return;
    if (alreadySeen(n.id)) return;
    // Defence in depth: never surface another user's notification, even if a
    // payload ever arrives that is not addressed to us.
    var me = currentUser();
    if (n.userId && me && n.userId !== me) return;

    remember(n.id);
    try {
      api.showDesktopNotification({
        id: n.id,
        type: n.type || 'GENERAL',
        title: n.title || 'Notification',
        message: n.message || '',
        link: n.link || ''
      });
    } catch (e) { /* bridge must never break the page */ }
  }

  function poll() {
    if (typeof window.fetch !== 'function') return;
    window.fetch('/api/notifications', { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var items = (data && data.items) || [];
        for (var i = 0; i < items.length; i++) {
          var n = items[i];
          // Unread and unseen: matches how the website decides what is new.
          if (n && !n.read && !alreadySeen(n.id)) forward(n);
        }
      })
      .catch(function () { /* server unreachable; the next tick retries */ });
  }

  if (window.SummitLive && typeof window.SummitLive.on === 'function') {
    window.SummitLive.on('notif.changed', function (evt) {
      var raw = evt && evt.data;
      if (raw) {
        try {
          var n = JSON.parse(raw);
          if (n && n.id) {
            forward(n);
            return;
          }
        } catch (e) { /* not JSON: fall through to the poll */ }
      }
      poll();
    });
  }

  // Catch anything that arrived while this page was loading, and keep
  // catching what the SSE stream misses.
  poll();
  setInterval(poll, POLL_MS);
})();
