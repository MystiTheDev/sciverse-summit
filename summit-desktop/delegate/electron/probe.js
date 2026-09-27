'use strict';

/* Readiness probe.
 *
 * Runs in the main process because the renderer cannot do this job reliably:
 * a `no-cors` fetch from a file:// page to an unreachable address does not
 * settle when you expect (the OS may hold the socket for 20s+), leaving the UI
 * stuck on "Waiting" with no way to report failure. Node's http client gives us
 * a hard, predictable deadline with no CORS involved.
 *
 * It is deliberately an HTTP GET rather than a bare TCP connect: Tomcat binds
 * the port several seconds before the Spring context finishes starting, so a
 * TCP connect reports "up" while the app still cannot serve a page. Any HTTP
 * response (including a redirect or 401) means the app is genuinely ready.
 */

const http = require('http');

const DEFAULT_TIMEOUT = 2500;
const MAX_TIMEOUT = 10000;
const HOST_RE = /^[a-z0-9._:-]{1,255}$/i;

/**
 * @param {string} host
 * @param {number} port
 * @param {string} [path]
 * @param {number} [timeoutMs]
 * @returns {Promise<{up: boolean, ms: number, status?: number, reason?: string}>}
 */
function probeServer(host, port, path, timeoutMs) {
  return new Promise((resolve) => {
    const started = Date.now();

    // This value only ever reaches http.request, but it is untrusted input
    // from the renderer, so keep it to a plain host: no slashes, no spaces.
    if (typeof host !== 'string' || !HOST_RE.test(host)) {
      return resolve({ up: false, ms: 0, reason: 'bad-host' });
    }
    const portNum = Number(port);
    if (!Number.isInteger(portNum) || portNum < 1 || portNum > 65535) {
      return resolve({ up: false, ms: 0, reason: 'bad-port' });
    }

    const timeout = Math.max(250, Math.min(Number(timeoutMs) || DEFAULT_TIMEOUT, MAX_TIMEOUT));
    const target = typeof path === 'string' && /^\/[^\s]*$/.test(path) ? path : '/login';

    let settled = false;
    let req = null;
    let wallClock = null;
    const done = (up, extra) => {
      if (settled) return;
      settled = true;
      if (wallClock) clearTimeout(wallClock);
      if (req) req.destroy();
      resolve(Object.assign({ up: up, ms: Date.now() - started }, extra || {}));
    };

    try {
      req = http.request({
        host: host,
        port: portNum,
        path: target,
        method: 'GET',
        headers: { 'User-Agent': 'SciVerse-Summit-Desktop', Connection: 'close' },
      }, (res) => {
        // Any answer counts as ready. Drain so the socket closes promptly.
        res.resume();
        res.on('end', () => done(true, { status: res.statusCode }));
        res.on('error', () => done(true, { status: res.statusCode }));
      });
    } catch (e) {
      return done(false, { reason: 'error' });
    }

    // Wall-clock bound. req.setTimeout only covers socket *idle* time, so it
    // never fires while the TCP connect itself is hanging on an unroutable
    // address — which is exactly the case that must not block the UI.
    wallClock = setTimeout(() => done(false, { reason: 'timeout' }), timeout);

    req.on('error', (err) => done(false, { reason: (err && err.code) || 'error' }));
    req.end();
  });
}

module.exports = { probeServer };
