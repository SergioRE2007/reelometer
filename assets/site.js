(() => { try {
(() => {
  const NBSP = ' ';
  const doc = document.documentElement;
  const lang = doc.lang === 'en' ? 'en' : 'es';
  let dict = {};
  try {
    const node = document.getElementById('i18n');
    if (node) dict = JSON.parse(node.textContent);
  } catch (e) { /* sin textos de JS */ }

  const fill = (s, vars) => String(s).replace(/\{(\w+)\}/g, (m, k) => (vars && k in vars ? vars[k] : m));
  const t = (key, vars) => {
    const v = dict[key];
    if (v == null) { console.warn('[i18n] falta', key); return key; }
    return fill(typeof v === 'object' ? v.other : v, vars);
  };
  const pluralRules = new Intl.PluralRules(lang === 'es' ? 'es-ES' : 'en');
  const plural = (key, n, vars) => {
    const v = dict[key];
    if (v == null) { console.warn('[i18n] falta', key); return key; }
    if (typeof v !== 'object') return fill(v, { n: fmt.int(n), ...vars });
    const cat = pluralRules.select(n);
    return fill(v[cat] ?? v.other, { n: Number.isInteger(n) ? fmt.int(n) : fmt.dec(n, 1), ...vars });
  };

  const SEP = lang === 'es' ? { group: '.', decimal: ',' } : { group: ',', decimal: '.' };
  const group = (digits) => digits.replace(/\B(?=(\d{3})+(?!\d))/g, SEP.group);
  const fmt = {
    int(n) {
      const r = Math.round(Number(n) || 0);
      return (r < 0 ? '−' : '') + group(String(Math.abs(r)));
    },
    dec(n, places = 1) {
      const x = Number(n) || 0;
      const [i, d] = Math.abs(x).toFixed(places).split('.');
      const out = group(i) + (d && /[1-9]/.test(d) ? SEP.decimal + d.replace(/0+$/, '') : '');
      return (x < 0 ? '−' : '') + out;
    },
    pct(n, places = 0) { return fmt.dec(n, places) + (lang === 'es' ? NBSP + '%' : '%'); },
    hours(h, places = 1) { return fmt.dec(h, places) + NBSP + 'h'; },
    minutes(m) { return fmt.int(m) + NBSP + 'min'; },
    seconds(s) { return fmt.int(s) + NBSP + 's'; },
    unit(n, unit, places = 0) { return (places ? fmt.dec(n, places) : fmt.int(n)) + NBSP + unit; },
    NBSP,
  };

  const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  const reducedMotion = () => motionQuery.matches;

  const springs = {
    snappy: { damping: 0.7, stiffness: 900 },
    standard: { damping: 0.85, stiffness: 380 },
    gentle: { damping: 0.9, stiffness: 160 },
    bouncy: { damping: 0.55, stiffness: 500 },
  };
  function spring({ from = 0, to = 1, spring: sp = 'standard', velocity = 0, onUpdate, onDone } = {}) {
    const p = typeof sp === 'string' ? springs[sp] : sp;
    const k = p.stiffness, c = 2 * p.damping * Math.sqrt(k);
    let x = from, v = velocity, target = to, raf = 0, last = 0, stopped = false;
    const finish = () => { x = target; v = 0; onUpdate && onUpdate(x, 0); onDone && onDone(); };
    if (reducedMotion()) { finish(); return { stop() {}, retarget(t2) { target = t2; finish(); } }; }
    const step = (now) => {
      if (stopped) return;
      let dt = last ? Math.min((now - last) / 1000, 0.064) : 1 / 60;
      last = now;
      while (dt > 0) {
        const h = Math.min(dt, 1 / 240);
        const a = -k * (x - target) - c * v;
        v += a * h; x += v * h; dt -= h;
      }
      if (Math.abs(x - target) < 0.001 * Math.max(1, Math.abs(target)) && Math.abs(v) < 0.01) { finish(); return; }
      onUpdate && onUpdate(x, v);
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return {
      stop() { stopped = true; cancelAnimationFrame(raf); },
      retarget(t2) { target = t2; if (stopped) { stopped = false; last = 0; raf = requestAnimationFrame(step); } },
    };
  }

  function onVisible(el, fn, { once = true, threshold = 0.25, rootMargin = '0px' } = {}) {
    if (!el) return () => {};
    if (!('IntersectionObserver' in window)) { fn(true); return () => {}; }
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        if (e.isIntersecting) { fn(true, e); if (once) io.disconnect(); }
        else if (!once) fn(false, e);
      }
    }, { threshold, rootMargin });
    io.observe(el);
    return () => io.disconnect();
  }

  const THEME_KEY = 'reelometer-theme';
  const theme = {
    get() {
      try { return localStorage.getItem(THEME_KEY) || 'system'; } catch (e) { return 'system'; }
    },
    set(mode) {
      try {
        if (mode === 'dark' || mode === 'light') localStorage.setItem(THEME_KEY, mode);
        else localStorage.removeItem(THEME_KEY);
      } catch (e) { /* sin almacenamiento: vale solo para esta visita */ }
      if (mode === 'dark' || mode === 'light') doc.dataset.theme = mode; else delete doc.dataset.theme;
      document.dispatchEvent(new CustomEvent('reel:theme', { detail: { mode, effective: theme.effective() } }));
    },
    effective() {
      const m = doc.dataset.theme;
      if (m === 'dark' || m === 'light') return m;
      return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    },
  };

  function barcode(el, digits) {
    if (!el) return;
    el.classList.add('barcode');
    const bar = (b, g) => `<i${b ? ' class="b"' : ''} style="flex-grow:${g}"></i>`;
    const parts = [bar(1, 1), bar(0, 1), bar(1, 1), bar(0, 2)];
    for (const ch of String(digits)) {
      const d = +ch || 0;
      parts.push(bar(1, 1 + (d % 4)), bar(0, 1 + ((d * 7) % 3)));
    }
    parts.push(bar(0, 1), bar(1, 1), bar(0, 1), bar(1, 1));
    el.innerHTML = parts.join('');
  }

  function stamp(el) {
    if (!el) return;
    el.classList.remove('stamp--pending');
    if (!reducedMotion()) el.classList.add('is-in');
  }

  window.Reel = { lang, t, plural, fmt, reducedMotion, spring, springs, onVisible, theme, barcode, stamp };
})();

} catch (e) { console.error('[js/base.js]', e); } })();
(() => { try {
(() => {
  const R = window.Reel;
  const STEP_MS = 90;          // un volteo intermedio
  const LAST_MS = 260;         // el último, que se asienta con spring
  const STAGGER = 40;          // ms por columna, de izquierda a derecha
  const ACCEL = 'cubic-bezier(0.55, 0, 1, 0.45)';   // la hoja cae (gravedad), no es una duración lineal
  const DECEL = 'cubic-bezier(0, 0.55, 0.45, 1)';
  const SETTLE = getComputedStyle(document.documentElement).getPropertyValue('--spring-snappy').trim() || DECEL;

  function cell(ch) {
    const c = document.createElement('span');
    c.className = 'flap-cell';
    c.innerHTML =
      '<span class="flap-half flap-top"><span></span></span>' +
      '<span class="flap-half flap-bottom"><span></span></span>' +
      '<span class="flap-half flap-top flap-leaf flap-leaf-top"><span></span></span>' +
      '<span class="flap-half flap-bottom flap-leaf flap-leaf-bottom"><span></span></span>';
    setCell(c, ch);
    return c;
  }
  const parts = (c) => c.querySelectorAll(':scope > .flap-half > span');
  function setCell(c, ch) {
    c.dataset.ch = ch;
    for (const s of parts(c)) s.textContent = ch;
    c.classList.toggle('flap-cell--blank', ch === ' ');
  }

  function settle(c) {
    const lt = c.querySelector(':scope > .flap-leaf-top'), lb = c.querySelector(':scope > .flap-leaf-bottom');
    for (const el of [lt, lb]) el.getAnimations().forEach((x) => x.cancel());
    lt.style.visibility = '';
    c.classList.remove('flap-cell--flipping');
  }

  function flipOnce(c, a, b, ms, last, token) {
    settle(c);
    const [top, bottom, leafTop, leafBottom] = parts(c);
    top.textContent = b; bottom.textContent = a; leafTop.textContent = a; leafBottom.textContent = b;
    c.classList.add('flap-cell--flipping');
    const lt = leafTop.parentElement, lb = leafBottom.parentElement;
    const half = ms * 0.5;
    const a1 = lt.animate([{ transform: 'rotateX(0deg)' }, { transform: 'rotateX(-90deg)' }], { duration: half, easing: ACCEL, fill: 'forwards' });
    return a1.finished.then(() => {
      if (c._token !== token) return null;
      lt.style.visibility = 'hidden';
      const a2 = lb.animate([{ transform: 'rotateX(90deg)' }, { transform: 'rotateX(0deg)' }],
        { duration: last ? LAST_MS : half, easing: last ? SETTLE : DECEL, fill: 'forwards' });
      return a2.finished;
    }).then(() => {
      if (c._token !== token) return;
      bottom.textContent = b;
      settle(c);
      c.dataset.ch = b;
    });
  }

  async function run(c, target, delay, token, direct) {
    if (delay) await new Promise((r) => setTimeout(r, delay));
    if (c._token !== token) return;
    const from = c.dataset.ch;
    if (from === target && !c.classList.contains('flap-cell--flipping')) { setCell(c, target); return; }
    const digits = /^\d$/.test(from) && /^\d$/.test(target);
    const seq = [];
    if (digits && !direct && from !== target) {
      let d = +from;
      while (d !== +target) { d = (d + 1) % 10; seq.push(String(d)); }
    } else seq.push(target);
    let cur = from;
    for (let i = 0; i < seq.length; i++) {
      if (c._token !== token) return;
      await flipOnce(c, cur, seq[i], STEP_MS, i === seq.length - 1, token).catch(() => {});
      cur = seq[i];
    }
    if (c._token === token) setCell(c, target);
  }

  function mount(el, { value = 0, minDigits = 1, group = true } = {}) {
    el.classList.add('flap');
    el.textContent = '';
    const row = document.createElement('span');
    row.className = 'flap-row';
    row.setAttribute('aria-hidden', 'true');
    el.append(row);
    let cells = [];
    let shown = '';

    const layout = (str) => {
      if (str.length === cells.length) return;
      const old = cells.map((c) => c.dataset.ch);
      row.textContent = '';
      cells = [];
      const pad = str.length - old.length;
      for (let i = 0; i < str.length; i++) {
        const fromRight = str.length - i;
        if (group && fromRight % 3 === 0 && i > 0 && /^\d+$/.test(str)) {
          const sep = document.createElement('span');
          sep.className = 'flap-sep';
          row.append(sep);
        }
        const c = cell(pad > 0 ? (i < pad ? '0' : old[i - pad]) : str[i]);
        cells.push(c);
        row.append(c);
      }
    };

    const api = {
      value: null,
      set(n, { animate = true, direct = false } = {}) {
        const v = Math.max(0, Math.round(n));
        let str = String(v);
        if (str.length < minDigits) str = str.padStart(minDigits, '0');
        api.value = v;
        api.render(str, animate, direct);
      },
      text(s, { animate = true } = {}) {
        api.value = null;
        api.render(String(s), animate);
      },
      render(str, animate, direct = false) {
        if (str === shown) return;
        layout(str);
        shown = str;
        const instant = !animate || R.reducedMotion();
        const token = {};
        cells.forEach((c, i) => {
          c._token = token;
          if (instant) { settle(c); setCell(c, str[i]); return; }
          run(c, str[i], direct ? 0 : i * STAGGER, token, direct);
        });
        el.dispatchEvent(new CustomEvent('flap:change', { detail: { value: api.value, text: str } }));
      },
    };
    api.set(value, { animate: false });
    return api;
  }

  R.flap = { mount };
})();

} catch (e) { console.error('[js/flap.js]', e); } })();
(() => { try {
const root = document.getElementById('header');
if (!root) return;
const R = window.Reel;
const btn = root.querySelector('[data-theme-btn]');
if (!btn) return;
const name = btn.querySelector('[data-theme-name]');
const ORDER = ['system', 'dark', 'light'];
const mode = () => { const m = R.theme.get(); return ORDER.includes(m) ? m : 'system'; };
const next = (m) => ORDER[(ORDER.indexOf(m) + 1) % ORDER.length];
const word = (m) => R.t('header.js.theme.' + m);

function paint() {
  const m = mode();
  name.textContent = word(m);
  btn.setAttribute('aria-label', R.t('header.js.theme.aria', { cur: word(m), next: word(next(m)) }));
}

btn.addEventListener('click', () => { R.theme.set(next(mode())); paint(); });
window.addEventListener('storage', (e) => {
  if (e.key !== null && e.key !== 'reelometer-theme') return;
  const m = mode();
  if (m === 'dark' || m === 'light') document.documentElement.dataset.theme = m;
  else delete document.documentElement.dataset.theme;
  paint();
});
paint();

} catch (e) { console.error('[js/header.js]', e); } })();
(() => { try {
const root = document.getElementById('hero');
if (!root) return;
const R = window.Reel;
const NB = R.fmt.NBSP;
const $ = (s) => root.querySelector(s);

const SECS = 15;                                   // base común de la web: un vídeo cada 15 s
const FEED_SECS = 0.6;                             // lo que tarda en contar un vídeo del feed
const opened = new Date();
const t0 = Date.now();
const pad = (n, w = 2) => String(n).padStart(w, '0');
const hm = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;
const clock = (d) => `${hm(d)}:${pad(d.getSeconds())}`;
const ticketNo = pad(opened.getHours() * 60 + opened.getMinutes(), 4);
const dayOfYear = Math.round((new Date(opened.getFullYear(), opened.getMonth(), opened.getDate()) - new Date(opened.getFullYear(), 0, 0)) / 864e5);

function dur(s, zeros) {
  if (s < 60) return Number.isInteger(s) ? R.fmt.seconds(s) : R.fmt.dec(s, 1) + NB + 's';
  s = Math.round(s);
  const p = zeros ? pad : String;
  if (s < 3600) return `${Math.floor(s / 60)}${NB}min${NB}${p(s % 60)}${NB}s`;
  return `${Math.floor(s / 3600)}${NB}h${NB}${p(Math.floor(s / 60) % 60)}${NB}min`;
}

const kicker = $('[data-kicker]');
if (kicker) kicker.textContent = R.t('hero.js.kicker', { n: ticketNo, h: hm(opened) });
for (const el of root.querySelectorAll('[data-ticket-no]')) el.textContent = R.t('hero.js.ticket', { n: ticketNo });
const dateEl = $('[data-date]');
if (dateEl) dateEl.textContent = R.t('hero.js.date', { d: pad(opened.getDate()), m: pad(opened.getMonth() + 1), y: opened.getFullYear(), h: hm(opened) });

const flapEl = $('[data-flap]');
const flap = R.flap.mount(flapEl, { value: 0, minDigits: 3, group: true });
const items = $('[data-items]');
const live = $('[data-live]');
const older = $('[data-older]');
const liveLabel = $('[data-live-label]');
const liveSecs = $('[data-live-secs]');
const liveTime = $('[data-live-time]');
const countEl = $('[data-count]');
const totalEl = $('[data-total]');
const quote = $('[data-quote]');
const announce = $('[data-announce]');
const barcodeEl = $('[data-barcode]');
const digitsEl = $('[data-barcode-digits]');
const out = $('[data-out]');
const paper = $('[data-paper]');
const stamp = $('[data-stamp]');

let count = 0;              // vídeos ya cobrados
let lastS = -1;
let lastAnnounce = 0;
let introDone = false;
let timer = 0;

const wide = window.matchMedia('(min-width: 1200px)');
const maxLines = () => (wide.matches ? 4 : 3);   // líneas a la vista; las anteriores se agrupan en una
const shown = [];                                 // líneas impresas a la vista: { kind, n, secs, li }
const group = { a: 0, b: 0, videos: 0, feed: 0, secs: 0 };

const motion = () => !R.reducedMotion() && !document.hidden;

function setDigits(n) {
  root.style.setProperty('--hero-n', String(Math.max(3, String(n).length)));
}

function paintBarcode(s) {
  const code = `${ticketNo}${pad(opened.getDate())}${pad(opened.getMonth() + 1)}${pad(count % 10000, 4)}${pad(s % 10000, 4)}`;
  R.barcode(barcodeEl, code);
  digitsEl.textContent = code.replace(/(\d{4})(?=\d)/g, '$1 ');
}

function paintGroup() {
  if (!group.videos && !group.feed) { older.hidden = true; return; }
  const r = group.videos === 0 ? '' : group.a === group.b ? pad(group.a, 3) : `${pad(group.a, 3)}–${pad(group.b, 3)}`;
  const label = !group.videos ? R.t('hero.js.groupFeedOnly', { f: R.fmt.int(group.feed) })
    : group.feed ? R.t('hero.js.groupFeed', { r, f: R.fmt.int(group.feed) })
    : R.t('hero.js.group', { r });
  older.querySelector('[data-older-label]').textContent = label;
  older.querySelector('[data-older-sum]').textContent = dur(Math.round(group.secs * 10) / 10, true);
  older.hidden = false;
}

function toGroup(e) {
  if (e.kind === 'video') {
    group.a = group.videos ? Math.min(group.a, e.n) : e.n;
    group.b = Math.max(group.b, e.n);
    group.videos++;
  } else group.feed++;
  group.secs += e.secs;
}

function trim(animate) {
  while (shown.length > maxLines()) {
    const e = shown.shift();
    toGroup(e);
    if (animate) {
      e.li.classList.remove('is-in');
      setTimeout(() => e.li.remove(), 700);
    } else e.li.remove();
  }
  paintGroup();
}

function print(e, animate, at) {
  const li = document.createElement('li');
  li.className = 'hero-item';
  const label = e.kind === 'video' ? R.t('hero.js.video', { n: pad(e.n, 3) }) : R.t('hero.js.feed');
  const amount = e.kind === 'video' ? R.fmt.seconds(SECS) : R.fmt.dec(e.secs, 1) + NB + 's';
  li.innerHTML = '<div><span class="hero-item-time"></span><p class="receipt-line"><span></span><span></span></p></div>';
  li.querySelector('.hero-item-time').textContent = clock(at);
  const [l, v] = li.querySelectorAll('.receipt-line > span');
  l.textContent = label;
  v.textContent = amount;
  e.li = li;
  items.insertBefore(li, live);
  shown.push(e);
  if (animate) requestAnimationFrame(() => requestAnimationFrame(() => li.classList.add('is-in')));
  else li.classList.add('is-in');
  trim(animate);
}

function addVideos(from, to, animate) {
  const skipTo = to - maxLines();
  for (let n = from; n <= to; n++) {
    const e = { kind: 'video', n, secs: SECS };
    if (n <= skipTo) toGroup(e);
    else print(e, animate && n === to, new Date(opened.getTime() + n * SECS * 1000));
  }
  paintGroup();
}

function quoteFor(s) {
  const c = count === 0 ? 0 : count === 1 ? 1 : count < 10 ? 2 : 3;
  const key = `hero.js.${['q0', 'q1', 'qn', 'qmany'][c]}.${((dayOfYear + c) % 3) + 1}`;
  return R.t('hero.js.quote', { q: R.t(key, { t: dur(s, false), n: R.fmt.int(count) }) });
}

function emit(s) {
  document.dispatchEvent(new CustomEvent('reel:tally', { detail: { seconds: s, videos: count } }));
}

function tick(catchUp) {
  const s = Math.max(0, Math.floor((Date.now() - t0) / 1000));
  if (s === lastS) return;
  lastS = s;
  const n = Math.floor(s / SECS);
  if (n > count) {
    const animate = !catchUp && n - count === 1 && motion();
    addVideos(count + 1, n, animate);
    count = n;
    setDigits(count);
    if (introDone) flap.set(count, { animate: !R.reducedMotion(), direct: catchUp });
    flapEl.setAttribute('aria-label', R.plural('hero.js.aria', count));
    countEl.textContent = R.fmt.int(count);
    paintBarcode(s);
    emit(s);
    if (!document.hidden && s - lastAnnounce >= 60) {
      lastAnnounce = s;
      announce.textContent = R.plural('hero.js.announce', count, { t: dur(s, false) });
    }
  }
  totalEl.textContent = dur(s, true);
  liveLabel.textContent = R.t('hero.js.video', { n: pad(count + 1, 3) });
  liveSecs.textContent = R.fmt.seconds(s - count * SECS);
  liveTime.textContent = clock(new Date());
  const q = quoteFor(s);
  if (quote.textContent !== q) quote.textContent = q;
}

function start() {
  if (timer) return;
  tick(true);
  timer = setInterval(() => tick(false), 250);
}
function stop() {
  clearInterval(timer);
  timer = 0;
}

function land() {
  out.classList.add('is-out');
  paper.style.translate = '';
  R.stamp(stamp);
}
function intro() {
  introDone = true;
  if (R.reducedMotion()) {
    flap.set(count, { animate: false });
    land();
    return;
  }
  flap.set(count);
  R.onVisible(out, () => {
    R.spring({
      from: -101, to: 0, spring: 'gentle',
      onUpdate: (y) => { paper.style.translate = `0 ${y.toFixed(2)}%`; },
      onDone: land,
    });
  }, { threshold: 0.05 });
}

flapEl.setAttribute('aria-label', R.plural('hero.js.aria', 0));
countEl.textContent = R.fmt.int(0);
setDigits(0);
paintBarcode(0);
if (!R.reducedMotion()) flap.text(R.t('hero.js.blank'), { animate: false });

document.addEventListener('reel:feed', () => {
  print({ kind: 'feed', secs: FEED_SECS }, motion(), new Date());
});
wide.addEventListener('change', () => trim(false));
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { stop(); return; }
  start();
  if (!introDone) intro();
});

emit(0);
if (!document.hidden) {
  start();
  intro();
} else {
  tick(true);   // deja los textos puestos; la animación espera a que se vea la pestaña
}

} catch (e) { console.error('[js/hero.js]', e); } })();
(() => { try {
const root = document.getElementById('board');
if (!root) return;
const R = window.Reel;
if (!R || !R.flap) return;

const reduced = R.reducedMotion();
const KEYS = ['gate', 'dest', 'meta', 'status'];
const STATES = ['ontime', 'boarding', 'seen'];
const word = Object.fromEntries(STATES.map((s) => [s, R.t('board.js.' + s)]));
const up = (s) => s.toLocaleUpperCase(R.lang);
const pad = (s, n) => s.padEnd(n, ' ');
const two = (n) => String(n).padStart(2, '0');

const rows = [...root.querySelectorAll('[data-board-row]')].map((a) => {
  const cells = {}, txt = {};
  for (const k of KEYS) {
    cells[k] = a.querySelector('.board-' + k);
    txt[k] = up(cells[k].textContent.trim().replace(/\s+/g, ' '));
  }
  return { a, id: a.hash.slice(1), cells, txt, sr: a.querySelector('[data-board-sr]'), state: 'ontime', was: false, shown: false, f: {} };
});
if (!rows.length) return;

const n = {};
for (const k of KEYS) n[k] = Math.max(...rows.map((r) => r.txt[k].length));
n.status = Math.max(...STATES.map((s) => up(word[s]).length));
for (const k of KEYS) root.style.setProperty('--board-n-' + k, n[k]);

const target = (row, k) => pad(k === 'status' ? up(word[row.state]) : row.txt[k], n[k]);

for (const row of rows) {
  for (const k of KEYS) {
    const f = R.flap.mount(row.cells[k], { group: false });
    f.text(reduced ? target(row, k) : ' '.repeat(n[k]), { animate: false });
    row.f[k] = f;
  }
  row.shown = reduced;
}

R.onVisible(root.querySelector('.board-rows'), () => {
  if (reduced) return;
  rows.forEach((row, i) => {
    KEYS.forEach((k, j) => {
      setTimeout(() => {
        row.f[k].text(target(row, k));
        if (k === 'status') row.shown = true;
      }, 120 + i * 110 + j * 70);
    });
  });
}, { once: true, threshold: 0.2 });

const clockWrap = root.querySelector('[data-board-clock-wrap]');
const clockEl = root.querySelector('[data-board-clock]');
if (clockWrap && clockEl) {
  const hhmm = () => { const d = new Date(); return two(d.getHours()) + ':' + two(d.getMinutes()); };
  const clock = R.flap.mount(clockEl, { group: false });
  clock.text(hhmm(), { animate: false });
  clockWrap.hidden = false;
  const tick = () => {
    clock.text(hhmm());
    setTimeout(tick, 60000 - (Date.now() % 60000) + 50);
  };
  setTimeout(tick, 60000 - (Date.now() % 60000) + 50);
}

function setState(row, state) {
  if (row.state === state) return;
  row.state = state;
  row.a.dataset.state = state;
  if (row.sr) row.sr.textContent = word[state];
  if (row.shown) row.f.status.text(target(row, 'status'));
}

let fed = false;
const inView = new Set();
function update() {
  const cur = rows.filter((r) => inView.has(r.id)).pop();
  for (const row of rows) {
    const isCur = row === cur;
    if (isCur) row.was = true;
    row.a.setAttribute('aria-current', isCur ? 'true' : 'false');
    setState(row, isCur ? 'boarding' : (row.was || (row.id === 'feed' && fed)) ? 'seen' : 'ontime');
  }
}

if ('IntersectionObserver' in window) {
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (e.isIntersecting) inView.add(e.target.id); else inView.delete(e.target.id);
    }
    update();
  }, { rootMargin: '-40% 0px -59% 0px' });
  for (const row of rows) {
    const sec = document.getElementById(row.id);
    if (sec) io.observe(sec);
  }
}

