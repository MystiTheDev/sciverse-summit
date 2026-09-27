/* SciVerse Summit — shared update UI (Chair + Delegate).
 *
 * Consolidates what used to be an ~80-line copy of the same overlay logic in
 * both apps' renderer code, where the two copies had already drifted.
 *
 * Two kinds of update, decided by a marker line at the top of the GitHub
 * release body:
 *
 *     type: update     (or absent) -> routine update, compact overlay
 *     type: upgrade               -> major upgrade, full-screen panel
 *
 * Everything after that first line is the changelog, rendered from a small,
 * safe subset of markdown.
 *
 * A no-op when window.summitAPI is absent, so the page still works in a plain
 * browser.
 */
(function () {
  'use strict';

  var api = window.summitAPI;
  if (!api) return;
  if (window.SummitUpdateUI) return;

  var TIPS = (typeof UPDATE_TIPS !== 'undefined' && Array.isArray(UPDATE_TIPS) && UPDATE_TIPS.length)
    ? UPDATE_TIPS
    : ['Tip: Updates install on the next restart.'];

  var ctx = { serverRunning: false, backToConsole: null };
  // The version being offered (from the update feed), as opposed to the
  // version currently installed, which is what the upgrade panel must report.
  var latestVersion = '';
  var tipTimer = null;
  var tipIndex = 0;

  function $(id) { return document.getElementById(id); }

  /* ── Release body parsing ─────────────────────────────── */

  /**
   * Splits a GitHub release body into its type marker and changelog.
   * `notes` is a string, or an array of { version, note } from electron-updater.
   * @returns {{type: 'update'|'upgrade', body: string}}
   */
  function parseRelease(notes) {
    var text = '';
    if (typeof notes === 'string') {
      text = notes;
    } else if (Array.isArray(notes) && notes.length) {
      // fullChangelog could hand us several; the newest is first.
      text = (notes[0] && (notes[0].note || notes[0].releaseNote)) || '';
    }
    text = String(text || '').replace(/\r\n/g, '\n');

    // Accepts "type: upgrade", "**Type:** upgrade", "- type: upgrade".
    var marker = /^[ \t]*(?:[-*+][ \t]*)?(?:\*\*|__)?[ \t]*type[ \t]*:?[ \t]*(?:\*\*|__)?[ \t]*(update|upgrade)[ \t]*(?:\*\*|__)?[ \t]*(?:\n|$)/i;
    var m = marker.exec(text);
    if (!m) return { type: 'update', body: text.trim() };
    return { type: m[1].toLowerCase(), body: text.slice(m[0].length).trim() };
  }

  /* ── Safe markdown subset ─────────────────────────────── */

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function inline(s) {
    // Callers pass already-escaped text, so only formatting is applied here.
    return esc(s)
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[\s(])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
        '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
  }

  /**
   * Renders a safe subset: headings, bullet lists, blockquotes, paragraphs,
   * inline code/bold/italic/links. All input is escaped first, so release
   * notes can never inject markup.
   */
  function renderMarkdown(md) {
    var lines = String(md == null ? '' : md).split('\n');
    var out = [];
    var para = [];

    // One entry per currently-open <ul>, so nesting can be closed back out to
    // the right level. `liOpen` records whether that level has an <li> still
    // awaiting its closing tag, which is what lets a nested <ul> sit inside
    // its parent <li> rather than beside it.
    var stack = [];

    function flushPara() {
      if (para.length) { out.push('<p>' + inline(para.join(' ')) + '</p>'); para = []; }
    }
    function closeListsTo(n) {
      while (stack.length > n) {
        var top = stack.pop();
        if (top.liOpen) { out.push('</li>'); top.liOpen = false; }
        out.push('</ul>');
      }
    }
    function flushList() { closeListsTo(0); }

    // Two spaces per level, and capped so a pathological indent cannot
    // produce unbounded nesting.
    var MAX_DEPTH = 4;
    function depthOf(line) {
      var lead = (/^[ \t]*/.exec(line)[0]).replace(/\t/g, '  ').length;
      return Math.min(Math.floor(lead / 2), MAX_DEPTH);
    }

    function pushBullet(depth, text) {
      flushPara();
      var target = depth + 1;
      closeListsTo(target);
      while (stack.length < target) { out.push('<ul>'); stack.push({ liOpen: false }); }
      // A sibling bullet closes the previous <li> before starting this one.
      var top = stack[stack.length - 1];
      if (top.liOpen) { out.push('</li>'); top.liOpen = false; }
      out.push('<li>' + inline(text));
      top.liOpen = true;
    }

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var t = line.trim();

      if (!t) { flushPara(); flushList(); continue; }

      var h = /^(#{1,6})\s+(.*)$/.exec(t);
      if (h) {
        flushPara(); flushList();
        var level = Math.min(h[1].length, 4);
        out.push('<h' + level + ' class="sv-md-h sv-md-h' + level + '">' + inline(h[2]) + '</h' + level + '>');
        continue;
      }

      // GHR-style "## Added" headings are common in release notes.
      var q = /^&gt;\s?(.*)$/.exec(t) || /^>\s?(.*)$/.exec(t);
      if (q) {
        flushPara(); flushList();
        out.push('<blockquote>' + inline(q[1]) + '</blockquote>');
        continue;
      }

      // Depth comes from the raw line, so indented bullets keep their level.
      var b = /^[-*+]\s+(.*)$/.exec(t);
      if (b) {
        pushBullet(depthOf(line), b[1]);
        continue;
      }

      var numbered = /^\d+[.)]\s+(.*)$/.exec(t);
      if (numbered) {
        pushBullet(depthOf(line), numbered[1]);
        continue;
      }

      flushList();
      para.push(t);
    }
    flushPara();
    flushList();
    return out.join('');
  }

  /**
   * The version running right now. Taken from the page's own version readout
   * (`#appVersion`, which both apps already populate from the main process),
   * falling back to anything the host passed in via attach().
   */
  function installedVersion() {
    var explicit = ctx.installedVersion;
    if (explicit) return String(explicit).replace(/^v/i, '');
    var el = $('appVersion');
    var t = el ? String(el.textContent || '').trim() : '';
    return t.replace(/^v/i, '');
  }

  /* ── Tips ─────────────────────────────────────────────── */

  function showTip() {
    var el = $('updateTip');
    if (!el) return;
    el.style.opacity = '0';
    setTimeout(function () {
      el.textContent = TIPS[tipIndex % TIPS.length];
      el.style.opacity = '1';
      tipIndex++;
    }, 320);
  }
  function startTips() { tipIndex = Math.floor(Math.random() * TIPS.length); showTip(); tipTimer = setInterval(showTip, 3200); }
  function stopTips() { if (tipTimer) clearInterval(tipTimer); tipTimer = null; }

  /* ── Overlays ─────────────────────────────────────────── */

  function showOverlay() {
    var o = $('updateOverlay');
    if (o) { o.classList.add('open'); o.setAttribute('aria-hidden', 'false'); }
  }
  function hideOverlay() {
    var o = $('updateOverlay');
    if (o) { o.classList.remove('open'); o.setAttribute('aria-hidden', 'true'); }
    stopTips();
  }
  function showUpgrade() {
    var u = $('upgradePanel');
    if (u) { u.classList.add('open'); u.setAttribute('aria-hidden', 'false'); }
  }
  function hideUpgrade() {
    var u = $('upgradePanel');
    if (u) { u.classList.remove('open'); u.setAttribute('aria-hidden', 'true'); }
  }

  /**
   * @param {number} percent
   * @param {object} [progress] the raw progress event, for the speed readout
   */
  function setBar(percent, progress) {
    var bar = $('updateBar');
    var pct = $('updatePct');
    var speed = $('updateSpeed');
    if (bar) bar.style.width = percent + '%';
    if (pct) pct.textContent = percent + '%';
    if (speed) speed.textContent = fmtSpeed(progress && progress.bytesPerSecond);
  }

  function fmtSpeed(bps) {
    if (!bps || bps <= 0) return '';
    var kb = bps / 1024;
    return kb > 1024 ? (kb / 1024).toFixed(1) + ' MB/s' : Math.round(kb) + ' KB/s';
  }

  function wireActions(container, handlers) {
    if (!container) return;
    var dl = container.querySelector('#updDownloadBtn');
    var later = container.querySelector('#updLaterBtn');
    if (dl && dl.dataset.wired !== '1') {
      dl.dataset.wired = '1';
      dl.addEventListener('click', handlers.download);
    }
    if (later && later.dataset.wired !== '1') {
      later.dataset.wired = '1';
      later.addEventListener('click', handlers.dismiss);
    }
  }

  /* ── Server-running guard (chair only) ─────────────────── */

  function applyServerGuard() {
    var warn = $('upgradeServerWarning');
    var btn = $('upgUpgradeBtn');
    if (!warn || !btn) return;
    if (ctx.serverRunning) {
      warn.classList.add('show');
      btn.disabled = true;
      btn.title = 'Stop the session server before updating';
      var toConsole = $('upgBackToConsole');
      if (toConsole && typeof ctx.backToConsole === 'function') toConsole.classList.add('show');
    } else {
      warn.classList.remove('show');
      btn.disabled = false;
      btn.title = '';
    }
  }

  function refreshServerState() {
    if (typeof ctx.isServerRunning !== 'function') { applyServerGuard(); return Promise.resolve(); }
    return Promise.resolve(ctx.isServerRunning()).then(function (running) {
      ctx.serverRunning = !!running;
      applyServerGuard();
    }).catch(function () { applyServerGuard(); });
  }

  /* ── Public surface ───────────────────────────────────── */

  var UI = {
    parseRelease: parseRelease,
    renderMarkdown: renderMarkdown,

    /**
     * @param {object} o
     * @param {function} [o.isServerRunning] resolves to true when a local
     *        session server is live; gates the upgrade button for the chair
     * @param {function} [o.backToConsole] shown when gated
     * @param {string}  [o.installedVersion] overrides the version read from
     *        the page's own `#appVersion` readout
     */
    attach: function (o) {
      o = o || {};
      // Event subscriptions are not guarded by the dataset.wired checks below,
      // so a second attach() would double-fire every updater event. Merge
      // options and bail out if we are already wired.
      if (ctx.attached) {
        if (o.isServerRunning) ctx.isServerRunning = o.isServerRunning;
        if (o.backToConsole) ctx.backToConsole = o.backToConsole;
        if (o.installedVersion) ctx.installedVersion = o.installedVersion;
        return;
      }
      ctx.attached = true;
      ctx.isServerRunning = o.isServerRunning || null;
      ctx.backToConsole = o.backToConsole || null;
      if (o.installedVersion) ctx.installedVersion = o.installedVersion;

      if (typeof api.onUpdateAvailable === 'function') {
        api.onUpdateAvailable(function (info) { UI.onAvailable(info); });
      }
      if (typeof api.onUpdateProgress === 'function') {
        api.onUpdateProgress(function (p) { UI.onProgress(p); });
      }
      if (typeof api.onUpdateDownloaded === 'function') {
        api.onUpdateDownloaded(function (info) { UI.onDownloaded(info); });
      }
      if (typeof api.onUpdateError === 'function') {
        api.onUpdateError(function (msg) { UI.onError(msg); });
      }

      var restart = $('updateRestartBtn');
      if (restart && restart.dataset.wired !== '1') {
        restart.dataset.wired = '1';
        restart.addEventListener('click', function () { if (api.restartToUpdate) api.restartToUpdate(); });
      }
      var dismiss = $('updateDismissBtn');
      if (dismiss && dismiss.dataset.wired !== '1') {
        dismiss.dataset.wired = '1';
        dismiss.addEventListener('click', function () { hideOverlay(); });
      }
      var toConsole = $('upgBackToConsole');
      if (toConsole && toConsole.dataset.wired !== '1') {
        toConsole.dataset.wired = '1';
        toConsole.addEventListener('click', function () {
          if (typeof ctx.backToConsole === 'function') ctx.backToConsole();
        });
      }
      var upgLater = $('upgLaterBtn');
      if (upgLater && upgLater.dataset.wired !== '1') {
        upgLater.dataset.wired = '1';
        // A major upgrade may be deferred, but never silently: re-check later.
        upgLater.addEventListener('click', function () { hideUpgrade(); });
      }
      var upgApply = $('upgUpgradeBtn');
      if (upgApply && upgApply.dataset.wired !== '1') {
        upgApply.dataset.wired = '1';
        upgApply.addEventListener('click', function () {
          if (upgApply.disabled) return;
          hideUpgrade();
          UI.onDownloadRequested();
        });
      }
    },

    /** Version detected: decide routine overlay vs full-screen upgrade. */
    onAvailable: function (info) {
      var ver = (info && (info.version || info.tag)) ? String(info.version || info.tag) : '';
      latestVersion = ver;
      var release = parseRelease(info && info.releaseNotes);

      if (release.type === 'upgrade') {
        UI.showUpgradeScreen(ver, release.body);
        return;
      }

      // Routine update: the existing compact overlay, plus a changelog.
      var title = $('updateTitle');
      var sub = $('updateSub');
      var tipEl = $('updateTip');
      var actions = $('updateActions');
      var notes = $('updateNotes');

      if (title) title.textContent = 'Update available' + (ver ? ' v' + ver : '');
      if (sub) sub.textContent = 'A new version is ready to download. Want to update now?';
      setBar(0);
      if (tipEl) tipEl.textContent = 'Tip: You can keep working — the download only starts if you choose to.';

      if (notes) {
        if (release.body) {
          notes.innerHTML = renderMarkdown(release.body);
          notes.classList.add('show');
        } else {
          notes.innerHTML = '';
          notes.classList.remove('show');
        }
      }

      if (actions) {
        actions.innerHTML = '<button id="updDownloadBtn" class="primary">Download Update</button>'
          + '<button id="updLaterBtn" class="ghost">Later</button>';
        actions.style.display = 'flex';
        wireActions(actions, {
          download: function () {
            if (title) title.textContent = 'Downloading update…';
            if (sub) sub.textContent = 'Hang tight — almost there.';
            setBar(6);
            if (actions) actions.style.display = 'none';
            startTips();
            if (api.downloadUpdate) api.downloadUpdate();
          },
          dismiss: hideOverlay,
        });
      }
      showOverlay();
    },

    showUpgradeScreen: function (ver, body) {
      var title = $('upgTitle');
      var from = $('upgFrom');
      var notes = $('upgNotes');
      if (title) title.textContent = 'Major upgrade available' + (ver ? ' — v' + ver : '');
      var mine = installedVersion();
      if (from) from.textContent = mine ? ('You are on v' + mine + (ver ? ' — updating to v' + ver : '')) : '';
      if (notes) {
        notes.innerHTML = body ? renderMarkdown(body)
          : '<p class="sv-md-empty">No changelog was published for this release.</p>';
      }
      showUpgrade();
      refreshServerState();
    },

    onDownloadRequested: function () {
      var title = $('updateTitle');
      var sub = $('updateSub');
      var actions = $('updateActions');
      if (title) title.textContent = 'Downloading update…';
      if (sub) sub.textContent = 'Hang tight — almost there.';
      setBar(6);
      if (actions) actions.style.display = 'none';
      startTips();
      showOverlay();
      if (api.downloadUpdate) api.downloadUpdate();
    },

    onProgress: function (p) {
      showOverlay();
      var n = Math.max(0, Math.min(100, Math.round((p && p.percent) || 0)));
      setBar(n, p);
      var title = $('updateTitle');
      if (title) title.textContent = 'Downloading update…';
      var sub = $('updateSub');
      if (sub) sub.textContent = 'Hang tight — almost there.';
      var actions = $('updateActions');
      if (actions) actions.style.display = 'none';
      if (!tipTimer) startTips();
    },

    onDownloaded: function (info) {
      showOverlay();
      stopTips();
      var ver = (info && (info.version || info.tag)) ? String(info.version || info.tag) : '';
      setBar(100);
      var title = $('updateTitle');
      var sub = $('updateSub');
      var tipEl = $('updateTip');
      var actions = $('updateActions');
      if (title) title.textContent = 'Update ready' + (ver ? ' v' + ver : '');
      if (sub) sub.textContent = 'SciVerse Summit ' + (ver || '') + ' is ready — restart to apply it.';
      if (tipEl) tipEl.textContent = 'Tip: click Restart to Update when you are ready.';
      if (actions) {
        actions.innerHTML = '<button id="updateRestartBtn" class="primary">Restart to Update</button>'
          + '<button id="updateDismissBtn" class="ghost">Later</button>';
        actions.style.display = 'flex';
      }
    },

    onError: function (msg) {
      setBar(0);
      var title = $('updateTitle');
      var sub = $('updateSub');
      var tipEl = $('updateTip');
      var actions = $('updateActions');
      if (title) title.textContent = 'Update check failed';
      if (sub) sub.textContent = String(msg || 'Will retry next launch. Your current version keeps working.');
      stopTips();
      if (tipEl) tipEl.textContent = '';
      if (actions) actions.style.display = 'none';
      showOverlay();
      setTimeout(hideOverlay, 4200);
    },

    /** Re-evaluate the upgrade gate, e.g. after the server is stopped. */
    refresh: refreshServerState,
    hideAll: function () { hideOverlay(); hideUpgrade(); },
  };

  window.SummitUpdateUI = UI;
})();
