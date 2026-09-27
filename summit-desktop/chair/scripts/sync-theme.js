'use strict';

/* Copies the canonical shared theme into this app's src/ so both apps ship an
 * identical design system. Runs before `npm start` and `npm run dist`.
 * If the shared file is missing (e.g. this app folder was copied out on its
 * own) the existing copy is left untouched so the app still builds. */

const fs = require('fs');
const path = require('path');

const src = path.join(__dirname, '..', '..', 'shared', 'theme.css');
const dest = path.join(__dirname, '..', 'src', 'theme.css');

try {
  if (!fs.existsSync(src)) {
    console.warn('[sync-theme] shared/theme.css not found — keeping existing src/theme.css');
    process.exit(0);
  }
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(src, dest);
  console.log('[sync-theme] theme.css synced');
} catch (err) {
  console.warn('[sync-theme] skipped:', err.message);
}