document.addEventListener('reel:feed', (e) => {
  if (fed || !(e.detail && e.detail.n > 0)) return;
  fed = true;
  update();
});

} catch (e) { console.error('[js/board.js]', e); } })();
(() => { try {
const root = document.getElementById('feed');
if (!root) return;
const R = window.Reel;
const $ = (s, r = root) => r.querySelector(s);
const $$ = (s, r = root) => [...r.querySelectorAll(s)];

const phone = $('.feed-phone');
const scroller = $('.feed-scroller');
const items = $$('.feed-item', scroller);
const segs = $$('.feed-segs i');
const rows = $$('.feed-row');
const pos = $('.feed-pos-i');
const also = $('.feed-also');
const live = $('#feed-live');
const prev = $('.feed-prev');
const next = $('.feed-next');
const N = items.length;
if (!phone || !N) return;

const DWELL = 600;          // ViewTracker: un vídeo cuenta con 600 ms en pantalla
const VISIBLE = 0.6;        // y al menos el 60 % visible
const reduced = () => R.reducedMotion();
const two = (n) => String(n).padStart(2, '0');

const tally = R.flap ? R.flap.mount($('.feed-tally-n'), { value: 0, minDigits: 3, group: false }) : null;

const flips = items.map((it) => {
  const el = $('.feed-num', it);
  if (!el || !R.flap) return null;
  const word = el.dataset.text;
  if (word) {
    const f = R.flap.mount(el, { group: false });
    f.text(word, { animate: false });
    return () => {
      if (reduced()) return;
      f.text(' '.repeat(word.length), { animate: false });
      setTimeout(() => f.text(word), 120);
    };
  }
  const n = Number(el.dataset.n) || 0;
  const f = R.flap.mount(el, { value: n, minDigits: String(n).length, group: false });
  return () => {
    if (reduced()) return;
    if (n === 0) f.text(' ', { animate: false }); else f.set(0, { animate: false });
    setTimeout(() => f.set(n), 120);
  };
});

let current = 0, aim = 0, inView = false, shown = -1, timer = 0, alsoTimer = 0;
const counted = new Set();
const kindOf = (it) => (it.classList.contains('feed-item--screen') ? 'screen' : it.classList.contains('feed-item--end') ? 'end' : 'data');

function paint() {
  items.forEach((_, i) => {
    const state = i === current ? 'now' : counted.has(i) ? 'seen' : 'queued';
    if (segs[i]) segs[i].dataset.state = state;
    const row = rows[i];
    if (!row) return;
    row.dataset.state = state;
    const s = $('.feed-row-s', row);
    if (s) s.textContent = R.t('feed.js.' + state);
    if (state === 'now') row.setAttribute('aria-current', 'true'); else row.removeAttribute('aria-current');
  });
  const out = $('.feed-out', items[N - 1]);
  if (out) out.tabIndex = current === N - 1 ? 0 : -1;
  if (pos) pos.textContent = two(current + 1);
  phone.dataset.kind = kindOf(items[current]);
  if (prev) prev.setAttribute('aria-disabled', String(current === 0));
  if (next) next.setAttribute('aria-disabled', String(current === N - 1));
}

function count(i) {
  counted.add(i);
  const n = counted.size;
  if (tally) tally.set(n);
  if (live) live.textContent = R.plural('feed.js.count', n);
  if (also) {
    also.classList.remove('is-on');
    void also.offsetWidth;
    also.classList.add('is-on');
    clearTimeout(alsoTimer);
    alsoTimer = setTimeout(() => also.classList.remove('is-on'), 2400);
  }
  paint();
  document.dispatchEvent(new CustomEvent('reel:feed', { detail: { n } }));
}

function arm() {
  clearTimeout(timer);
  if (!inView || document.hidden) return;
  if (shown !== current) {
    shown = current;
    if (flips[current]) flips[current]();
  }
  if (counted.has(current)) return;
  const i = current;
  timer = setTimeout(() => {
    if (i === current && inView && !document.hidden) count(i);
  }, DWELL);
}

function setCurrent(i) {
  if (i < 0 || i >= N || i === current) return;
  current = i;
  aim = i;
  paint();
  arm();
}

if ('onscrollsnapchange' in HTMLElement.prototype) {
  scroller.addEventListener('scrollsnapchange', (e) => {
    const t = e.snapTargetBlock;
    if (t) setCurrent(items.indexOf(t));
  });
} else if ('IntersectionObserver' in window) {
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (e.isIntersecting && e.intersectionRatio >= VISIBLE) setCurrent(items.indexOf(e.target));
    }
  }, { root: scroller, threshold: VISIBLE });
  items.forEach((it) => io.observe(it));
}

