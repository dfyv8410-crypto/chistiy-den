// Unit tests for storage and utility functions
// Run: node tests/unit/storage.test.js

// Simulate the functions from index.html
function calcDays(startStr) {
  if (!startStr) return 0;
  const parts = startStr.trim().split('T')[0].split('-').map(Number);
  if (parts.length < 3 || isNaN(parts[0]) || isNaN(parts[1]) || isNaN(parts[2])) {
    console.error('calcDays: invalid date', startStr);
    return 0;
  }
  const startUTC = Date.UTC(parts[0], parts[1] - 1, parts[2]);
  const now = new Date();
  const nowUTC = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.max(0, Math.floor((nowUTC - startUTC) / 86400000));
}

function pluralDays(n) {
  const mod10 = n % 10, mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return 'день';
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'дня';
  return 'дней';
}

function formatDuration(min) {
  if (!min || min <= 0) return '';
  if (min < 60) return min + ' мин';
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h + ' ч ' + m + ' мин';
}

function intensityColor(v) {
  if (v <= 3) return '#3DDC84';
  if (v <= 6) return '#F0C040';
  if (v <= 8) return '#E89040';
  return '#E04040';
}

const MILESTONES = [
  { days: 0, key: 'white', name: 'Белая', hex: '#F0EDE8', desc: 'Первый день' },
  { days: 30, key: 'orange', name: 'Оранжевая', hex: '#E8924A', desc: '30 дней' },
  { days: 60, key: 'green', name: 'Зелёная', hex: '#5DB87A', desc: '60 дней' },
  { days: 90, key: 'red', name: 'Красная', hex: '#D4605A', desc: '90 дней' },
  { days: 182, key: 'blue', name: 'Синяя', hex: '#4A7FB5', desc: '6 месяцев' },
  { days: 274, key: 'yellow', name: 'Жёлтая', hex: '#D4A843', desc: '9 месяцев' },
  { days: 365, key: 'gold', name: 'Золотая', hex: '#C8963E', desc: '1 год' },
  { days: 548, key: 'gray', name: 'Серая', hex: '#A09888', desc: '18 месяцев' },
  { days: 730, key: 'black', name: 'Чёрная', hex: '#4A4038', desc: '2 года' },
  { days: 1095, key: 'purple', name: 'Фиолетовая', hex: '#8B6FA8', desc: '3 года и далее' },
];

function currentMilestoneIndex(days) {
  let idx = 0;
  for (let i = 0; i < MILESTONES.length; i++) {
    if (days >= MILESTONES[i].days) idx = i;
  }
  return idx;
}

function esc(s) {
  const d = { textContent: s || '' };
  return d.innerHTML || String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// === TESTS ===
let passed = 0, failed = 0;

function assert(cond, msg) {
  if (cond) { passed++; } else { failed++; console.error('FAIL:', msg); }
}

// calcDays
assert(calcDays('') === 0, 'calcDays empty');
assert(calcDays(null) === 0, 'calcDays null');
assert(calcDays('invalid') === 0, 'calcDays invalid');

// pluralDays
assert(pluralDays(1) === 'день', 'pluralDays 1');
assert(pluralDays(2) === 'дня', 'pluralDays 2');
assert(pluralDays(5) === 'дней', 'pluralDays 5');
assert(pluralDays(11) === 'дней', 'pluralDays 11');
assert(pluralDays(21) === 'день', 'pluralDays 21');
assert(pluralDays(22) === 'дня', 'pluralDays 22');

// formatDuration
assert(formatDuration(0) === '', 'formatDuration 0');
assert(formatDuration(5) === '5 мин', 'formatDuration 5');
assert(formatDuration(60) === '1 ч 0 мин', 'formatDuration 60');
assert(formatDuration(90) === '1 ч 30 мин', 'formatDuration 90');

// intensityColor
assert(intensityColor(1) === '#3DDC84', 'intensityColor 1');
assert(intensityColor(5) === '#F0C040', 'intensityColor 5');
assert(intensityColor(7) === '#E89040', 'intensityColor 7');
assert(intensityColor(10) === '#E04040', 'intensityColor 10');

// currentMilestoneIndex
assert(currentMilestoneIndex(0) === 0, 'milestone 0 days');
assert(currentMilestoneIndex(15) === 0, 'milestone 15 days');
assert(currentMilestoneIndex(30) === 1, 'milestone 30 days');
assert(currentMilestoneIndex(45) === 1, 'milestone 45 days');
assert(currentMilestoneIndex(365) === 6, 'milestone 365 days');
assert(currentMilestoneIndex(2000) === 9, 'milestone 2000 days');

// esc
assert(esc('<script>') === '&lt;script&gt;', 'esc HTML');
assert(esc('') === '', 'esc empty');

// Report
console.log(`\nResults: ${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
