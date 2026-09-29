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

  var MIN_VISIBLE_MS = 700;   // below this it is a flash, not a loading state
  var EXIT_MS = 420;          // must match the opacity transition in loading.css
  var MAX_MS = 60000;         // failsafe: never leave the user stuck

  var el = null;
  var label = null;
  var shownAt = 0;
  var hideTimer = null;
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
    shownAt = Date.now();

    clearTimeout(maxTimer);
    maxTimer = setTimeout(function () { hide(true); }, MAX_MS);
    return api;
  }

  function set(text, isError) {
    if (!el) return show(text);
    if (text) label.textContent = text;
    if (isError) label.classList.add('is-error');
    return api;
  }

  function hide(force) {
    if (!el) return api;
    clearTimeout(hideTimer);
    clearTimeout(maxTimer);

    var remaining = force ? 0 : MIN_VISIBLE_MS - (Date.now() - shownAt);
    hideTimer = setTimeout(function () {
      if (!el) return;
      el.classList.add('is-hiding');
      setTimeout(function () {
        if (el && el.parentNode) el.parentNode.removeChild(el);
        el = null;
        label = null;
      }, EXIT_MS);
    }, Math.max(0, remaining));
    return api;
  }

  var api = { show: show, set: set, hide: hide, MIN_VISIBLE_MS: MIN_VISIBLE_MS };
  global.SummitLoading = api;
})(window);
