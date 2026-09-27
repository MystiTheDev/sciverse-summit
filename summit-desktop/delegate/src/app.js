/* Delegate app — behaviour. Loaded after the shared theme. */
(function () {
  'use strict';

  const api = window.summitAPI;
  const PORT = 8080;
  const $ = (id) => document.getElementById(id);

  /* ── Splash (unchanged behaviour) ─────────────────────── */
  (function () {
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

  /* ── Settings ─────────────────────────────────────────── */
  const modal = $('settingsModal');
  const DEFAULTS = {
    remember: true,
    autoConnect: false,
    launchAtLogin: false,
    glint: true,
  };
  let settings = Object.assign({}, DEFAULTS);
  const updStatus = $('updStatus');

  async function loadSettings() {
    try { if (api && api.getSettings) settings = Object.assign({}, DEFAULTS, await api.getSettings()); } catch (e) { /* defaults */ }
    applySettings();
  }

  function applySettings() {
    $('setRemember').checked = !!settings.remember;
    $('setAutoConnect').checked = !!settings.autoConnect;
    $('setLaunchAtLogin').checked = !!settings.launchAtLogin;
    $('setGlint').checked = !!settings.glint;
    document.body.classList.toggle('sv-no-glint', !settings.glint);
  }

  async function saveSettings() {
    settings.remember = $('setRemember').checked;
    settings.autoConnect = $('setAutoConnect').checked;
    settings.launchAtLogin = $('setLaunchAtLogin').checked;
    settings.glint = $('setGlint').checked;
    applySettings();
    if (api && api.setSettings) { try { await api.setSettings(settings); } catch (e) { /* non-fatal */ } }
    renderRecent();
  }

  ['setRemember', 'setAutoConnect', 'setLaunchAtLogin', 'setGlint']
    .forEach((id) => { const el = $(id); if (el) el.addEventListener('change', saveSettings); });

  function openModal() { modal.classList.add('open'); }
  function closeModal() { modal.classList.remove('open'); }
  $('settingsBtn').addEventListener('click', openModal);
  Array.from(document.querySelectorAll('[data-close-modal]')).forEach((b) => b.addEventListener('click', closeModal));
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.classList.contains('open')) closeModal(); });

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

  /* ── Recent chairs ────────────────────────────────────── */
  const recentList = $('recentList');
  const recentPanel = $('recentPanel');

  function ago(ts) {
    const s = Math.floor((Date.now() - ts) / 1000);
    if (s < 60) return 'just now';
    if (s < 3600) return Math.floor(s / 60) + 'm ago';
    if (s < 86400) return Math.floor(s / 3600) + 'h ago';
    return Math.floor(s / 86400) + 'd ago';
  }

  async function renderRecent() {
    if (!settings.remember) { recentPanel.style.display = 'none'; return; }
    let list = [];
    if (api && api.getRecent) {
      // getRecent goes through ipcRenderer.invoke, so it is a Promise — it
      // must be awaited, otherwise `list.length` is undefined and the panel
      // silently never renders.
      try { list = (await api.getRecent()) || []; } catch (e) { list = []; }
    }
    if (!Array.isArray(list)) list = [];
    recentList.innerHTML = '';
    if (!list.length) {
      recentPanel.style.display = 'none';
      return;
    }
    recentPanel.style.display = '';
    list.forEach((entry) => {
      const host = typeof entry === 'string' ? entry : entry.host;
      const when = typeof entry === 'string' ? 0 : (entry.at || 0);
      const row = document.createElement('div');
      row.className = 'recent__row';
      const icon = document.createElement('i');
      icon.className = 'bi bi-router recent__icon';
      const hostEl = document.createElement('span');
      hostEl.className = 'recent__host';
      hostEl.textContent = host;
      const whenEl = document.createElement('span');
      whenEl.className = 'recent__when';
      whenEl.textContent = when ? ago(when) : '';
      const use = document.createElement('button');
      use.className = 'sv-btn sv-btn-sm recent__use';
      use.textContent = 'Use';
      use.addEventListener('click', () => { $('host').value = host; $('host').focus(); });
      row.appendChild(icon);
      row.appendChild(hostEl);
      row.appendChild(whenEl);
      row.appendChild(use);
      recentList.appendChild(row);
    });
  }

  $('clearRecentBtn').addEventListener('click', async () => {
    if (api && api.clearRecent) { try { await api.clearRecent(); } catch (e) { /* noop */ } }
    renderRecent();
  });

  /* ── Connect flow ─────────────────────────────────────── */
  const hostInput = $('host');
  const connectBtn = $('connectBtn');
  const formView = $('formView');
  const stateView = $('stateView');
  const connectError = $('connectError');
  const stateTitle = $('stateTitle');
  const stateDetail = $('stateDetail');
  const stateTarget = $('stateTarget');
  const stateIcon = $('stateIcon');
  const cancelBtn = $('cancelBtn');

  let polling = false;

  function normalise(raw) {
    return String(raw || '').trim().replace(/^https?:\/\//i, '').replace(/\/.*$/, '').split(':')[0];
  }

  function isPlausibleHost(h) {
    if (!/^[a-z0-9._-]{1,253}$/i.test(h)) return false;
    // A host made only of digits and dots is meant to be an IPv4 address, so
    // hold it to that: "10.0.0.9910.0.0.99" passes the charset test above but
    // is never a real host, and accepting it produces a confusing error later.
    if (/^[\d.]+$/.test(h)) {
      const parts = h.split('.');
      return parts.length === 4 && parts.every((p) => /^\d{1,3}$/.test(p) && Number(p) <= 255);
    }
    return true;
  }

  function showError(msg) {
    connectError.innerHTML = '<i class="bi bi-exclamation-triangle-fill"></i><span>' + msg + '</span>';
    connectError.classList.add('show');
  }

  function clearError() { connectError.classList.remove('show'); connectError.innerHTML = ''; }

  function setState(state, title, detail) {
    stateView.setAttribute('data-state', state);
    stateTitle.textContent = title;
    stateDetail.innerHTML = detail;
    stateIcon.className = 'bi ' + (state === 'error' ? 'bi-wifi-off'
      : state === 'live' ? 'bi-check2-circle' : 'bi-hourglass-split');
  }

  function showState() {
    formView.style.display = 'none';
    stateView.classList.add('show');
  }
  function showForm() {
    polling = false;
    stateView.classList.remove('show');
    formView.style.display = '';
  }

  // Resolves true when something is listening on the port.
  //
  // Prefers the main-process TCP probe: it has a hard timeout we control and
  // avoids CORS entirely. The fetch fallback keeps this page usable if it is
  // ever opened outside the app (no summitAPI bridge).
  async function serverUp(base, host, port) {
    if (api && api.probeServer) {
      try {
        const res = await api.probeServer(host, port, '/login', 2500);
        return !!(res && res.up);
      } catch (e) {
        return false;
      }
    }
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 3000);
    try {
      await fetch(base + '/login', { mode: 'no-cors', cache: 'no-store', signal: ctrl.signal });
      return true;
    } catch {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  async function beginConnect(rawHost) {
    // Guard re-entrancy: a double-click on Connect would otherwise start a
    // second poll loop, and the two would fight over the displayed state.
    if (polling) return;

    const host = normalise(rawHost);
    if (!host) { hostInput.focus(); showError('Enter the address your chair gave you.'); return; }
    if (!isPlausibleHost(host)) { hostInput.focus(); showError('That does not look like an address. Example: 192.168.1.42'); return; }
    clearError();

    const base = 'http://' + host + ':' + PORT;
    if (api && api.addRecent) { try { await api.addRecent(host); } catch (e) { /* noop */ } }
    renderRecent();

    polling = true;
    stateTarget.textContent = base;
    showState();
    setState('waiting', 'Waiting for the chair…', 'Looking for <span class="connect__target">' + base + '</span>');

    let misses = 0;
    while (polling) {
      if (await serverUp(base, host, PORT)) {
        setState('live', 'Found the session', 'Joining <span class="connect__target">' + base + '</span>…');
        await new Promise((r) => setTimeout(r, 350));
        window.location.href = base + '/delegate';
        return;
      }
      misses += 1;
      if (misses === 2) {
        setState('error', "Can't reach that address",
          'Nothing is answering on <span class="connect__target">' + base + '</span>. ' +
          'Check the address with your chair, and that you are on the same network.');
      } else if (misses > 2 && misses % 2 === 0) {
        setState('waiting', 'Still trying…', 'Retrying <span class="connect__target">' + base + '</span> — the chair may not have started yet.');
      }
      await new Promise((r) => setTimeout(r, 1500));
    }
  }

  connectBtn.addEventListener('click', () => beginConnect(hostInput.value));
  hostInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') beginConnect(hostInput.value); });
  hostInput.addEventListener('input', clearError);
  cancelBtn.addEventListener('click', () => { showForm(); hostInput.focus(); });

  /* ── Boot ─────────────────────────────────────────────── */
  (async function init() {
    await loadSettings();
    if (api && api.getVersion) {
      try { $('appVersion').textContent = 'v' + (await api.getVersion()); } catch (e) { /* noop */ }
    }
    let last = '';
    if (api && api.getLastHost) {
      try { last = (await api.getLastHost()) || ''; } catch (e) { /* noop */ }
    }
    if (last) hostInput.value = last;
    renderRecent();
    if (settings.autoConnect && last) beginConnect(last);
  })();
})();
