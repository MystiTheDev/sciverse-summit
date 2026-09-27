/* Chair console — behaviour. Loaded after the shared theme. */
(function () {
  'use strict';

  const api = window.summitAPI;
  const PORT = 8080;
  const BASE = 'http://127.0.0.1:' + PORT;
  const $ = (id) => document.getElementById(id);

  /* ── Splash (unchanged behaviour) ─────────────────────── */
  (function () {
    let skip = false;
    try { skip = new URLSearchParams(window.location.search).has('skipSplash'); } catch (e) { /* noop */ }
    if (skip) {
      const el = $('splash');
      if (el) el.remove();
      return;
    }
    const started = Date.now();
    function hide() {
      const wait = Math.max(0, 20000 - (Date.now() - started));
      setTimeout(() => {
        const el = $('splash');
        if (!el) return;
        el.classList.add('hidden');
        setTimeout(() => el.remove(), 500);
      }, wait);
    }
    if (document.readyState === 'complete') hide();
    else window.addEventListener('load', hide);
    setTimeout(hide, 25000);
  })();

  /* ── Update overlay (unchanged behaviour) ─────────────── */
  (function () {
    if (!api) return;
    const overlay = $('updateOverlay');
    const title = $('updateTitle');
    const sub = $('updateSub');
    const bar = $('updateBar');
    const pct = $('updatePct');
    const speed = $('updateSpeed');
    const tipEl = $('updateTip');
    const actions = $('updateActions');
    let tipTimer = null;
    let tipIdx = 0;

    const tips = () => (typeof UPDATE_TIPS !== 'undefined' && Array.isArray(UPDATE_TIPS) && UPDATE_TIPS.length)
      ? UPDATE_TIPS : ['Tip: Updates install on restart.'];

    function showTip() {
      const arr = tips();
      tipEl.style.opacity = '0';
      setTimeout(() => {
        tipEl.textContent = arr[tipIdx % arr.length];
        tipEl.style.opacity = '1';
        tipIdx++;
      }, 320);
    }
    function startTips() { tipIdx = Math.floor(Math.random() * tips().length); showTip(); tipTimer = setInterval(showTip, 3200); }
    function stopTips() { if (tipTimer) clearInterval(tipTimer); tipTimer = null; }
    function open() { overlay.classList.add('open'); overlay.setAttribute('aria-hidden', 'false'); }
    function fmtSpeed(bps) {
      if (!bps || bps <= 0) return '';
      const kb = bps / 1024;
      return kb > 1024 ? (kb / 1024).toFixed(1) + ' MB/s' : Math.round(kb) + ' KB/s';
    }

    if (api.onUpdateAvailable) api.onUpdateAvailable((info) => {
      open(); stopTips();
      const ver = (info && (info.version || info.tag)) ? ' v' + (info.version || info.tag) : '';
      title.textContent = 'Update available' + ver;
      sub.textContent = 'A new version is ready to download. Want to update now?';
      bar.style.width = '0%'; pct.textContent = '—'; speed.textContent = '';
      tipEl.textContent = 'Tip: You can keep working — download starts only if you choose to.';
      actions.innerHTML = '<button id="updDownloadBtn" class="primary">Download Update</button><button id="updLaterBtn" class="ghost">Later</button>';
      actions.style.display = 'flex';
      const dl = $('updDownloadBtn');
      const later = $('updLaterBtn');
      if (dl) dl.addEventListener('click', () => {
        title.textContent = 'Downloading update…';
        sub.textContent = 'Hang tight — almost there.';
        bar.style.width = '6%'; pct.textContent = '6%';
        actions.style.display = 'none';
        startTips();
        if (api.downloadUpdate) api.downloadUpdate();
      });
      if (later) later.addEventListener('click', () => { overlay.classList.remove('open'); stopTips(); });
    });

    if (api.onUpdateProgress) api.onUpdateProgress((p) => {
      open();
      const n = Math.max(0, Math.min(100, Math.round(p.percent || 0)));
      bar.style.width = n + '%'; pct.textContent = n + '%';
      speed.textContent = fmtSpeed(p.bytesPerSecond);
      title.textContent = 'Downloading update…';
      sub.textContent = 'Hang tight — almost there.';
      actions.style.display = 'none';
      if (!tipTimer) startTips();
    });

    if (api.onUpdateDownloaded) api.onUpdateDownloaded((info) => {
      open(); stopTips();
      const ver = (info && (info.version || info.tag)) ? ' v' + (info.version || info.tag) : '';
      bar.style.width = '100%'; pct.textContent = '100%'; speed.textContent = '';
      title.textContent = 'Update ready' + ver;
      sub.textContent = 'SciVerse Summit ' + ver.trim() + ' is ready — restart to apply it.';
      actions.innerHTML = '<button id="updateRestartBtn" class="primary">Restart to Update</button><button id="updateLaterBtn2" class="ghost">Later</button>';
      actions.style.display = 'flex';
      tipEl.textContent = 'Tip: Click Restart to Update when you are ready.';
      const rb = $('updateRestartBtn');
      const lb = $('updateLaterBtn2');
      if (rb) rb.addEventListener('click', () => { if (api.restartToUpdate) api.restartToUpdate(); });
      if (lb) lb.addEventListener('click', () => { overlay.classList.remove('open'); });
    });

    if (api.onUpdateError) api.onUpdateError((msg) => {
      bar.style.width = '0%'; pct.textContent = '—'; speed.textContent = '';
      title.textContent = 'Update check failed';
      sub.textContent = String(msg || 'Will retry next launch. Your current version keeps working.');
      stopTips(); tipEl.textContent = '';
      actions.style.display = 'none';
      setTimeout(() => overlay.classList.remove('open'), 4200);
    });
  })();

  /* ── Console log (bounded, severity-coloured) ─────────── */
  const logEl = $('log');
  const logEmpty = $('logEmpty');
  const logCount = $('logCount');
  const logJump = $('logJump');
  const MAX_LINES = 2000;
  let lineCount = 0;
  let autoScroll = true;

  const LVL = (text) => {
    if (/^\s*(ERROR|ERR\b|Exception|Caused by)/i.test(text)) return 'lv-error';
    if (/^\s*(WARN)/i.test(text)) return 'lv-warn';
    if (/(Server is up|is up\.|server live|Server started)/i.test(text)) return 'lv-good';
    if (/^\s*(app>|db >|spawning|waiting|stopped)/i.test(text)) return 'lv-app';
    return '';
  };

  function nearBottom() {
    return logEl.scrollHeight - logEl.scrollTop - logEl.clientHeight < 40;
  }

  function append(text, level) {
    if (lineCount === 0 && logEmpty && logEmpty.parentNode) logEmpty.remove();
    const row = document.createElement('div');
    row.className = 'log__line ' + (level || LVL(text));
    const t = new Date().toLocaleTimeString([], { hour12: false });
    const time = document.createElement('span');
    time.className = 'log__time';
    time.textContent = t;
    const msg = document.createElement('span');
    msg.className = 'log__msg';
    msg.textContent = text;
    row.appendChild(time);
    row.appendChild(msg);
    logEl.appendChild(row);
    lineCount++;

    while (lineCount > MAX_LINES && logEl.firstElementChild) {
      logEl.removeChild(logEl.firstElementChild);
      lineCount--;
    }
    if (logCount) logCount.textContent = lineCount + (lineCount === 1 ? ' line' : ' lines');
    if (autoScroll) logEl.scrollTop = logEl.scrollHeight;
  }

  logEl.addEventListener('scroll', () => {
    autoScroll = nearBottom();
    logJump.classList.toggle('show', !autoScroll);
  });
  logJump.addEventListener('click', () => {
    autoScroll = true;
    logEl.scrollTop = logEl.scrollHeight;
    logJump.classList.remove('show');
  });

  function clearLog() {
    logEl.innerHTML = '';
    lineCount = 0;
    autoScroll = true;
    logJump.classList.remove('show');
    const empty = document.createElement('div');
    empty.className = 'log__empty';
    empty.id = 'logEmpty';
    empty.textContent = 'Output cleared.';
    logEl.appendChild(empty);
    if (logCount) logCount.textContent = '0 lines';
  }

  $('logClearBtn').addEventListener('click', clearLog);
  $('logCopyBtn').addEventListener('click', async () => {
    const text = Array.from(logEl.querySelectorAll('.log__line'))
      .map((r) => r.textContent.replace(/\s+/g, ' ').trim()).join('\n');
    try {
      await navigator.clipboard.writeText(text || 'No output.');
      append('Copied output to clipboard.', 'lv-app');
    } catch (e) {
      append('Could not copy output: ' + e.message, 'lv-error');
    }
  });

  if (api && api.onLog) api.onLog((line) => append(line));

  /* ── State ─────────────────────────────────────────────── */
  const hero = $('hero');
  const heroTitle = $('heroTitle');
  const heroMeta = $('heroMeta');
  const heroIcon = $('heroIcon');
  const headerPill = $('headerPill');
  const headerPillText = $('headerPillText');
  const startBtn = $('startBtn');
  const openBtn = $('openBtn');
  const stopBtn = $('stopBtn');
  const copyBtn = $('copyBtn');
  const showBtn = $('showBtn');
  const joinBox = $('joinBox');
  const joinEmpty = $('joinEmpty');
  const joinUrl = $('joinUrl');

  let startedAt = null;
  let tick = null;

  function fmtUptime(ms) {
    const s = Math.floor(ms / 1000);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const pad = (n) => String(n).padStart(2, '0');
    return (h > 0 ? h + ':' : '') + pad(m) + ':' + pad(sec);
  }

  function setState(state, titleText, metaText) {
    hero.setAttribute('data-state', state);
    heroTitle.textContent = titleText;
    heroMeta.innerHTML = metaText || '';
    const map = {
      idle: ['Stopped', 'idle', 'bi-power'],
      starting: ['Starting…', 'starting', 'bi-arrow-repeat'],
      live: ['Server live', 'live', 'bi-check2-circle'],
      error: ['Failed', 'error', 'bi-exclamation-triangle'],
    };
    const conf = map[state] || map.idle;
    headerPill.className = 'sv-pill' + (state === 'idle' ? '' : ' is-' + conf[1]);
    headerPillText.textContent = conf[0];
    heroIcon.className = 'bi ' + conf[2];

    // Stop is only meaningful while a process exists. Showing it on a stopped
    // or failed server would just be a button that does nothing. While a
    // process exists, Start is swapped out rather than left disabled, so the
    // panel never shows two competing actions.
    const canStop = state === 'starting' || state === 'live';
    stopBtn.style.display = canStop ? '' : 'none';
    startBtn.style.display = canStop ? 'none' : '';
  }

  function startTicker() {
    stopTicker();
    tick = setInterval(() => {
      if (startedAt) {
        const el = $('heroMeta');
        const up = document.createElement('span');
        up.className = 'sv-mono';
        up.textContent = 'Up ' + fmtUptime(Date.now() - startedAt) + '  ·  port 8080';
        heroMeta.innerHTML = '';
        heroMeta.appendChild(up);
      }
    }, 1000);
  }
  function stopTicker() { if (tick) clearInterval(tick); tick = null; }

  /* ── Settings ─────────────────────────────────────────── */
  const modal = $('settingsModal');
  const DEFAULTS = {
    autoStart: false,
    launchAtLogin: false,
    tray: true,
    rememberBounds: true,
    logSize: 'medium',
    logWrap: true,
    clearOnStart: true,
    desktopNotifications: true,
  };
  let settings = Object.assign({}, DEFAULTS);
  let updStatus = $('updStatus');

  async function loadSettings() {
    try { if (api && api.getSettings) settings = Object.assign({}, DEFAULTS, await api.getSettings()); } catch (e) { /* defaults */ }
    applySettings();
  }

  function applySettings() {
    $('setAutoStart').checked = !!settings.autoStart;
    $('setLaunchAtLogin').checked = !!settings.launchAtLogin;
    $('setTray').checked = !!settings.tray;
    $('setRememberBounds').checked = !!settings.rememberBounds;
    $('setLogSize').value = settings.logSize;
    $('setLogWrap').checked = !!settings.logWrap;
    $('setClearOnStart').checked = !!settings.clearOnStart;
    $('setDesktopNotif').checked = !!settings.desktopNotifications;

    logEl.classList.remove('small', 'large');
    if (settings.logSize === 'small') logEl.classList.add('small');
    if (settings.logSize === 'large') logEl.classList.add('large');
    logEl.classList.toggle('no-wrap', !settings.logWrap);
  }

  async function saveSettings() {
    settings.autoStart = $('setAutoStart').checked;
    settings.launchAtLogin = $('setLaunchAtLogin').checked;
    settings.tray = $('setTray').checked;
    settings.rememberBounds = $('setRememberBounds').checked;
    settings.logSize = $('setLogSize').value;
    settings.logWrap = $('setLogWrap').checked;
    settings.clearOnStart = $('setClearOnStart').checked;
    settings.desktopNotifications = $('setDesktopNotif').checked;
    applySettings();
    if (api && api.setSettings) { try { await api.setSettings(settings); } catch (e) { /* non-fatal */ } }
  }

  ['setAutoStart', 'setLaunchAtLogin', 'setTray', 'setRememberBounds', 'setLogSize', 'setLogWrap', 'setClearOnStart', 'setDesktopNotif']
    .forEach((id) => { const el = $(id); if (el) el.addEventListener('change', saveSettings); });

  function openModal() { modal.classList.add('open'); }
  function closeModal() { modal.classList.remove('open'); }
  $('settingsBtn').addEventListener('click', openModal);
  Array.from(document.querySelectorAll('[data-close-modal]')).forEach((b) => b.addEventListener('click', closeModal));
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.classList.contains('open')) closeModal(); });

  /* Update check (manual) */
  $('checkUpdateBtn').addEventListener('click', async () => {
    if (!api || !api.checkForUpdates) { updStatus.textContent = 'Update checks are only available in the installed app.'; return; }
    updStatus.className = 'upd-status busy';
    updStatus.textContent = 'Checking…';
    try {
      const res = await api.checkForUpdates();
      if (res === 'dev-mode') {
        updStatus.className = 'upd-status';
        updStatus.textContent = 'Update checks run in the installed app only (this is a dev build).';
      }
    } catch (e) {
      updStatus.className = 'upd-status err';
      updStatus.textContent = 'Could not check: ' + (e && e.message ? e.message : e);
    }
  });
  if (api && api.onManualUpdateResult) {
    api.onManualUpdateResult((r) => {
      if (!r) return;
      if (r.state === 'checking') { updStatus.className = 'upd-status busy'; updStatus.textContent = 'Checking…'; }
      else if (r.state === 'available') { updStatus.className = 'upd-status'; updStatus.textContent = 'Update v' + r.version + ' available — it will ask before downloading.'; }
      else if (r.state === 'not-available') { updStatus.className = 'upd-status ok'; updStatus.textContent = 'You are on the latest version (v' + (r.current || '—') + ').'; }
      else if (r.state === 'error') { updStatus.className = 'upd-status err'; updStatus.textContent = 'Check failed: ' + (r.message || 'unknown error'); }
    });
  }

  /* Data folder */
  if (api && api.getSystemInfo) {
    api.getSystemInfo().then((info) => {
      if (!info) return;
      $('appVersion').textContent = 'v' + (info.appVersion || '');
      $('infoVersion').textContent = 'v' + (info.appVersion || '') + (info.electron ? ' · Electron ' + info.electron : '');
      $('infoJava').textContent = info.java || '—';
      $('infoData').textContent = info.dataDir || '—';
      $('dataPathLabel').textContent = info.dataDir || '—';
      $('infoPort').textContent = String(info.port || 8080);
    }).catch(() => {});
  }
  $('openDataBtn').addEventListener('click', async () => {
    if (api && api.openDataFolder) { const ok = await api.openDataFolder(); if (!ok) append('Could not open the data folder.', 'lv-error'); }
  });
  $('copyDataBtn').addEventListener('click', async () => {
    try {
      const info = await api.getSystemInfo();
      await navigator.clipboard.writeText(info.dataDir || '');
      append('Copied data folder path.', 'lv-app');
    } catch (e) { append('Could not copy path: ' + e.message, 'lv-error'); }
  });
  $('resetDataBtn').addEventListener('click', async () => {
    if (!confirm('Erase ALL session data?\n\nEvery session, delegate, note and setting on this PC will be deleted. This cannot be undone.')) return;
    try {
      const res = await api.resetData();
      append(res && res.ok ? 'Session data erased. Restart the server to start fresh.' : 'Nothing to erase.', res && res.ok ? 'lv-good' : 'lv-app');
    } catch (e) { append('Could not erase data: ' + e.message, 'lv-error'); }
  });

  /* ── Server control ────────────────────────────────────── */
  // Bounded probe: without a hard timeout an unresponsive port can hang the
  // check for many seconds, so the poll loop would stall and the Start button
  // would stay disabled with no feedback. The main-process TCP probe gives us
  // a predictable deadline; the fetch is a fallback for running outside Electron.
  async function serverUp() {
    if (api && api.probeServer) {
      try {
        const res = await api.probeServer('127.0.0.1', PORT, '/login', 2000);
        return !!(res && res.up);
      } catch (e) {
        return false;
      }
    }
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 3000);
    try {
      await fetch(BASE + '/login', { mode: 'no-cors', cache: 'no-store', signal: ctrl.signal });
      return true;
    } catch {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  async function waitForServer() {
    for (let i = 0; i < 90; i++) {
      if (await serverUp()) return true;
      await new Promise((r) => setTimeout(r, 1000));
    }
    return false;
  }

  function showJoin(url) {
    if (url) {
      joinUrl.textContent = url;
      joinBox.style.display = '';
      joinEmpty.style.display = 'none';
    }
  }

  async function refreshJoin() {
    try {
      if (!api || !api.lanAddress) return;
      const ip = await api.lanAddress();
      showJoin('http://' + ip + ':8080');
    } catch (e) { /* leave the empty state */ }
  }

  startBtn.addEventListener('click', async () => {
    if (settings.clearOnStart) clearLog();
    startBtn.disabled = true;
    setState('starting', 'Starting server…', 'Spring Boot takes 10&ndash;30 seconds on a cold start');
    append('Starting the bundled server (java -jar summit.jar) …', 'lv-app');
    try {
      await api.startServer();
      append('Waiting for http://127.0.0.1:8080 …', 'lv-app');
      if (await waitForServer()) {
        startedAt = Date.now();
        setState('live', 'Server live', '');
        startTicker();
        openBtn.disabled = false;
        await refreshJoin();
        append('Server is live. Open Chair View when you are ready — this console stays put.', 'lv-good');
      } else {
        setState('error', 'Server did not respond', 'Check the output below, or port 8080 may be in use');
        startBtn.disabled = false;
        append('Timed out waiting for the server to answer.', 'lv-error');
      }
    } catch (e) {
      setState('error', 'Failed to start', '');
      append('ERROR: ' + e.message, 'lv-error');
      startBtn.disabled = false;
    }
  });

  openBtn.addEventListener('click', () => { window.location.href = BASE + '/'; });

  stopBtn.addEventListener('click', async () => {
    if (!confirm('Stop the session server?\n\nEvery delegate will be disconnected and anything not yet saved is lost.')) return;
    stopBtn.disabled = true;
    stopTicker();
    startedAt = null;
    setState('starting', 'Stopping server…', 'Waiting for the JVM to exit');
    append('Stopping the session server (requested from the console)…', 'lv-app');
    try {
      await api.stopServer();
      // server:exited normally resets the hero; if that event somehow never
      // arrives, do not leave the console stuck on "Stopping".
      setTimeout(() => {
        if (hero.getAttribute('data-state') === 'starting') {
          setState('idle', 'Server stopped', 'Start it again when you are ready');
          startBtn.disabled = false;
          openBtn.disabled = true;
        }
      }, 4000);
    } catch (e) {
      setState('error', 'Could not stop the server', '');
      append('ERROR: ' + e.message, 'lv-error');
    } finally {
      stopBtn.disabled = false;
    }
  });

  copyBtn.addEventListener('click', async () => {
    const url = joinUrl.textContent;
    if (!url || url === '—') return;
    try {
      await navigator.clipboard.writeText(url);
      const prev = copyBtn.innerHTML;
      copyBtn.innerHTML = '<i class="bi bi-check2"></i><span>Copied</span>';
      setTimeout(() => { copyBtn.innerHTML = prev; }, 1600);
      append('Copied join address: ' + url, 'lv-app');
    } catch (e) { append('Could not copy: ' + e.message, 'lv-error'); }
  });

  showBtn.addEventListener('click', () => {
    const url = joinUrl.textContent;
    if (url && url !== '—') window.open(url, '_blank');
  });

  /* ── Boot ─────────────────────────────────────────────── */
  (async function init() {
    await loadSettings();
    if (api && api.onServerExit) {
      api.onServerExit(() => {
        startedAt = null;
        stopTicker();
        setState('idle', 'Server stopped', 'The server process exited');
        startBtn.disabled = false;
        openBtn.disabled = true;
      });
    }
    try {
      if (api && api.serverStatus && await api.serverStatus() === 'running') {
        startedAt = Date.now();
        setState('live', 'Server live', '');
        startTicker();
        startBtn.disabled = true;
        openBtn.disabled = false;
        append('Reconnected to the running server. Open Chair View, or stop it here.', 'lv-app');
        await refreshJoin();
      }
    } catch (e) { /* launcher works standalone */ }
    if (settings.autoStart) {
      append('Auto-start is on — starting the server.', 'lv-app');
      startBtn.click();
    }
  })();
})();