if ('IntersectionObserver' in window) {
  new IntersectionObserver((entries) => {
    for (const e of entries) inView = e.isIntersecting && e.intersectionRatio >= VISIBLE;
    arm();
  }, { threshold: [0, VISIBLE] }).observe(phone);
} else {
  inView = true;
}
document.addEventListener('visibilitychange', arm);

function go(i) {
  aim = Math.max(0, Math.min(N - 1, i));
  scroller.scrollTo({ top: items[aim].offsetTop, behavior: reduced() ? 'auto' : 'smooth' });
}
const step = (dir) => go(aim + dir);
if (prev) prev.addEventListener('click', () => { if (current > 0) step(-1); });
if (next) next.addEventListener('click', () => { if (current < N - 1) step(1); });
rows.forEach((row) => row.addEventListener('click', () => go(Number(row.dataset.go) || 0)));
scroller.addEventListener('keydown', (e) => {
  if (e.altKey || e.ctrlKey || e.metaKey) return;
  const k = e.key;
  if (k === 'ArrowDown' || k === 'PageDown') step(1);
  else if (k === 'ArrowUp' || k === 'PageUp') step(-1);
  else if (k === 'Home') go(0);
  else if (k === 'End') go(N - 1);
  else return;
  e.preventDefault();
});

paint();

} catch (e) { console.error('[js/feed.js]', e); } })();
(() => { try {
const root = document.getElementById('calc');
if (!root) return;
const R = window.Reel;
const $ = (id) => document.getElementById(id);

const MAX = 1000;
const DAYS = { month: 30, year: 365, decade: 3650 };
const ONE = 0.95;                                    // Amounts.ONE: 0,95 ya se lee «uno»

const EQ = {
  degree: { h: 240 * 25, g: 'estudio' },
  language: { h: 600, g: 'estudio' },
  camino: { h: 780 / 4, g: 'viaje', whole: true },
  got: { h: 70, g: 'pantalla', whole: true },
  quijote: { h: 380000 / 250 / 60, g: 'lectura', whole: true },
  sleep: { h: 8, g: 'descanso' },
  marathon: { h: 4.5, g: 'deporte' },
  tortilla: { h: 0.75, g: 'cocina' },
};
const ORDER = Object.keys(EQ).sort((a, b) => EQ[b].h - EQ[a].h);

const dec = (v, d = Math.abs(v) >= 10 ? 0 : 1) => { const f = 10 ** d; return R.fmt.dec(Math.round(v * f) / f, d); };
const pct = (u) => { const p = u * 100; return p === 0 || p >= 1 ? R.fmt.pct(Math.round(p)) : R.fmt.pct(Math.round(p * 10) / 10, 1); };

function read(u) {
  if (u >= 10) return ['n', Math.round(u)];
  const whole = Math.floor(u), frac = u - whole;
  if (frac < 0.1) return ['n', whole];
  if (frac > 0.9) return ['n', whole + 1];
  if (frac < 0.4) return ['more', whole];
  if (frac <= 0.6) return ['andHalf', whole];
  return ['almost', whole + 1];
}
const K = (k, part) => 'calc.js.eq.' + k + '.' + part;
function amountText(k, u) {
  if (u <= 0) return R.plural(K(k, 'n'), 0);
  if (u >= ONE) { const [part, n] = read(u); return R.plural(K(k, part), n); }
  if (u >= 0.85) return R.plural(K(k, 'almost'), 1);
  if (u < 0.001) return R.t(K(k, 'less'), { p: pct(0.001) });
  if (u >= 0.23 && u < 0.27) return R.t(K(k, 'quarter'));
  if (u >= 0.45 && u < 0.55) return R.t(K(k, 'half'));
  if (u >= 0.73 && u < 0.77) return R.t(K(k, 'threeQuarters'));
  return R.t(K(k, 'percent'), { p: pct(u) });
}
function ticketValue(k, u) {
  let v;
  if (u <= 0) v = R.fmt.int(0);
  else if (u >= ONE) v = dec(u);
  else if (u >= 0.1) v = dec(u, 1);
  else if (u >= 0.01) v = dec(u, 2);
  else v = '<' + dec(0.01, 2);
  return EQ[k].whole ? '×' + v : v;
}
function headline(hours, seed) {
  if (hours <= 0) return R.t('calc.js.none');
  const seen = new Set();
  const top = ORDER.filter((k) => hours / EQ[k].h >= ONE && !seen.has(EQ[k].g) && seen.add(EQ[k].g)).slice(0, 3);
  const k = top.length ? top[seed % top.length] : ORDER[ORDER.length - 1];
  const x = R.t(K(k, 'do'), { x: amountText(k, hours / EQ[k].h) });
  return R.t('calc.js.sentence.' + (seed % 3), { x });
}

const frag = document.createDocumentFragment();
for (let i = 0; i <= 100; i++) {
  const t = document.createElement('span');
  t.className = 'calc-tick' + (i % 10 === 0 ? ' calc-tick--major' : i % 5 === 0 ? ' calc-tick--mid' : '');
  t.style.left = i + '%';
  frag.append(t);
}
for (let i = 0; i <= 10; i++) {
  const n = document.createElement('span');
  n.className = 'calc-tick-n' + (i % 2 ? ' calc-tick-n--odd' : '') + (i === 8 ? ' calc-tick-n--pen' : '') +
    (i === 0 ? ' calc-tick-n--first' : i === 10 ? ' calc-tick-n--last' : '');
  n.style.left = i * 10 + '%';
  n.textContent = R.fmt.int(i * 100);
  frag.append(n);
}
$('calc-ticks').append(frag);

const range = $('calc-range'), ruler = $('calc-ruler');
const rows = [...root.querySelectorAll('.calc-eq')].map((li) => ({
  k: li.dataset.k, val: li.querySelector('.calc-eq-val'), say: li.querySelector('.calc-eq-say'),
}));
const flap = R.flap.mount($('calc-flap'), { value: 0, minDigits: 1, group: true });
const state = { n: 212, pace: 15, period: 'year' };   // 15 s por vídeo: la base común de la web
let flapTimer = 0, liveTimer = 0, shown = null;

const hoursOf = () => (state.n * state.pace * DAYS[state.period]) / 3600;
const hoursLabel = (h) => (h >= 10 ? R.fmt.int(h) : dec(h, 1));

function putFlap(animate) {
  const h = hoursOf();
  const label = hoursLabel(h);
  if (label === shown) return;
  shown = label;
  if (h >= 10) flap.set(Math.round(h), { animate });
  else flap.text(label, { animate });
}

function render({ settle = 'soon' } = {}) {
  const { n, pace, period } = state;
  const hours = hoursOf();
  const seed = Math.floor(n / 50) + Object.keys(DAYS).indexOf(period);

  ruler.style.setProperty('--p', n / MAX);
  range.value = n;
  range.setAttribute('aria-valuetext', R.plural('calc.js.valuetext', n));
  $('calc-n').textContent = R.fmt.int(n);
  $('calc-tag').textContent = R.fmt.int(n);

  clearTimeout(flapTimer);
  if (settle === 'now') putFlap(true);
  else if (settle === 'init') putFlap(false);
  else flapTimer = setTimeout(() => putFlap(true), 160);

  const h = Math.round(hours * 10) / 10;
  const hoursSr = R.plural('calc.js.hours', h >= 10 ? Math.round(h) : h);
  $('calc-hours-sr').textContent = hoursSr + ' ' + R.t('calc.js.per.' + period);
  $('calc-per-label').textContent = R.t('calc.js.period.' + period);
  const days = Math.floor(hours / 24);
  $('calc-days').textContent = days < 1 ? R.t('calc.js.days0') : R.plural('calc.js.days', days);
  $('calc-base').textContent = R.plural('calc.js.base', n, { r: R.fmt.seconds(pace), d: R.fmt.int(DAYS[period]) });
  const sentence = headline(hours, seed);
  $('calc-sentence').textContent = sentence;

  for (const r of rows) {
    const u = hours / EQ[r.k].h;
    r.val.textContent = ticketValue(r.k, u);
    r.val.classList.toggle('is-one', u >= ONE);
    r.say.textContent = amountText(r.k, u);
  }

  clearTimeout(liveTimer);
  if (settle !== 'init') {
    liveTimer = setTimeout(() => {
      $('calc-live').textContent = R.t('calc.js.live', {
        v: R.plural('calc.js.valuetext', n),
        r: R.plural('calc.js.secs', pace),
        h: hoursSr,
        p: R.t('calc.js.per.' + period),
        s: sentence,
      });
    }, 600);
  }
}

const setN = (v, settle) => {
  const n = Math.max(0, Math.min(MAX, Math.round(v)));
  if (n === state.n) return;
  state.n = n;
  render({ settle });
};

let dragging = false;
range.addEventListener('pointerdown', () => { dragging = true; ruler.classList.remove('is-easing'); });
window.addEventListener('pointerup', () => { if (dragging) { dragging = false; putFlap(true); } });
window.addEventListener('pointercancel', () => { dragging = false; });
range.addEventListener('input', () => {
  ruler.classList.toggle('is-easing', !dragging);
  setN(+range.value, 'soon');
});
range.addEventListener('keydown', (e) => {
  const d = { PageUp: 50, PageDown: -50 }[e.key];
  if (!d) return;
  e.preventDefault();
  ruler.classList.add('is-easing');
  setN(state.n + d, 'now');
});

root.querySelectorAll('.calc-step').forEach((b) => {
  const d = +b.dataset.step;
  let hold = 0, rep = 0, repeated = false;
  const stop = () => { clearTimeout(hold); clearInterval(rep); };
  const step = () => { ruler.classList.add('is-easing'); setN(state.n + d, 'soon'); };
  b.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    repeated = false;
    hold = setTimeout(() => { repeated = true; rep = setInterval(step, 90); }, 400);
  });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => b.addEventListener(ev, stop));
  b.addEventListener('click', () => {
    if (repeated) { repeated = false; putFlap(true); return; }
    step();
  });
});

