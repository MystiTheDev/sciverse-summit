(function () {
    function init() {
        if (!document.body || !document.body.classList.contains('liquid-glass')) return;
        if (window.matchMedia && window.matchMedia('(hover: none)').matches) return;

        var targets = Array.prototype.slice.call(
            document.querySelectorAll('.lg-reactive, .apple-glass-header, .modern-sidebar')
        );
        if (!targets.length) return;

        targets.forEach(function (el) {
            if (el.querySelector('.lg-glow')) return;
            var glow = document.createElement('span');
            glow.className = 'lg-glow';
            glow.setAttribute('aria-hidden', 'true');
            el.appendChild(glow);
            if (getComputedStyle(el).position === 'static') el.style.position = 'relative';

            var ticking = false;
            el.addEventListener('pointermove', function (e) {
                if (ticking) return;
                ticking = true;
                requestAnimationFrame(function () {
                    var r = el.getBoundingClientRect();
                    el.style.setProperty('--lg-x', (e.clientX - r.left) + 'px');
                    el.style.setProperty('--lg-y', (e.clientY - r.top) + 'px');
                    ticking = false;
                });
            });
            el.addEventListener('pointerleave', function () {
                el.style.removeProperty('--lg-x');
                el.style.removeProperty('--lg-y');
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
