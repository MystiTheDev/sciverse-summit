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
    // The site has no /favicon.ico; every page already declares this logo as
    // its icon, so use the same asset for OS notifications.
    var NOTIF_ICON = '/images/sciverse_summit_logo_final.png';

    var STORAGE_KEY = 'notifBannerShownIds';
    var bannerQueue = [];
    var shownIds = {};
    var unreadPending = {};
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
            if (typeof window.Notification === 'undefined') return;
            if (Notification.permission !== 'granted') return;
            if (!document.hidden) return; // only when user is in another app/tab

            var body = n.message || '';
            var icon = NOTIF_ICON;
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
        // Only count notifications that have been surfaced and are still
        // unread. Counting everything ever shown made the number grow for the
        // whole session, so "(7)" could be shown long after 7 were dismissed.
        var unreadCount = 0;
        try {
            Object.keys(unreadPending).forEach(function (id) {
                if (unreadPending[id]) unreadCount++;
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
        return { label: labels[type] || 'View', href: n.link };
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

        // Speaker-queue and session notices carry no link, so their bell cards
        // are not clickable and could only be cleared by hand. Showing the
        // toast is the delivery, so settle it now instead of leaving the
        // unread badge lit for the rest of the session.
        if (!n.link) {
            markReadOnServer(n.id);
            delete unreadPending[n.id];
            updateTitleFlash();
        }

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
        unreadPending[id] = true;
        saveShownIds();
    }

    // Server-side "mark as read" for a notification the user cannot act on.
    // Speaker and session alerts have no deep link, so their bell cards were
    // never clickable and stayed unread forever, keeping the badge lit.
    function markReadOnServer(id) {
        if (id == null) return;
        try {
            fetch('/api/notifications/read?id=' + encodeURIComponent(id), {
                method: 'POST',
                credentials: 'same-origin'
            }).catch(function () { /* best effort */ });
        } catch (e) { /* best effort */ }
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

                // Drop anything the server now reports as read, so the title
                // flash count reflects reality rather than session history.
                items.forEach(function (n) {
                    if (n && n.read) delete unreadPending[n.id];
                });

                if (bannerQueue.length > 0) {
                    showNextFromQueue();
                }
                updateTitleFlash();
            }).catch(function (e) { console.warn('[notif-banner] poll failed:', e); });
    }

    /* Desktop notification permission.
       Permission used to be requested once, on the first click anywhere, and
       never re-offered. If it was denied, desktop notifications were simply
       switched off with no explanation and no way back, so the Settings modal
       calls these to report the state and re-request. */
    function notifPermissionState() {
        if (typeof window.Notification === 'undefined') return 'unsupported';
        return window.Notification.permission; // 'granted' | 'denied' | 'default'
    }

    function requestNotifPermission() {
        if (typeof window.Notification === 'undefined') return Promise.resolve('unsupported');
        var api = window.Notification;
        if (api.permission !== 'default') return Promise.resolve(api.permission);
        try {
            return Promise.resolve(api.requestPermission())
                .then(function (result) {
                    window.dispatchEvent(new CustomEvent('summit-notif-permission', { detail: { permission: result } }));
                    return result;
                });
        } catch (e) {
            return Promise.resolve(api.permission);
        }
    }

    // Keep the existing lazy request on first interaction, but announce the
    // outcome so the Settings row updates if it is granted there.
    (function requestNotifPermission() {
        function tryRequest() {
            if (typeof window.Notification !== 'undefined' && window.Notification.permission === 'default') {
                window.Notification.requestPermission().then(function (result) {
                    window.dispatchEvent(new CustomEvent('summit-notif-permission', { detail: { permission: result } }));
                }).catch(function () { /* ignored */ });
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
    window.SummitNotifPermission = {
        state: notifPermissionState,
        request: requestNotifPermission
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
