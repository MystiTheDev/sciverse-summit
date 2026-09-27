#!/usr/bin/env node
'use strict';
/* Validates a draft release body against the changelog renderer before it is
 * published, so nothing ships as broken literal text.
 *
 *   node summit-desktop/shared/check-release-notes.js notes.md
 *   node summit-desktop/shared/check-release-notes.js notes.md --render
 *
 * --render prints the HTML the app will produce, which is the fastest way to
 * see what a reader will actually see.
 *
 * Uses the real renderMarkdown() out of update-ui.js, so it cannot drift from
 * what the apps do.
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const args = process.argv.slice(2).filter((a) => a !== '--render');
const showRender = process.argv.includes('--render');
const file = args[0];

if (!file) {
  console.error('usage: node check-release-notes.js <notes.md> [--render]');
  process.exit(2);
}

let notes;
try {
  notes = fs.readFileSync(file, 'utf8');
} catch (e) {
  console.error('cannot read ' + file + ': ' + e.message);
  process.exit(2);
}

/* ---- load the real renderer ---- */
const uiPath = path.join(__dirname, 'update-ui.js');
const noop = () => {};
const el = () => ({
  style: {}, dataset: {}, className: '', innerHTML: '', textContent: '',
  disabled: false, title: '', value: '',
  setAttribute: noop, getAttribute: noop, removeAttribute: noop,
  classList: { add: noop, remove: noop, contains: () => false },
  addEventListener: noop, removeEventListener: noop,
  focus: noop, click: noop, querySelector: () => null, querySelectorAll: () => [],
});
const cache = new Map();
const sandbox = {
  console, setTimeout, clearTimeout, setInterval: () => 0, clearInterval: noop,
  JSON, Object, Array, Promise, String, Number, RegExp, Error, Boolean, Date, Math,
};
sandbox.document = {
  getElementById: (id) => { if (!cache.has(id)) cache.set(id, el()); return cache.get(id); },
  addEventListener: noop,
};
sandbox.window = {
  summitAPI: { onUpdateAvailable: noop, onUpdateProgress: noop, onUpdateDownloaded: noop, onUpdateError: noop },
  addEventListener: noop,
};
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
try {
  vm.runInContext(fs.readFileSync(uiPath, 'utf8'), sandbox, { filename: uiPath });
} catch (e) {
  console.error('could not load update-ui.js: ' + e.message);
  process.exit(2);
}
const UI = sandbox.window.SummitUpdateUI;
if (!UI) { console.error('update-ui.js did not expose SummitUpdateUI'); process.exit(2); }

/* ---- checks ---- */
const lines = notes.split(/\r?\n/);
const problems = [];
const warnings = [];
const add = (arr, line, msg) => arr.push({ line, msg });

const isTable = (l) => /^\s*\|.*\|\s*$/.test(l);
const isRule = (l) => /^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(l);
const isImage = (l) => /!\[[^\]]*\]\([^)]*\)/.test(l);
const isFence = (l) => /^\s*```/.test(l);
const isHtml = (l) => /<\/?(details|summary|img|br|div|span|p|table|tr|td|b|i|u|strong|em|a)\b/i.test(l);
const isBullet = (l) => /^\s*([-*+]|\d+[.)])\s+/.test(l);
const isHeading = (l) => /^#{1,6}\s/.test(l);
const isQuote = (l) => /^>\s?/.test(l);
const bulletDepth = (l) => {
  const lead = (/^[ \t]*/.exec(l)[0]);
  if (/\t/.test(lead)) return { tabs: true, depth: 0 };
  return { tabs: false, depth: Math.floor(lead.length / 2) };
};

const MAX_DEPTH = 4; // must match renderMarkdown's cap

lines.forEach((l, i) => {
  const n = i + 1;
  if (isTable(l)) add(problems, n, 'table — collapses into one mangled paragraph. Use bullets instead.');
  if (isImage(l)) add(problems, n, 'image — renders as a stray "!" plus a link. Drop it.');
  if (isFence(l)) add(problems, n, 'code fence — renders as stray backticks. Use inline `code` on one line.');
  if (isRule(l)) add(problems, n, 'horizontal rule — renders as literal dashes. Delete the line.');
  if (isHtml(l)) add(problems, n, 'raw HTML — shown escaped as source text. Use plain markdown.');

  // Nesting IS supported now, so only flag the things it cannot express.
  if (isBullet(l)) {
    const { tabs, depth } = bulletDepth(l);
    if (tabs) add(problems, n, 'tab indentation — use 2 spaces per level.');
    const lead = (/^[ \t]*/.exec(l)[0]).length;
    if (lead % 2 !== 0) add(warnings, n, 'indent of ' + lead + ' spaces — nesting is 2 spaces per level.');
    if (depth >= MAX_DEPTH) add(warnings, n, 'deeper than ' + MAX_DEPTH + ' levels — anything past that is flattened.');
  }
  if (isHeading(l) && /^#{5,6}\s/.test(l)) add(warnings, n, 'h5/h6 renders as h4 — use ## or ### instead.');
  if (/^\s*\d+[.)]\s+/.test(l)) add(warnings, n, 'ordered list renders as bullets, numbers dropped. Prefer "-".');
  if (l.length > 240) add(warnings, n, 'very long line (' + l.length + ' chars) — these get scanned, not read.');
});

// A visible marker inside the body is almost certainly a forgotten instruction.
lines.forEach((l, i) => {
  // Strip heading/bullet/quote markers first, so "- Delete this" is caught too.
  const bare = l.replace(/^\s*(?:[-*+]\s+|\d+[.)]\s+|>\s?|#{1,6}\s+)/, '').trim();
  if (/^(delete|remove)\s+(this|the|these|those|any)\b/i.test(bare)
    || /^(note to self|todo|placeholder|optional codename|comment)\b/i.test(bare)) {
    add(problems, i + 1, 'looks like a leftover instruction, and it will be visible to readers.');
  }
});

// The type marker must be the very first line if present.
const release = UI.parseRelease(notes);
const firstLine = lines[0] || '';
const markerLooksMisplaced = /type\s*:\s*(update|upgrade)/i.test(notes)
  && !/^\s*(?:[-*+]\s*)?(?:\*\*|__)?\s*type\s*:/i.test(firstLine);

const out = { problems, warnings, release, markerLooksMisplaced, body: release.body };

if (showRender) {
  console.log('--- HTML the app will render ---');
  console.log(UI.renderMarkdown(release.body).replace(/></g, '>\n<'));
  console.log('');
}

console.log('file: ' + file);
console.log('type: ' + release.type + (release.type === 'upgrade' ? '   (full-screen prompt, blocked while a session runs)' : '   (small overlay)'));
console.log('changelog: ' + (release.body ? release.body.split(/\r?\n/).filter((l) => l.trim()).length + ' non-empty lines' : 'EMPTY — nothing will be shown'));
console.log('');

if (markerLooksMisplaced) {
  console.log('PROBLEM  line 1: a "type:" marker appears but not on the first line,');
  console.log('                 so it will be treated as changelog text and shown to readers.');
  console.log('');
}
for (const p of problems) console.log('PROBLEM  line ' + p.line + ': ' + p.msg);
for (const w of warnings) console.log('warning  line ' + w.line + ': ' + w.msg);

if (!problems.length && !out.markerLooksMisplaced) {
  console.log('OK — no unsupported syntax found.' + (warnings.length ? ' (' + warnings.length + ' warning(s))' : ''));
}
console.log('');
process.exit(problems.length || out.markerLooksMisplaced ? 1 : 0);
