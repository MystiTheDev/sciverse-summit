'use strict';

/* Copies the canonical shared files into this app so both apps ship an
 * identical design system, notification bridge and notification service.
 * Runs before `npm start` and `npm run dist`.
 *
 * If a shared file is missing (e.g. this app folder was copied out on its own)
 * the existing copy is left untouched so the app still builds.
 */

const fs = require('fs');
const path = require('path');

const APP = path.join(__dirname, '..');
const SHARED = path.join(APP, '..', 'shared');

// theme.css + notif-bridge.js are renderer assets; notifications.js is required
// by the main process, so it lands in electron/ instead of src/.
const FILES = [
  { name: 'theme.css', dest: path.join(APP, 'src', 'theme.css') },
  { name: 'notif-bridge.js', dest: path.join(APP, 'src', 'notif-bridge.js') },
  { name: 'notifications.js', dest: path.join(APP, 'electron', 'notifications.js') },
];

try {
  if (!fs.existsSync(SHARED)) {
    console.warn('[sync-shared] shared/ not found — keeping existing copies');
    process.exit(0);
  }
  let copied = 0;
  for (const file of FILES) {
    const from = path.join(SHARED, file.name);
    if (!fs.existsSync(from)) {
      console.warn(`[sync-shared] shared/${file.name} not found — keeping existing copy`);
      continue;
    }
    fs.mkdirSync(path.dirname(file.dest), { recursive: true });
    fs.copyFileSync(from, file.dest);
    copied++;
  }
  console.log(`[sync-shared] synced ${copied} file(s)`);
} catch (err) {
  console.warn('[sync-shared] skipped:', err.message);
}
