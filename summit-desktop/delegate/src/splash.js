/* SciVerse Summit — splash screen state (Chair + Delegate).
 *
 * The panel markup lives here as one string and is injected wherever the
 * splash is needed, so there is a single source instead of a copy in each
 * app. Copyright stays with the product owner.
 *
 * Exposes Splash.mount(el, opts) -> api { set(pct, text), ready(err) }.
 * A no-op outside a DOM, so builds and tests can require it freely.
 */
(function () {
  'use strict';

  var NAME_LETTERS = 'SciVerse Summit';
  var STAGGER_MS = 52;

  /* Timings follow the entrance choreography: panel 500ms, logo to 850ms,
   * brand letters finish ~1070ms, description ~1280ms. */
  var MIN_MS = 3000;
  var CAP_MS = 4000;

  // Handle the host uses to hand over. See mount() for why this is a function
  // call rather than an ipcRenderer channel.
  var host = (typeof window !== 'undefined' && window)
    ? (window.SummitSplashHost = window.SummitSplashHost || {})
    : null;

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function currentYear() {
    try {
      var d = new Date();
      if (d && typeof d.getFullYear === 'function') return d.getFullYear();
    } catch (e) { /* fall through */ }
    return 2026;
  }

  /**
   * Builds the panel. `opts` chooses the per-app copy; everything else is
   * identical so the two apps launch the same screen.
   *
   * @param {object} opts
   * @param {string} opts.role       e.g. 'Chair' / 'Delegate'
   * @param {string} opts.tagline    two-line product tagline (HTML)
   * @param {string} opts.description  short paragraph (HTML)
   * @param {string} opts.version    e.g. '3.1.0'
   * @param {string} opts.logo       src for the logo image
   * @param {string} opts.year       copyright year
   * @param {string} [opts.copyright] true to append "All rights reserved."
   */
  function build(opts) {
    opts = opts || {};
    var letters = String(NAME_LETTERS).split('').map(function (ch, i) {
      return '<span style="animation-delay:' + (420 + i * STAGGER_MS) + 'ms">' + escapeHtml(ch) + '</span>';
    }).join('');

    var role = escapeHtml(opts.role || '');
    var version = escapeHtml(opts.version || '');
    var logo = escapeHtml(opts.logo || 'logo.png');
    var year = escapeHtml(opts.year || currentYear());

    return '' +
      '<div class="sv-splash" id="svSplash" role="status" aria-label="SciVerse Summit is loading">' +
      '<div class="sv-splash__blob sv-splash__blob--a" aria-hidden="true"></div>' +
      '<div class="sv-splash__blob sv-splash__blob--b" aria-hidden="true"></div>' +
      '<div class="sv-splash__stage">' +
      '<div class="sv-splash__panel">' +
      '<div class="sv-splash__hero" aria-hidden="true">' +
      '<div class="sv-splash__glow"></div>' +
      '<img class="sv-splash__logo" src="' + logo + '" alt="">' +
      '</div>' +
      '<div class="sv-splash__content">' +
      '<h1 class="sv-splash__brand">' + letters + '</h1>' +
      '<p class="sv-splash__version">v' + version + ' &middot; ' + role + '</p>' +
      '<p class="sv-splash__tagline">' + (opts.tagline || '') + '</p>' +
      '<p class="sv-splash__desc">' + (opts.description || '') + '</p>' +
      '<div class="sv-splash__pct" id="svPct">0%</div>' +
      '<div class="sv-splash__track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0" id="svTrack">' +
      '<div class="sv-splash__fill" id="svFill"></div>' +
      '</div>' +
      '<p class="sv-splash__status" id="svStatus"></p>' +
      '<p class="sv-splash__copy">&copy; ' + year + ' SciVerse</p>' +
      '</div>' +
      // The triangles sit outside the panel so they can break its edge. Inside
      // it they were painted over the card and read as part of the artwork
      // rather than as shapes crossing the boundary.
      '<div class="sv-splash__tri sv-splash__tri--tl" aria-hidden="true"></div>' +
      '<div class="sv-splash__tri sv-splash__tri--ml" aria-hidden="true"></div>' +
      '<div class="sv-splash__tri sv-splash__tri--bl" aria-hidden="true"></div>' +
      '<div class="sv-splash__tri sv-splash__tri--r" aria-hidden="true"></div>' +
      '</div>' +
      '</div>' +
      '</div>';
  }

  /**
   * Mounts the splash into `container` and returns the driver.
   * The splash is not dismissible: it holds for the minimum, exits on
   * ready(), and force-exits at the cap with the error state applied.
   */
  function mount(container, opts) {
    opts = opts || {};
    if (!container || typeof container.innerHTML === 'undefined') return null;

    container.innerHTML = build(opts);

    var root = container.firstElementChild || container.querySelector('.sv-splash');
    var fill = container.querySelector('#svFill');
    var pct = container.querySelector('#svPct');
    var track = container.querySelector('#svTrack');
    var status = container.querySelector('#svStatus');

    var born = Date.now();
    var finished = false;
    var shown = 0;

    var capTimer = setTimeout(function () {
      finish(new Error('Splash timed out waiting for the app to be ready'));
    }, typeof opts.capMs === 'number' ? opts.capMs : CAP_MS);

    function render() {
      var p = Math.max(0, Math.min(100, Math.round(shown)));
      if (fill) fill.style.width = p + '%';
      if (pct) pct.textContent = p + '%';
      if (track) track.setAttribute('aria-valuenow', String(p));
    }

    function finish(err) {
      if (finished) return;
      finished = true;
      if (capTimer) clearTimeout(capTimer);
      var wait = Math.max(0, (typeof opts.minMs === 'number' ? opts.minMs : MIN_MS) - (Date.now() - born));
      if (err && status) {
        status.textContent = String((err && err.message) || err || 'Something went wrong.');
        status.classList.add('is-error');
        render();
      }
      setTimeout(function () {
        if (root) root.classList.add('is-leaving');
        setTimeout(function () {
          if (root && root.parentNode) root.parentNode.removeChild(root);
          if (typeof opts.onDone === 'function') opts.onDone(err || null);
          // A standalone host window uses this to hand over, so the splash
          // owns its minimum hold and exit rather than being cut short.
          if (typeof opts.onReady === 'function') opts.onReady(err || null);
        }, 460);
      }, wait);
    }

    /* Progress setter. Named rather than inlined in the returned object so the
     * host hand-off below can use it too - an inline `set:` property is not a
     * binding in scope, and calling set(...) from here used to throw
     * "set is not defined". */
    function setProgress(n, text) {
      var p = Math.max(0, Math.min(100, Number(n) || 0));
      if (p > shown) shown = p;
      if (typeof text === 'string' && status) status.textContent = text;
      render();
    }

    render();

    /* Host hand-off. The main process calls this once the real window has
     * loaded; the splash then finishes its own exit and closes itself, so the
     * minimum hold and the animation both complete instead of being cut off.
     *
     * This is a plain function the host invokes with executeJavaScript, not an
     * ipcRenderer channel. The splash window runs with contextIsolation on and
     * no preload, so webContents.send() has nowhere to land: it delivers to
     * ipcRenderer.on(), not to a window 'message' listener, and it carries no
     * payload for a listener to inspect. A listener written against
     * addEventListener('message') therefore never fires, and the splash sits
     * at its last progress value until the cap forces it out.
     */
    if (host) {
      host.done = function () {
        setProgress(100, 'Ready');
        finish(null);
      };
    }

    return {
      id: 'svSplash',
      // The bar only ever moves forward; a regression would read as a bug.
      set: setProgress,
      ready: function () { finish(null); },
      error: function (err) { finish(err || new Error('Splash failed')); },
      minMs: MIN_MS,
      capMs: CAP_MS,
    };
  }

  var api = { build: build, mount: mount, MIN_MS: MIN_MS, CAP_MS: CAP_MS };
  if (typeof window !== 'undefined') window.Splash = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();
