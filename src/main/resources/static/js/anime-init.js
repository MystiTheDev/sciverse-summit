/* SciVerse Summit — anime.js animation helpers */

(function() {
    'use strict';

    var lib = window.sciVerseAnime || {};
    window.sciVerseAnime = lib;

    var initializedPages = new Set();

    lib.ready = function() {
        return typeof anime !== 'undefined';
    };

    // Phase 2: honor OS reduced-motion — entrances/ambient/hover all check this.
    lib.shouldAnimate = function() {
        return !(typeof window.matchMedia === 'function'
                && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
    };

    lib.safe = function(fn) {
        if (!lib.ready()) return;
        try { fn(); } catch (e) { /* noop */ }
    };

    lib.staggerCards = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateY: [18, 0],
                delay: anime.stagger(65),
                duration: 540,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.staggerRows = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateY: [14, 0],
                delay: anime.stagger(38),
                duration: 420,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.staggerStatTiles = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateY: [20, 0],
                scale: [0.92, 1],
                delay: anime.stagger(55),
                duration: 500,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.fadeUp = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateY: [16, 0],
                duration: 480,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.pulseLive = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                scale: [1, 1.45, 1],
                opacity: [1, 0.4, 1],
                duration: 1400,
                easing: 'easeInOutSine',
                loop: true
            });
        });
    };

    lib.driftBlobs = function(selector) {
        lib.safe(function() {
            var nodes = document.querySelectorAll(selector);
            if (!nodes.length) return;
            anime({
                targets: nodes,
                translateX: function() { return anime.random(-50, 50); },
                translateY: function() { return anime.random(-50, 50); },
                scale: [1, 1.22, 1],
                duration: 20000,
                easing: 'easeInOutSine',
                direction: 'alternate',
                loop: true
            });
        });
    };

    lib.toastIn = function(el) {
        lib.safe(function() {
            if (!el) return;
            anime({
                targets: el,
                opacity: [0, 1],
                translateY: [-14, 0],
                duration: 340,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.toastOut = function(el) {
        lib.safe(function() {
            if (!el) return;
            anime({
                targets: el,
                opacity: 0,
                translateY: -14,
                duration: 260,
                easing: 'easeInCubic'
            });
        });
    };

    // Phase 4: right-exit used by the stacked notification toasts on dismiss.
    // Always fires `done` (even with reduced motion or if anime is missing)
    // so callers never leak toast nodes.
    lib.toastOutRight = function(el, done) {
        var finish = function() { if (typeof done === 'function') done(); };
        if (!el) { finish(); return; }
        if (!lib.shouldAnimate() || !lib.ready()) { finish(); return; }
        try {
            anime({
                targets: el,
                opacity: 0,
                translateX: '110%',
                duration: 260,
                easing: 'easeInCubic',
                complete: finish
            });
        } catch (e) { finish(); }
    };

    /* Slide-right exit for stacked glass toasts / panel cards.
       Entry animations are owned by CSS (lg-notif-in) - do not touch. */
    lib.toastOutRight = function(el, done) {
        var finished = false;
        function once() { if (!finished) { finished = true; (done || function () {})(); } }
        if (!lib.ready() || !el) { once(); return; }
        try {
            anime({
                targets: el,
                opacity: 0,
                translateX: 60,
                duration: 280,
                easing: 'easeInCubic',
                complete: once
            });
            /* Safety: never leave a half-exited node behind. */
            setTimeout(once, 600);
        } catch (e) { once(); }
    };

    lib.modalIn = function(modalEl) {
        if (!lib.shouldAnimate()) return;
        lib.safe(function() {
            if (!modalEl) return;
            var content = modalEl.querySelector('.modal-content');
            if (!content) return;
            content.style.opacity = '0';
            content.style.transform = 'scale(0.95)';
            anime({
                targets: content,
                opacity: [0, 1],
                scale: [0.95, 1],
                duration: 300,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.dropdownIn = function(menuEl) {
        lib.safe(function() {
            if (!menuEl) return;
            menuEl.style.opacity = '0';
            menuEl.style.transform = 'translateY(-10px)';
            anime({
                targets: menuEl,
                opacity: [0, 1],
                translateY: [-10, 0],
                duration: 240,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.buttonPop = function(btn) {
        lib.safe(function() {
            if (!btn) return;
            anime({
                targets: btn,
                scale: [1, 0.94, 1.04, 1],
                duration: 360,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.cardHover = function(selector) {
        if (!lib.shouldAnimate()) return;
        lib.safe(function() {
            var nodes = document.querySelectorAll(selector);
            if (!nodes.length) return;
            nodes.forEach(function(node) {
                node.addEventListener('mouseenter', function() {
                    anime({
                        targets: node,
                        translateY: -6,
                        scale: 1.01,
                        duration: 280,
                        easing: 'easeOutExpo'
                    });
                });
                node.addEventListener('mouseleave', function() {
                    anime({
                        targets: node,
                        translateY: 0,
                        scale: 1,
                        duration: 280,
                        easing: 'easeOutExpo'
                    });
                });
            });
        });
    };

    lib.ripple = function(btn) {
        lib.safe(function() {
            if (!btn) return;
            btn.addEventListener('click', function(e) {
                var rect = btn.getBoundingClientRect();
                var ripple = document.createElement('span');
                var size = Math.max(rect.width, rect.height);
                ripple.style.cssText = 'position:absolute;border-radius:50%;background:rgba(255,255,255,0.35);transform:scale(0);pointer-events:none;width:' + size + 'px;height:' + size + 'px;left:' + (e.clientX - rect.left - size / 2) + 'px;top:' + (e.clientY - rect.top - size / 2) + 'px;';
                btn.style.position = 'relative';
                btn.style.overflow = 'hidden';
                btn.appendChild(ripple);
                anime({
                    targets: ripple,
                    scale: [0, 2.5],
                    opacity: [0.6, 0],
                    duration: 600,
                    easing: 'easeOutExpo',
                    complete: function() {
                        if (ripple.parentNode) ripple.parentNode.removeChild(ripple);
                    }
                });
            });
        });
    };

    // Phase 1: click/press physics for buttons — ripple + pop. Guarded so a
    // button is never double-wired, and skipped for reduced-motion users.
    lib.pressable = function(selector) {
        if (typeof window.matchMedia === 'function'
                && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        lib.safe(function() {
            var nodes = document.querySelectorAll(selector);
            if (!nodes.length) return;
            nodes.forEach(function(btn) {
                if (btn.__svPressable) return;
                btn.__svPressable = true;
                lib.ripple(btn);
                btn.addEventListener('click', function() { lib.buttonPop(btn); });
            });
        });
    };

    lib.countUp = function(el, target, duration) {
        lib.safe(function() {
            if (!el) return;
            duration = duration || 1200;
            var obj = { value: 0 };
            anime({
                targets: obj,
                value: target,
                round: 1,
                duration: duration,
                easing: 'easeOutExpo',
                update: function() {
                    el.textContent = obj.value;
                }
            });
        });
    };

    lib.slideInLeft = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateX: [-30, 0],
                duration: 500,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.slideInRight = function(selector) {
        lib.safe(function() {
            anime({
                targets: selector,
                opacity: [0, 1],
                translateX: [30, 0],
                duration: 500,
                easing: 'easeOutExpo'
            });
        });
    };

    lib.spinElement = function(el) {
        lib.safe(function() {
            if (!el) return;
            anime({
                targets: el,
                rotate: '1turn',
                duration: 800,
                easing: 'linear',
                loop: true
            });
        });
    };

    lib.stopSpinElement = function(el) {
        lib.safe(function() {
            if (!el) return;
            anime.remove(el);
            el.style.transform = '';
        });
    };

    lib.initPage = function(pageName) {
        if (!lib.ready() || !lib.shouldAnimate()) return;
        if (initializedPages.has(pageName)) return;
        initializedPages.add(pageName);

        if (pageName === 'index') {
            lib.staggerCards('.delegates-card, .bulk-actions-bar');
            lib.staggerRows('.modern-delegates-table tbody tr');
            lib.driftBlobs('.bg-blob');
            lib.cardHover('.delegates-card');
            lib.cardHover('.bulk-actions-bar');
        }

        if (pageName === 'notes') {
            lib.staggerCards('.eval-card');
            lib.cardHover('.eval-card');
        }

        if (pageName === 'dashboard') {
            lib.staggerCards('.action-btn, .join-code-dashboard');
            lib.cardHover('.action-btn');
            lib.cardHover('.join-code-dashboard');
            lib.pressable('.action-btn');
            lib.driftBlobs('.bg-blob');
        }

        if (pageName === 'speakers') {
            lib.pulseLive('.live-pulse, .gtf-pulse-dot');
            lib.staggerCards('.glass-card');
            lib.cardHover('.glass-card');
            lib.pressable('.btn-stage, .btn-green, .btn-ghost, .btn-red, .btn-amber, #add-to-queue-btn, .btn-glass, .timer-controls .btn, #now-speaking-panel .btn-danger, .queue-item .btn');
        }

        if (pageName === 'motions') {
            lib.pulseLive('.live-pulse');
            lib.staggerCards('.live-motion-card, .panel-card');
            lib.cardHover('.live-motion-card');
            lib.pressable('.live-motion-card .btn, .accept-actions .btn, #acceptStayBtn, #acceptGoBtn');
        }

        if (pageName === 'history') {
            lib.staggerCards('.history-card, .glass-card');
            lib.cardHover('.history-card');
        }

        if (pageName === 'resolution') {
            lib.staggerCards('.res-card, .res-item');
            lib.cardHover('.res-card');
        }

        if (pageName === 'voting') {
            lib.staggerCards('.vote-card, .res-item');
            lib.cardHover('.vote-card');
        }

        if (pageName === 'add-edit') {
            lib.staggerCards('.glass-card');
            lib.cardHover('.glass-card');
        }

        if (pageName === 'import') {
            lib.staggerCards('.imp-card, .glass-card');
            lib.cardHover('.imp-card');
        }

        if (pageName === 'delegate-home') {
            lib.staggerCards('.panel-card');
            lib.cardHover('.panel-card');
            lib.pressable('.btn-join, .link-btn, .btn-join-secondary, #joinSubmitBtn, #leaveSubmitBtn');
            lib.driftBlobs('.bg-blob');
        }

        if (pageName === 'delegate-motions') {
            lib.staggerCards('.motion-card');
            lib.cardHover('.motion-card');
        }

        if (pageName === 'delegate-resolution') {
            lib.staggerCards('.res-card');
            lib.cardHover('.res-card');
        }

        if (pageName === 'delegate-voting') {
            lib.staggerCards('.vote-card');
            lib.cardHover('.vote-card');
        }

        if (pageName === 'delegate-stats') {
            lib.staggerStatTiles('.stat-tile, .glass-card');
            lib.cardHover('.stat-tile');
            // Counting numbers, growing bars, sweeping score ring.
            lib.safe(function() {
                document.querySelectorAll('.metric-value, .activity-cell-value, .score-value, .overall-score').forEach(function(el) {
                    var raw = (el.textContent || '').trim();
                    if (!/^\d+$/.test(raw)) return;
                    var target = parseInt(raw, 10);
                    el.textContent = '0';
                    lib.countUp(el, target, 1000);
                });
                document.querySelectorAll('.breakdown-score').forEach(function(el) {
                    var m = /^\s*(\d+)\s*\/\s*(\d+)\s*$/.exec(el.textContent || '');
                    if (!m) return;
                    var target = parseInt(m[1], 10), max = m[2], obj = { value: 0 };
                    anime({
                        targets: obj, value: target, round: 1,
                        duration: 1000, easing: 'easeOutExpo',
                        update: function() { el.textContent = obj.value + '/' + max; }
                    });
                });
                document.querySelectorAll('.breakdown-fill').forEach(function(el, i) {
                    var target = el.style.width || '0%';
                    el.style.width = '0%';
                    anime({
                        targets: el, width: target,
                        duration: 900, delay: 150 + i * 90, easing: 'easeOutExpo'
                    });
                });
                var CIRC = 502.65;
                document.querySelectorAll('.ring-fill, .ring-glow').forEach(function(el) {
                    var target = parseFloat(el.getAttribute('stroke-dashoffset') || '0');
                    el.style.strokeDashoffset = CIRC;
                    anime({
                        targets: el, strokeDashoffset: target,
                        duration: 1200, easing: 'easeOutExpo'
                    });
                });
            });
        }

        if (pageName === 'admin-users') {
            lib.staggerCards('.user-card, .glass-card');
            lib.cardHover('.user-card');
        }

        if (pageName === 'admin-audit') {
            lib.staggerCards('.audit-card, .filter-bar');
            lib.cardHover('.audit-card');
        }

        if (pageName === 'login') {
            lib.driftBlobs('.bg-blob');
            lib.fadeUp('.login-card');
        }

        if (pageName === 'register') {
            lib.staggerCards('.registration-card');
            lib.cardHover('.registration-card');
        }

        if (pageName === 'setup') {
            lib.staggerCards('.setup-card');
            lib.cardHover('.setup-card');
            lib.pressable('.btn-create');
        }

        if (pageName === 'stats') {
            lib.staggerStatTiles('.stats-card, .glass-card, .top-card');
            lib.cardHover('.stats-card');
            lib.cardHover('.top-card');
            lib.cardHover('.stat-tile');
            lib.pressable('.btn-liquid');
            // Phase 2: table rows cascade + animated counters (skip the m/s time tile).
            lib.staggerRows('.modern-stats-table tbody tr');
            (function() {
                var tiles = document.querySelectorAll('.stats-grid .stat-tile .stat-tile-value');
                [0, 1, 3, 4].forEach(function(i) {
                    var el = tiles[i];
                    if (!el) return;
                    var target = parseInt((el.textContent || '').replace(/[^0-9]/g, ''), 10);
                    if (isNaN(target)) return;
                    el.textContent = '0';
                    lib.countUp(el, target, 900);
                });
            })();
        }

        if (pageName === 'unmod') {
            lib.staggerCards('.timer-card');
            lib.cardHover('.timer-card');
        }

        if (pageName === 'data-import') {
            lib.staggerCards('.imp-card, .glass-card');
            lib.cardHover('.imp-card');
        }
    };

    // Auto-detect current page and run page-specific entrance animations.
    function autoInit() {
        if (!lib.ready()) return;
        try {
            var path = window.location.pathname || '';
            var page = path.replace(/^\//, '') || 'index';
            if (!page) page = 'index';
            // Map URL paths to the page names used in initPage()
            if (page === 'add' || page.indexOf('/edit/') === 0) page = 'add-edit';
            if (page === 'delegate') page = 'delegate-home';
            if (page.indexOf('/delegate/') === 0) page = page.replace(/^delegate\//, 'delegate-');
            if (page.indexOf('/admin/') === 0) page = page.replace(/\//g, '-');
            if (page === 'settings/install') page = 'data-import';
            lib.initPage(page);
        } catch (e) { /* noop */ }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', autoInit);
    } else {
        autoInit();
    }

    // Phase 4: one global Bootstrap modal entrance — every page, present and
    // future. `show.bs.modal` bubbles, so a single capture listener covers all
    // modals without touching any page markup.
    if (!window.__svModalWired) {
        window.__svModalWired = true;
        document.addEventListener('show.bs.modal', function(e) {
            if (e && e.target) lib.modalIn(e.target);
        }, true);
    }

    // Expose for manual calls from inline scripts / pages.
    window.sciVerseAnime.autoInit = autoInit;

})();
