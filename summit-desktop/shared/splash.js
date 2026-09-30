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

  /* The entrance choreography runs to about 2620ms. MIN_MS is the hold on top
   * of that and is what decides how long the card is on screen.
   *
   * CAP_MS is the failsafe for a page that never receives a hand-off, not a
   * schedule, so it must stay comfortably above MIN_MS. Both apps override it
   * with capMs: 12000 anyway; this is only the fallback for a host that does
   * not pass one.
   */
  var MIN_MS = 5000;
  var CAP_MS = 6600;

  /* The progress ramp never reaches 100% while the splash is still up: 100%
   * means "leaving now", and it is applied at the exit. Holding at 99 leaves a
   * little visible motion right up to the hand-off. */
  var RAMP_START = 12;
  var RAMP_CEIL = 99;

  /* How long the card's entrance takes (see the choreography note above). The
   * progress ramp waits this out before it starts, so the bar is not already
   * two-thirds full at the moment it first becomes visible. */
  var ENTRY_MS = 2620;

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
    var handedOff = false;
    var holdMs = typeof opts.minMs === 'number' ? opts.minMs : MIN_MS;
    var stages = Array.isArray(opts.stages) ? opts.stages : [];
    var rampTimer = null;
    var rampStopped = false;
    /* The ramp does not start at mount. The card is still running its entrance
     * for the first ENTRY_MS, and because the bar is a child of that card the
     * progress was already running while nothing was visible - so the first
     * time you could see it, it read about 65%. It now starts once the card has
     * landed, and runs to just before the exit. */
    var rampStart = born + ENTRY_MS;
    var rampMs = Math.max(600, holdMs - ENTRY_MS - 500);

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
      var wait = Math.max(0, holdMs - (Date.now() - born));
      if (err && status) {
        status.textContent = String((err && err.message) || err || 'Something went wrong.');
        status.classList.add('is-error');
        render();
      }
      setTimeout(function () {
        // The ramp is stopped here, not above. finish() is called the moment
        // the host hands over - around 1.5s - while the hold runs to 5s, so
        // stopping it on entry froze the bar for the remaining 3.5s. It has to
        // keep climbing until the splash is genuinely leaving.
        stopRamp();
        // 100% is only true at the instant the splash leaves. Reaching it any
        // earlier is what made the bar read as broken: it hit 100% on hand-off
        // and then sat there for the rest of the hold.
        if (!err) setProgress(100, 'Ready');
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

    /* Progress ramp, owned here rather than in each splash page.
     *
     * The pages used to run their own setInterval with a fixed step, which was
     * three problems at once: the step was unrelated to how long the splash was
     * held, so the bar reached its ceiling early and then sat (chair 78% after
     * 2.0s, delegate 92% after 5.2s); the chair and delegate had different
     * ceilings and step sizes for no reason; and nothing stopped the interval
     * at hand-off, so it kept overwriting the status line after the bar had
     * already reached 100% - a full bar sitting next to a stale stage label.
     *
     * This is time-based against the hold instead: it eases from RAMP_START to
     * RAMP_CEIL and lands just before the splash leaves, so the bar is moving
     * for the whole time it is on screen. 100% is reserved for the exit.
     */
    function labelFor(p) {
      if (handedOff && p >= 96) return 'Ready';
      var label = '';
      for (var i = 0; i < stages.length; i++) {
        if (p >= stages[i][0]) label = stages[i][1];
      }
      return label;
    }

    function rampStep() {
      if (rampStopped) return;
      var t = Math.max(0, Math.min(1, (Date.now() - rampStart) / rampMs));
      // Smoothstep, not ease-out. Ease-out front-loads the movement - 75% of the
      // bar's travel happened in the first quarter of the ramp - so it shot
      // forward and then crawled. This one starts slow, speeds up, and settles.
      var eased = t * t * (3 - 2 * t);
      var target = RAMP_START + (RAMP_CEIL - RAMP_START) * eased;
      setProgress(target, labelFor(target));
      if (t >= 1) stopRamp();
    }

    /* A self-rescheduling timeout rather than setInterval. Same behaviour, and
     * it keeps the module dependent only on setTimeout/clearTimeout - which is
     * what the headless test harness models - and makes cancelling the ramp a
     * single clearTimeout with no interval id to leak.
     *
     * The id is cleared on entry, before stepping. It has to be: a fired
     * timeout is not reset to null, so leaving it set made the
     * `rampTimer === null` reschedule guard permanently false and the ramp ran
     * exactly once. The bar went 12% -> 14% and then sat there for the whole
     * hold, which is the "progress bar does not work" symptom.
     *
     * It also must not test `finished`. That flag is set the moment the host
     * hands over - around 1.5s - while the hold runs to 5s, so gating on it
     * killed the ramp three and a half seconds early. The ramp has its own
     * flag, set only when the splash is actually leaving. */
    function rampTick() {
      rampTimer = null;
      if (rampStopped) return;
      rampStep();
      if (rampTimer === null && !rampStopped) rampTimer = setTimeout(rampTick, 60);
    }

    function stopRamp() {
      rampStopped = true;
      if (rampTimer) { clearTimeout(rampTimer); rampTimer = null; }
    }

    setProgress(RAMP_START, labelFor(RAMP_START));
    rampTimer = setTimeout(rampTick, 60);

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
        // Only records that the app is up. It deliberately does not jump the
        // bar to 100%: the ramp is already heading for the end of the hold, so
        // forcing 100% here is what left a full bar stranded next to a stale
        // status line for the rest of the splash.
        handedOff = true;
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
