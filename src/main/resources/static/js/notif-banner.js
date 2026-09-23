/**
 * Shared notification banner logic.
 * Include this script on any page that needs the floating notification banner.
 *
 * Page-specific behaviour is configured via window.NotifBannerConfig:
 *   {
 *     showLink: true/false,        -- show the "View" link button (default: false)
 *     onSessionEnded: function(),  -- called when SESSION_ENDED notification arrives
 *     extraOnInit: function(),     -- called after banner init
 *   }
 */
(function () {
    if (window.NotifBanner) return; // already loaded

    var config = window.NotifBannerConfig || {};
    // Identity for the per-user SSE filter. The current-user meta lives in the
    // fragment <head> (never inserted into pages), so fall back to the hidden
    // carrier rendered inside the header fragment (present on every app page).
    function readCurrentUser() {
        var m = document.querySelector('meta[name="current-user"]');
        if (m && m.content) return m.content;
        var el = document.getElementById('svCurrentUser');
        if (el && el.textContent) return el.textContent.trim();
        return '';
    }
    var currentUser = readCurrentUser();
    var banner = document.getElementById('notifBanner');
    var titleEl = document.getElementById('notifBannerTitle');
    var msgEl = document.getElementById('notifBannerMsg');
    var dismissBtn = document.getElementById('notifBannerDismiss');
    var bannerInner = document.getElementById('notifBannerInner');
    var linkEl = document.getElementById('notifBannerLink');
    var linkText = document.getElementById('notifBannerLinkText');

    if (!banner) return;

    var STORAGE_KEY = 'notifBannerShownIds';
    var bannerQueue = [];
    var shownIds = {};
    var originalTitle = document.title;
    var titleFlashInterval = null;
    var MAX_VISIBLE = 3;
    var TOAST_LIFE = 8000;

    // Restore shown IDs from sessionStorage
    try {
        var stored = sessionStorage.getItem(STORAGE_KEY);
        if (stored) shownIds = JSON.parse(stored);
    } catch (e) {}

    function saveShownIds() {
        try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(shownIds)); } catch (e) {}
    }

    function notifColor(type) {
        var m = {
            'DELEGATE_JOINED': 'linear-gradient(135deg,#10b981,#059669)',
            'DELEGATE_LEFT': 'linear-gradient(135deg,#ef4444,#dc2626)',
            'MOTION': 'linear-gradient(135deg,#f59e0b,#d97706)',
            'VOTING': 'linear-gradient(135deg,#3b82f6,#6366f1)',
            'SESSION_ENDED': 'linear-gradient(135deg,#ef4444,#b91c1c)',
            'RESOLUTION': 'linear-gradient(135deg,#8b5cf6,#7c3aed)',
            'SPEAKER': 'linear-gradient(135deg,#06b6d4,#7c3aed)'
        };
        return m[type] || 'linear-gradient(135deg,#3b82f6,#6366f1)';
    }

    function notifIcon(type) {
        var m = {
            'DELEGATE_JOINED': 'bi-person-check-fill',
            'DELEGATE_LEFT': 'bi-person-x-fill',
            'MOTION': 'bi-megaphone-fill',
            'VOTING': 'bi-bullseye',
            'SESSION_ENDED': 'bi-door-closed-fill',
            'RESOLUTION': 'bi-file-earmark-text-fill',
            'SPEAKER': 'bi-mic-fill'
        };
        return m[type] || 'bi-bell-fill';
    }

    // --- Notification chime (distinct from timer chime) ---
    function playNotifChime() {
        try {
            if (typeof window.getSetting === 'function' && window.getSetting('chimeVolume') != null && Number(window.getSetting('chimeVolume')) <= 0) return;
            var AudioCtx = window.AudioContext || window.webkitAudioContext;
            if (!AudioCtx) return;
            if (!window._audioCtx) window._audioCtx = new AudioCtx();
            var ctx = window._audioCtx;
            if (ctx.state === 'suspended') ctx.resume().catch(function(){});

            var vol = (typeof window.getSetting === 'function'
                ? (window.getSetting('chimeVolume') == null ? 70 : Number(window.getSetting('chimeVolume')))
                : 70) / 100;
            if (vol <= 0) return;

            var now = ctx.currentTime;
            var master = ctx.createGain();
            master.gain.setValueAtTime(vol * 0.6, now);
            master.connect(ctx.destination);

            // Two-tone ascending ping: E5 -> A5 (soft, short, pleasant)
            var notes = [[659.25, 0], [880, 0.15]];
            notes.forEach(function(note) {
                var freq = note[0], offset = note[1];
                var osc = ctx.createOscillator();
                var g = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(freq, now + offset);
                g.gain.setValueAtTime(0, now + offset);
                g.gain.linearRampToValueAtTime(0.7, now + offset + 0.02);
                g.gain.exponentialRampToValueAtTime(0.001, now + offset + 0.45);
                osc.connect(g);
                g.connect(master);
                osc.start(now + offset);
                osc.stop(now + offset + 0.5);
            });
        } catch (e) { /* AudioContext blocked - silent fail */ }
    }

    // --- Browser Notification API (desktop notification when tab hidden) ---
    function fireDesktopNotification(n) {
        try {
            if (!('Notification' in window)) return;
            if (Notification.permission !== 'granted') return;
            if (!document.hidden) return; // only when user is in another app/tab

            var body = n.message || '';
            var icon = '/favicon.ico';
            var notif = new Notification(n.title || 'Notification', {
                body: body,
                icon: icon,
                tag: 'summit-' + n.id,
                renotify: true
            });
            if (n.link) {
                notif.onclick = function () {
                    window.focus();
                    window.location.href = n.link;
                    notif.close();
                };
            } else {
                notif.onclick = function () { window.focus(); notif.close(); };
            }
            setTimeout(function () { notif.close(); }, 10000);
        } catch (e) { /* Notification API blocked - silent fail */ }
    }

    // --- Title flash for unread notifications ---
    function startTitleFlash(count) {
        if (titleFlashInterval) return;
        var on = true;
        titleFlashInterval = setInterval(function () {
            document.title = on ? '(' + count + ') New Notification' : originalTitle;
            on = !on;
        }, 1200);
    }

    function stopTitleFlash() {
        if (titleFlashInterval) { clearInterval(titleFlashInterval); titleFlashInterval = null; }
        document.title = originalTitle;
    }

    function updateTitleFlash() {
        var unreadCount = 0;
        try {
            Object.keys(shownIds).forEach(function (id) {
                if (shownIds[id]) unreadCount++;
            });
        } catch (e) {}
        if (unreadCount > 0 && document.hidden) {
            startTitleFlash(unreadCount);
        } else {
            stopTitleFlash();
        }
    }

    // Restore original title when tab becomes visible
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) stopTitleFlash();
    });

    /* Stacked glass toasts. Entry animation is pure CSS (lg-notif-in);
       exit goes through sciVerseAnime.toastOutRight. The legacy single
       #notifBanner element is left untouched (hidden) for compatibility. */
    function region() {
        var r = document.getElementById('svToastRegion');
        if (!r) {
            r = document.createElement('div');
            r.id = 'svToastRegion';
            r.setAttribute('role', 'region');
            r.setAttribute('aria-label', 'Notifications');
            r.setAttribute('aria-live', 'polite');
            document.body.appendChild(r);
        }
        return r;
    }

    function visibleCount() {
        var r = document.getElementById('svToastRegion');
        return r ? r.children.length : 0;
    }

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /* Action button rules: SPEAKER never (banner-only); VOTING always
       ("Go to Voting" — delegates vote from showLink:false pages);
       MOTION and the rest only on showLink:true (chair) pages. */
    function actionFor(n) {
        if (!n || !n.link) return null;
        var type = String(n.type || '').toUpperCase();
        // Speaker queue alerts are banner-only by design — no navigation button.
        if (type === 'SPEAKER') return null;
        // Voting alerts always carry "Go to Voting" (delegates vote from their
        // portal, which sets showLink:false — the button must still appear).
        if (type === 'VOTING') return { label: 'Go to Voting', href: n.link };
        if (!config.showLink) return null;
        var labels = { MOTION: 'Go to Motions', VOTING: 'Go to Voting', RESOLUTION: 'View Resolutions' };
        var hideTypes = config.hideTypes || [];
        if (hideTypes.indexOf(n.type) === -1) {
            return { label: labels[type] || 'View', href: n.link };
        }
        return null;
    }

    function buildToast(n) {
        var type = String((n && n.type) || 'GENERAL').toUpperCase();
        var node = document.createElement('div');
        node.className = 'sv-toast';
        node.dataset.notifType = type;
        var act = actionFor(n);
        node.innerHTML =
            '<span class="sv-toast-ico"><i class="bi ' + notifIcon(n.type) + '"></i></span>' +
            '<div class="sv-toast-body"><strong>' + esc(n.title || 'Notification') + '</strong>' +
            '<span class="sv-toast-msg">' + esc(n.message || '') + '</span>' +
            (act ? '<br><a class="sv-toast-act" href="' + esc(act.href) + '"><i class="bi bi-box-arrow-up-right"></i>' + esc(act.label) + '</a>' : '') +
            '</div>' +
            '<button class="sv-toast-x" aria-label="Dismiss"><i class="bi bi-x-lg"></i></button>';
        node.querySelector('.sv-toast-x').addEventListener('click', function () {
            dismissToast(node);
        });
        return node;
    }

    function dismissToast(node) {
        if (!node || node._gone) return;
        node._gone = true;
        if (node._timer) clearTimeout(node._timer);
        var done = function () {
            if (node.parentNode) node.parentNode.removeChild(node);
            showNextFromQueue();
        };
        if (window.sciVerseAnime && window.sciVerseAnime.toastOutRight) {
            window.sciVerseAnime.toastOutRight(node, done);
        } else {
            done();
        }
    }

    function showBanner(n) {
        if (visibleCount() >= MAX_VISIBLE) {
            bannerQueue.push(n);
            return;
        }
        var node = buildToast(n);
        var reg = region();
        reg.insertBefore(node, reg.firstChild); // newest on top
        node._timer = setTimeout(function () { dismissToast(node); }, TOAST_LIFE);

        playNotifChime();

        fireDesktopNotification(n);

        if ((n.type || '').toUpperCase() === 'SESSION_ENDED' && typeof config.onSessionEnded === 'function') {
            setTimeout(config.onSessionEnded, 3500);
        }
    }

    function hideBanner() {
        var reg = document.getElementById('svToastRegion');
        if (reg) {
            Array.from(reg.children).forEach(function (child) { dismissToast(child); });
        }
        bannerQueue.length = 0;
    }

    function showNextFromQueue() {
        if (visibleCount() >= MAX_VISIBLE) return;
        if (bannerQueue.length > 0) {
            showBanner(bannerQueue.shift());
        }
    }

    function markShown(id) {
        shownIds[id] = true;
        saveShownIds();
    }

    function poll() {
        fetch('/api/notifications', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                var items = (data && data.items) || [];
                var unread = items.filter(function (n) { return !n.read && !shownIds[n.id]; });

                unread.forEach(function (n) {
                    console.log('[notif-banner] new notification:', n.id, n.type, n.title);
                    markShown(n.id);
                    bannerQueue.push(n);
                });

                if (bannerQueue.length > 0) {
                    showNextFromQueue();
                }
                updateTitleFlash();
            }).catch(function (e) { console.warn('[notif-banner] poll failed:', e); });
    }

    if (dismissBtn) dismissBtn.addEventListener('click', function () {
        hideBanner();
        showNextFromQueue();
    });

    // Request Notification permission on first user interaction
    (function requestNotifPermission() {
        function tryRequest() {
            if ('Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
            document.removeEventListener('pointerdown', tryRequest);
        }
        document.addEventListener('pointerdown', tryRequest, { once: true });
    })();

    // Expose for external use
    window.NotifBanner = {
        poll: poll,
        hideBanner: hideBanner,
        showBanner: showBanner
    };

    poll();
    setInterval(poll, 5000);
    if (window.SummitLive) window.SummitLive.on('notif.changed', function (evt) {
        // Direct push: if the SSE payload contains a notification, show it immediately
        if (evt && evt.data) {
            try {
                var n = JSON.parse(evt.data);
                if (n && n.id && !shownIds[n.id]) {
                    // Strict per-user gate: render a pushed notification ONLY when
                    // it is addressed to this logged-in user. Anything else (or an
                    // unknown identity) falls through to poll(), which is
                    // server-side per-user and can never leak across accounts.
                    if (!n.userId || !currentUser || n.userId !== currentUser) {
                        poll();
                        return;
                    }
                    console.log('[notif-banner] SSE push:', n.id, n.type, n.title);
                    markShown(n.id);
                    bannerQueue.push(n);
                    showNextFromQueue();
                }
            } catch (e) { /* non-JSON payload - fall through to poll */ }
        }
        // Also poll for any other unread notifications (belt-and-suspenders)
        poll();
    });

    if (typeof config.extraOnInit === 'function') config.extraOnInit();
})();
