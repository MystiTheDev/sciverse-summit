/* SciVerse Summit — loading screen controller (Chair + Delegate).
 *
 * One shared overlay covering the wait before an app is usable. The markup is
 * injected here rather than duplicated in each index.html, for the same reason
 * the splash panel is: one place to change it, and no chance of the two apps
 * drifting apart.
 *
 *   SummitLoading.show()                 mount the overlay
 *   SummitLoading.set('Contacting ...')  update the status line
 *   SummitLoading.hide()                 fade out and remove
 *
 * Deliberately has a minimum visible time. Without it a fast load would flash
 * the overlay for a couple of frames, which reads as a glitch rather than as
 * a loading state.
 */
(function (global) {
  'use strict';

  /* How long the overlay stays up once the window is actually visible. It was
   * 700ms, which was long enough to avoid a flash but short enough to read as
   * a flicker rather than a loading state. Timed from the reveal, not from
   * script parse - see listenForWindowShown. */
  var MIN_VISIBLE_MS = 5000;
  var READY_HOLD_MS = 1100;   // "Ready" on screen this long before it fades
  var EXIT_MS = 420;          // must match the opacity transition in loading.css
  var MAX_MS = 60000;         // failsafe: never leave the user stuck
  var SHOWN_WAIT_MS = 4000;   // if the host never reveals the window, do not hang

  var el = null;
  var label = null;
  var windowShownAt = null;   // when the host window actually became visible
  var hideTimer = null;
  var readyTimer = null;
  var maxTimer = null;

  function build(text) {
    var root = document.createElement('div');
    root.className = 'sv-load';
    root.setAttribute('role', 'status');
    root.setAttribute('aria-live', 'polite');

    var bars = document.createElement('div');
    bars.className = 'sv-load__bars';
    bars.setAttribute('aria-hidden', 'true');
    for (var i = 0; i < 5; i++) {
      var bar = document.createElement('span');
      bar.className = 'sv-load__bar';
      bars.appendChild(bar);
    }
    root.appendChild(bars);

    var p = document.createElement('p');
    p.className = 'sv-load__label';
    // Reads LOADING for the whole wait. It used to be given per-app copy that
    // already said things like "Starting the console", and the apps then set
    // it to "Ready" the instant boot finished - which sat on screen for the
    // whole 5s hold, so the loading screen spent its entire life claiming to
    // be done.
    p.textContent = text || 'Loading';
    root.appendChild(p);

    return { root: root, label: p };
  }

  function show(text) {
    // Already up: just update the text rather than stacking a second overlay.
    if (el) { set(text); return api; }

    var built = build(text);
    el = built.root;
    label = built.label;
    document.body.appendChild(el);
    windowShownAt = null;

    listenForWindowShown();
    clearTimeout(maxTimer);
    maxTimer = setTimeout(function () { hide(true); }, MAX_MS);
    return api;
  }

  /* The main window is created hidden and only revealed once the splash window
   * has handed over - about three seconds later. The overlay is therefore up
   * and finished long before anyone can see it, which is why the loading
   * screen appeared to be missing entirely. Its minimum-visible clock has to
   * start when the window is revealed, not when this script is parsed.
   */
  function listenForWindowShown() {
    var api = global.summitAPI;
    if (!api || typeof api.onWindowShown !== 'function') {
      // No bridge: a preload that failed, or the page opened outside the app.
      // Treat the window as already visible, so the overlay still holds for the
      // normal minimum and then goes. Returning quietly here instead would
      // leave windowShownAt null and make every launch wait out the full
      // SHOWN_WAIT_MS - a four-second stall on every start, in the one
      // situation where something has already gone wrong.
      windowShownAt = Date.now();
      return;
    }
    api.onWindowShown(function () { markWindowShown(); });
  }

  function markWindowShown() {
    windowShownAt = Date.now();
    // A hide() already waiting for the reveal can go now.
    if (el && hideTimer) beginExit(Date.now());
  }

  function set(text, isError) {
    if (!el) return show(text);
    if (text) label.textContent = text;
    if (isError) label.classList.add('is-error');
    return api;
  }

  /* Starts the exit, holding the overlay for the remainder of the minimum
   * measured from the reveal. `base` is when the window became visible, or
   * when the caller asked if the host never revealed it.
   */
  function beginExit(base) {
    var wait = Math.max(0, MIN_VISIBLE_MS - (Date.now() - base));
    hideTimer = setTimeout(fadeOut, wait);
  }

  function fadeOut() {
    if (!el) return;
    // "Ready" belongs to the very end, not the whole wait. It used to be set
    // the moment boot finished, so a 5s loading screen read READY for 5s.
    if (label && !label.classList.contains('is-error')) label.textContent = 'Ready';
    // ...and it is held for a beat before the screen goes. Setting Ready and
    // fading on the same frame meant it was legible for a split second, which
    // reads as a glitch rather than as an arrival.
    readyTimer = setTimeout(function () {
      if (!el) return;
      el.classList.add('is-hiding');
      setTimeout(function () {
        if (el && el.parentNode) el.parentNode.removeChild(el);
        el = null;
        label = null;
      }, EXIT_MS);
    }, READY_HOLD_MS);
  }

  function hide(force) {
    if (!el) return api;
    clearTimeout(hideTimer);
    clearTimeout(readyTimer);
    clearTimeout(maxTimer);

    var now = Date.now();
    if (force) { hideTimer = setTimeout(fadeOut, 0); return api; }

    if (windowShownAt !== null) {
      beginExit(windowShownAt);
    } else {
      // Not revealed yet. Hold the overlay until it is, so the user actually
      // sees a loading state rather than a finished app. SHOWN_WAIT_MS is the
      // escape hatch for a host that never signals (a plain browser, a
      // headless test), which would otherwise sit here forever.
      hideTimer = setTimeout(function () {
        if (windowShownAt === null) windowShownAt = Date.now();
        beginExit(windowShownAt);
      }, SHOWN_WAIT_MS);
    }
    return api;
  }

  var api = {
    show: show,
    set: set,
    hide: hide,
    markWindowShown: markWindowShown,
    MIN_VISIBLE_MS: MIN_VISIBLE_MS,
    READY_HOLD_MS: READY_HOLD_MS
  };
  global.SummitLoading = api;
})(window);
