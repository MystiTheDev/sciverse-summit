/* Sciverse Summit — lg-14 toast helper (notification-only, offline, no dependencies).
   Mirrors the pasted lg-14 demo behaviour without its full-page section or
   external image. Not auto-wired: existing notif-banner.js remains the single
   notification pipeline; call window.LgNotifToast() for one-off glass toasts. */
(function () {
  if (window.LgNotifToast) return;
  var LIFE = 4000;
  var COPY = {
    success: { ico: '\u2713', title: 'Saved', body: 'Your changes are live.' },
    info: { ico: 'i', title: 'Heads up', body: 'A new version is available.' },
    error: { ico: '!', title: 'Upload failed', body: 'Check your connection and retry.' }
  };
  function region() {
    var r = document.getElementById('lg-notif-region');
    if (!r) {
      r = document.createElement('div');
      r.id = 'lg-notif-region';
      r.className = 'lg-notif-region';
      r.setAttribute('role', 'region');
      r.setAttribute('aria-label', 'Notifications');
      r.setAttribute('aria-live', 'polite');
      document.body.appendChild(r);
    }
    return r;
  }
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  window.LgNotifToast = function (type, title, body) {
    var c = COPY[type] || COPY.info;
    var el = document.createElement('div');
    el.className = 'lg-notif-toast lg-notif-toast--' + (COPY[type] ? type : 'info');
    el.innerHTML = '<span class="lg-notif-ico">' + c.ico + '</span>'
      + '<div class="lg-notif-msg"><strong>' + esc(title != null ? title : c.title) + '</strong>'
      + esc(body != null ? body : c.body) + '</div>'
      + '<button class="lg-notif-close" aria-label="Dismiss">\u2715</button>';
    region().appendChild(el);
    var kill = function () {
      el.classList.add('lg-out');
      el.addEventListener('animationend', function () { el.remove(); }, { once: true });
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 350);
    };
    var t = setTimeout(kill, LIFE);
    el.querySelector('.lg-notif-close').addEventListener('click', function () { clearTimeout(t); kill(); });
  };
})();