function radiogroup(seg, key, parse) {
  const opts = [...seg.querySelectorAll('[role="radio"]')];
  const placeBar = () => {
    const on = opts.find((o) => o.getAttribute('aria-checked') === 'true');
    seg.style.setProperty('--seg-x', on.offsetLeft + 'px');
    seg.style.setProperty('--seg-w', on.offsetWidth + 'px');
    seg.style.setProperty('--seg-y', on.offsetTop + on.offsetHeight + 'px');   // por si las pestañas bajan de línea
  };
  const choose = (btn, focus) => {
    opts.forEach((o) => { const on = o === btn; o.setAttribute('aria-checked', String(on)); o.tabIndex = on ? 0 : -1; });
    placeBar();
    if (focus) btn.focus();
    const v = parse(btn.dataset.v);
    if (v !== state[key]) { state[key] = v; render({ settle: 'now' }); }
  };
  opts.forEach((o, i) => {
    o.addEventListener('click', () => choose(o));
    o.addEventListener('keydown', (e) => {
      const d = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
      const to = e.key === 'Home' ? 0 : e.key === 'End' ? opts.length - 1 : d ? (i + d + opts.length) % opts.length : -1;
      if (to < 0) return;
      e.preventDefault();
      choose(opts[to], true);
    });
  });
  opts.forEach((o) => { o.tabIndex = o.getAttribute('aria-checked') === 'true' ? 0 : -1; });
  placeBar();
  if (document.fonts) document.fonts.ready.then(placeBar);
  if ('ResizeObserver' in window) new ResizeObserver(placeBar).observe(seg);
}
radiogroup($('calc-pace'), 'pace', Number);
radiogroup($('calc-period'), 'period', String);

render({ settle: 'init' });

} catch (e) { console.error('[js/calc.js]', e); } })();
(() => { try {
const root = document.getElementById('ticket');
if (!root) return;

Reel.barcode(root.querySelector('[data-ticket-barcode]'), '0'.repeat(40));

const seal = root.querySelector('.ticket-seal');
if (seal && !Reel.reducedMotion() && 'IntersectionObserver' in window) {
  seal.classList.add('stamp--pending');
  Reel.onVisible(seal, () => Reel.stamp(seal), { threshold: 0.75, rootMargin: '0px 0px -10% 0px' });
}

} catch (e) { console.error('[js/ticket.js]', e); } })();
(() => { try {
const root = document.getElementById('download');
if (!root) return;
const out = root.querySelector('[data-download-since]');
let shown = -1;
const show = (n) => {
  const v = Math.max(0, Math.floor(Number(n) || 0));
  if (v === shown || !out) return;
  shown = v;
  out.textContent = Reel.plural('download.js.since', v);
};
show(0);
document.addEventListener('reel:tally', (e) => show(e.detail && e.detail.videos));

} catch (e) { console.error('[js/download.js]', e); } })();
(() => { try {
const root = document.getElementById('footer');
if (!root) return;

for (const a of root.querySelectorAll('a[hreflang]')) {
  if (a.hreflang === document.documentElement.lang) a.setAttribute('aria-current', 'true');
}

const out = root.querySelector('[data-footer-videos]');
let shown = 0;
const put = (n) => {
  const v = Math.max(0, Math.floor(Number(n) || 0));
  if (!out || v === shown) return;
  shown = v;
  out.textContent = Reel.fmt.int(v);
};
if (document.getElementById('hero')) {
  document.addEventListener('reel:tally', (e) => put(e.detail && e.detail.videos));
} else {
  setInterval(() => put(performance.now() / 15000), 1000);
}

const metas = [...document.querySelectorAll('meta[name="theme-color"]')];
for (const m of metas) m.dataset.c = m.content;
const colorOf = (scheme) => {
  const m = metas.find((x) => (x.media || '').includes(scheme));
  return m ? m.dataset.c : null;
};
const syncThemeColor = () => {
  const forced = document.documentElement.dataset.theme;
  for (const m of metas) {
    const c = forced === 'dark' || forced === 'light' ? colorOf(forced) : null;
    m.content = c || m.dataset.c;
  }
};
syncThemeColor();
document.addEventListener('reel:theme', syncThemeColor);

} catch (e) { console.error('[js/footer.js]', e); } })();
(() => { try {
const root = document.getElementById('notfound');
if (!root) return;
const doc = document.documentElement;
const base = root.dataset.base || '/';
const path = location.pathname;
const en = path.startsWith(base + 'en/');
const lang = en ? 'en' : 'es';
const header = document.getElementById('header');

const pathOut = root.querySelector('[data-notfound-path]');
if (pathOut) pathOut.textContent = path;

const flapEl = root.querySelector('[data-notfound-flap]');
if (flapEl && Reel.flap) Reel.flap.mount(flapEl, { value: 404, minDigits: 3, group: false });

const home = { es: base, en: base + 'en/' };
const privacy = { es: base + 'privacidad/', en: base + 'en/privacy/' };

if (en) {
  doc.lang = 'en';
  for (const el of root.querySelectorAll('[data-lang]')) el.hidden = el.dataset.lang !== 'en';
  const tr = (k, vars) => {
    const key = 'en:' + k;
    const v = Reel.t(key, vars);
    return v === key ? null : v;
  };
  for (const el of document.querySelectorAll('[data-k]')) {
    const v = tr(el.dataset.k);
    if (v != null) el.innerHTML = v;
  }
  for (const el of document.querySelectorAll('[data-k-label]')) {
    const v = tr(el.dataset.kLabel);
    if (v != null) el.setAttribute('aria-label', v);
  }
  const title = tr('notfound.js.title');
  if (title) document.title = title;

  const tpl = root.querySelector('template[data-notfound-en]');
  const enText = (k) => {
    const el = tpl && tpl.content.querySelector(`[data-key="${k}"]`);
    return el ? el.textContent : null;
  };
  const setText = (el, k) => { const v = enText(k); if (el && v != null) el.textContent = v; };
  const setLabel = (el, k) => { const v = enText(k); if (el && v != null) el.setAttribute('aria-label', v); };
  setText(document.querySelector('a.skip'), 'site.skip');
  if (header) {
    setLabel(header.querySelector('.header-brand'), 'header.home');
    setText(header.querySelector('.header-ver'), 'header.ver');
    setLabel(header.querySelector('.header-nav'), 'header.nav');
    for (const a of header.querySelectorAll('.header-nav a[href*="#"]')) {
      const id = a.getAttribute('href').split('#')[1];
      if (enText('header.nav.' + id) != null) setText(a, 'header.nav.' + id);
    }
    setText(header.querySelector('.header-theme-word'), 'header.theme');
    setText(header.querySelector('.header-lang-cur'), 'header.lang.cur');
    const alt = header.querySelector('.header-lang-alt');
    if (alt) {
      setText(alt, 'header.lang.alt');
      setLabel(alt, 'header.lang.aria');
      alt.setAttribute('hreflang', 'es');
      alt.setAttribute('lang', 'es');
      alt.setAttribute('href', home.es);
    }

    const btn = header.querySelector('[data-theme-btn]');
    const nameEl = btn && btn.querySelector('[data-theme-name]');
    const ORDER = ['system', 'dark', 'light'];
    const paintTheme = () => {
      const cur = Reel.theme.get();
      const m = ORDER.includes(cur) ? cur : 'system';
      const next = ORDER[(ORDER.indexOf(m) + 1) % ORDER.length];
      const w = tr('header.js.theme.' + m), wn = tr('header.js.theme.' + next);
      if (w == null || wn == null) return;
      nameEl.textContent = w;
      const aria = tr('header.js.theme.aria', { cur: w, next: wn });
      if (aria != null) btn.setAttribute('aria-label', aria);
    };
    if (btn && nameEl) {
      const later = () => queueMicrotask(paintTheme);
      btn.addEventListener('click', later);
      window.addEventListener('storage', later);
      document.addEventListener('reel:theme', later);
      paintTheme();
    }
  }
}

for (const a of document.querySelectorAll('a[href]')) {
  const h = a.getAttribute('href');
  const hl = a.getAttribute('hreflang');
  if (hl === 'es' || hl === 'en') {
    a.setAttribute('href', home[hl]);
    if (hl === lang && !(header && header.contains(a))) a.setAttribute('aria-current', 'true');
    else a.removeAttribute('aria-current');
    continue;
  }
  if (h === home.es || h === home.en) a.setAttribute('href', home[lang]);
  else if (h.startsWith(home.es + '#')) a.setAttribute('href', home[lang] + h.slice(home.es.length));
  else if (h === privacy.es || h === privacy.en) a.setAttribute('href', privacy[lang]);
}

} catch (e) { console.error('[js/notfound.js]', e); } })();