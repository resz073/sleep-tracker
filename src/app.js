// Sleep Tracker: screens, sleep maths, bedtime schedule, sync, sign-in and reminders.
import * as backend from "./backend.js";
import * as platform from "./platform.js";
import { links, appVersion } from "./app-config.js";

const NEED = 480; // sleep goal: 8 hours
const LS_TAB = "sleep-tracker-tab", LS_VIEW = "sleep-tracker-view", LS_REM = "sleep-tracker-reminders";
const $ = id => document.getElementById(id);
const RX = /^\d{4}-\d\d-\d\dT\d\d:\d\d$/, HM = /^([01]\d|2[0-3]):[0-5]\d$/;
const WEEKENDS = { "fri-sat": [5, 6], "sat-sun": [6, 0], "fri": [5] }, LATS = [5, 10, 15, 20, 30, 45];
const VNAMES = { today: "Today", trends: "Trends", schedule: "Schedule", history: "History" };
const blank = () => ({ entries: [], pending: { bed: null, t: 0 }, prefs: { weekend: "fri-sat", wake: null, lat: 15, t: 0 } });

let S = blank(), M = null, P = null;
let uid = null, account = null, unsub = null, live = false, ready = false, serverCanon = null, syncState = "local", writing = false, again = false;
let fresh = false, edit = null, hWeek = null, undoFn = null, toastT = 0, bdManual = false, tab = "m", listOpen = false, lastDay = "", pick = -1, tN = 0, view = "today";
const sel = { m: null, w: null };
const queuedActions = [];

/* ---------- helpers ---------- */
const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const pad = n => (n < 10 ? "0" : "") + n;
function ts(dt) { const m = /^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d)$/.exec(dt || ""); return m ? new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5]).getTime() : NaN; }
const dstr = d => d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
const okDT = s => { if (!RX.test(s || "")) return false; const d = new Date(ts(s)); return dstr(d) === s.slice(0, 10) && pad(d.getHours()) + ":" + pad(d.getMinutes()) === s.slice(11); };
const dur = e => Math.round((ts(e.wake) - ts(e.bed)) / 60000);
const valid = e => { if (!e || !okDT(e.bed) || !okDT(e.wake)) return false; const d = dur(e); return d > 0 && d <= 1440; };
const future = e => ts(e.wake) > Date.now() + 60000;
const tod = dt => (+dt.slice(11, 13)) * 60 + (+dt.slice(14, 16));
function fmt(m) { m = ((Math.round(m) % 1440) + 1440) % 1440; let h = Math.floor(m / 60); const mi = m % 60, ap = h >= 12 ? "PM" : "AM"; h = h % 12 || 12; return h + ":" + pad(mi) + " " + ap; }
function hm(m) { m = Math.abs(Math.round(m)); return Math.floor(m / 60) + "h " + pad(m % 60) + "m"; }
function dshort(m) { m = Math.abs(Math.round(m)); if (m < 60) return m + " min"; const h = Math.floor(m / 60), r = m % 60; return h + "h" + (r ? " " + pad(r) + "m" : ""); }
function dtiny(m) { m = Math.abs(Math.round(m)); if (m < 60) return m + "m"; const h = Math.floor(m / 60), r = m % 60; return h + "h" + (r ? pad(r) : ""); }
const cls = m => m > 0 ? "bad" : "good"; // m = goal minus actual: positive is a deficit
const adj = m => m < 720 ? m + 1440 : m;  // clock minutes on a night scale: times before noon count as after midnight
const mean = a => a.reduce((x, y) => x + y, 0) / a.length;
function sd(a) { if (a.length < 2) return 0; const m = mean(a); return Math.sqrt(mean(a.map(x => (x - m) * (x - m)))); }
const med = a => { const s = a.slice().sort((x, y) => x - y), k = s.length >> 1; return s.length % 2 ? s[k] : (s[k - 1] + s[k]) / 2; };
const r5 = m => Math.round(m / 5) * 5;
const mod = m => ((Math.round(m) % 1440) + 1440) % 1440;
const hhmm = m => { m = mod(m); return pad(Math.floor(m / 60)) + ":" + pad(m % 60); };
const hmin = s => (+s.slice(0, 2)) * 60 + (+s.slice(3, 5));
const dobj = k => { const p = k.split("-"); return new Date(+p[0], p[1] - 1, +p[2]); };
const addD = (k, n) => { const d = dobj(k); d.setDate(d.getDate() + n); return dstr(d); };
const fdate = (k, o) => dobj(k).toLocaleDateString("en-GB", o);
const dayFull = k => fdate(k, { weekday: "short" }) + " " + dobj(k).getDate() + " " + fdate(k, { month: "short" });
const dayShort = k => fdate(k, { day: "numeric", month: "short" });
const when = dt => dayFull(dt.slice(0, 10)) + ", " + fmt(tod(dt));
const today = () => dstr(new Date());
const nowStr = () => { const n = new Date(); return dstr(n) + "T" + pad(n.getHours()) + ":" + pad(n.getMinutes()); };
const rid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
function wkStart(k) { const d = dobj(k); d.setDate(d.getDate() - ((d.getDay() + 1) % 7)); return dstr(d); } // weeks run Saturday to Friday
// Which night a sleep belongs to, named by that night's wake-up date. Sleep starting 7 PM to noon is
// part of the night ending that morning (split sleep is grouped); starts from noon to 7 PM are naps.
const nightOf = e => { const tb = tod(e.bed); if (tb >= 720 && tb < 1140) return null; return tb >= 1140 ? addD(e.bed.slice(0, 10), 1) : e.bed.slice(0, 10); };
const isNight = e => nightOf(e) !== null;
const nightName = W => { const p = addD(W, -1); return fdate(p, { weekday: "short" }) + " " + dobj(p).getDate() + " – " + fdate(W, { weekday: "short" }) + " " + dobj(W).getDate(); };
const seq = (p, n) => { const o = []; for (let i = 0; i < n; i++) o.push(addD(p, i)); return o; };
function split(e) {
  const out = []; let a = ts(e.bed); const b = ts(e.wake);
  while (a < b) { const d = new Date(a), nm = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime(), end = Math.min(nm, b); out.push([dstr(d), Math.round((end - a) / 60000)]); a = end; }
  return out;
}
const mstat = (big, label, c) => '<div><b' + (c ? ' class="' + c + '"' : '') + '>' + big + '</b><span>' + label + '</span></div>';

/* ---------- state, merge & sync ----------
   Every entry has an id and a last-changed time t. Deleting leaves a marker {id,del,t}.
   This phone's copy and the account's copy are merged entry by entry, newest change wins,
   so nothing saved offline is lost when the other copy arrives. */
function normEntry(x) {
  if (!x || typeof x !== "object") return null;
  if (x.del && typeof x.id === "string" && x.id) return { id: x.id, del: 1, t: +x.t || 0 };
  let bed = x.bed, wake = x.wake;
  if (x.d && /^\d\d:\d\d$/.test(bed || "") && /^\d\d:\d\d$/.test(wake || "")) { bed = (bed > wake ? addD(x.d, -1) : x.d) + "T" + bed; wake = x.d + "T" + wake; }
  if (!okDT(bed) || !okDT(wake)) return null;
  const q = +x.q;
  return { id: typeof x.id === "string" && x.id ? x.id : "k" + bed + "|" + wake, bed, wake, q: q >= 1 && q <= 5 ? Math.round(q) : 0, t: +x.t || 0 };
}
function fromRaw(d) {
  const s = blank(); if (!d || typeof d !== "object") return s;
  if (Array.isArray(d.entries)) d.entries.forEach(x => { const e = normEntry(x); if (e) s.entries.push(e); });
  if (d.pending && typeof d.pending === "object") s.pending = { bed: okDT(d.pending.bed) ? d.pending.bed : null, t: +d.pending.t || 0 };
  if (d.prefs && typeof d.prefs === "object") { const p = d.prefs, lat = +p.lat; s.prefs = { weekend: WEEKENDS[p.weekend] ? p.weekend : "fri-sat", wake: HM.test(p.wake || "") ? p.wake : null, lat: LATS.includes(lat) ? lat : 15, t: +p.t || 0 }; }
  return merge(blank(), s);
}
function win(a, b) { if (!a) return b; if (!b) return a; if (a.t !== b.t) return a.t > b.t ? a : b; return JSON.stringify(a) >= JSON.stringify(b) ? a : b; }
function merge(a, b) {
  const m = new Map(); a.entries.concat(b.entries).forEach(e => m.set(e.id, win(m.get(e.id), e)));
  return { entries: [...m.values()], pending: win(a.pending, b.pending), prefs: win(a.prefs, b.prefs) };
}
function canon(s) {
  return JSON.stringify({
    entries: s.entries.slice().sort((x, y) => x.id < y.id ? -1 : x.id > y.id ? 1 : 0).map(e => e.del ? { id: e.id, del: 1, t: e.t } : { id: e.id, bed: e.bed, wake: e.wake, q: e.q, t: e.t }),
    pending: { bed: s.pending.bed, t: s.pending.t }, prefs: { weekend: s.prefs.weekend, wake: s.prefs.wake, lat: s.prefs.lat, t: s.prefs.t }
  });
}
const lsKey = u => "sleep-tracker:data:" + (u || "local");
function loadLocal() { try { const v = localStorage.getItem(lsKey(uid)); if (v) return fromRaw(JSON.parse(v)); } catch (e) { /* storage unavailable */ } return blank(); }
function saveLocal() { try { localStorage.setItem(lsKey(uid), canon(S)); } catch (e) { /* storage full or unavailable */ } }
function clearLocal(u) { try { localStorage.removeItem(lsKey(u)); } catch (e) { /* ignore */ } }
function status() {
  $("storage").textContent = !uid ? "Accounts aren't set up in this build, so your entries are saved on this phone only."
    : syncState === "ok" ? "Your entries are saved to your account and sync across your devices."
    : syncState === "err" ? "Not synced yet. Your entries are kept on this phone and will sync when you're back online."
    : "Syncing…";
}
function push() {
  if (!uid || !live) return;
  if (writing) { again = true; return; }
  const c = canon(S); if (c === serverCanon) return;
  if (serverCanon === null && !S.entries.length && !S.pending.t && !S.prefs.t) return;
  writing = true;
  const who = uid;
  backend.save(who, JSON.parse(c)).then(() => { if (who === uid) syncState = "ok"; }, () => { if (who === uid) syncState = "err"; })
    .then(() => { writing = false; status(); if (again) { again = false; push(); } });
}
function onServer(data) {
  if (!live) return;
  const server = data ? fromRaw(data) : null; serverCanon = server ? canon(server) : null;
  const before = canon(S); if (server) S = merge(S, server); const after = canon(S);
  if (after !== before) { saveLocal(); render(); if (!$("entrySheet").open) resetForm(); else calc(); }
  if (after !== serverCanon) push(); else { syncState = "ok"; status(); }
}
function commit() { saveLocal(); render(); push(); }
function put(e) { S.entries = S.entries.filter(x => x.id !== e.id).concat([e]); }
function act() {
  const by = new Map();
  S.entries.forEach(e => { if (e.del || !valid(e)) return; const k = e.bed + "|" + e.wake, o = by.get(k); if (!o || e.t > o.t) by.set(k, e); });
  return [...by.values()].sort((a, b) => ts(a.wake) - ts(b.wake) || ts(a.bed) - ts(b.bed));
}

/* ---------- day accounting ----------
   A day is complete when the night ending that morning and the night ending the next morning are both logged.
   Complete days count toward averages and deficit. A missed night (named by its wake-up date) leaves
   the day before and the day after incomplete; it is counted once, as a night not logged.
   Today, and any day touched by a sleep still in progress, is open. */
function model() {
  const es = act(), t = today(), byDay = {}, nightW = new Map();
  es.forEach(e => {
    split(e).forEach(p => { byDay[p[0]] = (byDay[p[0]] || 0) + p[1]; });
    const W = nightOf(e); if (!W) return;
    let N = nightW.get(W); if (!N) { N = { W, bed: e.bed, wake: e.wake, mins: 0, segs: [] }; nightW.set(W, N); }
    if (e.bed < N.bed) N.bed = e.bed; if (e.wake > N.wake) N.wake = e.wake; N.mins += dur(e); N.segs.push(e);
  });
  nightW.forEach(N => { const r = N.segs.filter(e => e.q); N.q = r.length ? mean(r.map(e => e.q)) : 0; N.unrated = N.segs.some(e => !e.q); });
  const nights = [...nightW.values()].sort((a, b) => a.W < b.W ? -1 : 1); // each night: first bedtime, final wake-up, total minutes asleep
  const start = nights.length ? nights[0].W : null;
  const pend = S.pending.bed && ts(S.pending.bed) <= Date.now() + 300000 ? S.pending.bed : null, pDay = pend ? pend.slice(0, 10) : null;
  const status = d => !start || d < start ? "before" : d > t ? "future" : (d === t || (pDay && d >= pDay)) ? "open" : (nightW.has(d) && nightW.has(addD(d, 1))) ? "done" : "missing";
  const inProg = pend ? nightOf({ bed: pend }) : null, MN = new Set();
  if (start) for (let W = addD(start, 1); W <= t; W = addD(W, 1)) if (!nightW.has(W) && !(inProg && W >= inProg)) MN.add(W);
  return { es, t, byDay, nightW, nights, start, pend, status, MN };
}
function stats(days) {
  let tot = 0, n = 0, met = 0, miss = 0;
  days.forEach(d => { const s = M.status(d); if (s === "done") { const v = M.byDay[d] || 0; tot += v; n++; if (v >= NEED) met++; } if (M.MN.has(d)) miss++; });
  return { tot, n, met, miss, bal: n * NEED - tot };
}
const nn = n => n > 1 ? "nights not logged" : "night not logged";
function missingNights() { const lo = addD(M.t, -13); return [...M.MN].filter(W => W >= lo && W < M.t).sort(); }
function usual() {
  const n = M.nights.slice(-14); if (!n.length) return { bt: "23:00", wt: "07:00" };
  return { bt: hhmm(r5(med(n.map(N => adj(tod(N.bed)))))), wt: hhmm(r5(med(n.map(N => tod(N.wake))))) };
}

/* ---------- bedtime schedule ----------
   Wake-up is the anchor: the time you set, or your usual (median) wake-up over the last 14 nights.
   Target bedtime = wake-up minus the 8 hour goal minus time to fall asleep.
   Tonight's bedtime moves toward the target 15 minutes at a time, starting from your usual bedtime over the
   last 7 nights. As your usual bedtime shifts, the next step follows, so the plan adapts to what you actually do. */
function plan() {
  const t = M.t, n14 = M.nights.filter(N => N.W > addD(t, -14) && N.W <= t), n7 = n14.filter(N => N.W > addD(t, -7));
  const autoWake = n14.length >= 3 ? r5(med(n14.map(N => tod(N.wake)))) : null, fixed = S.prefs.wake;
  const wake = fixed ? hmin(fixed) : autoWake; if (wake == null) return null;
  const lat = S.prefs.lat, target = adj(mod(wake - NEED - lat));
  const base = n7.length >= 3 ? n7 : n14.length >= 3 ? n14 : null, cur = base ? r5(med(base.map(N => adj(tod(N.bed))))) : null;
  let tonight = target, steps = 0, dir = 0;
  if (cur != null && Math.abs(cur - target) > 15) { dir = Math.sign(cur - target); tonight = cur - dir * 15; steps = Math.ceil((Math.abs(cur - target) - 15) / 15); }
  const sleepT = wake + 1440 - tonight - lat, cyc = Math.max(1, Math.round(sleepT / 90));
  const onT = n7.filter(N => Math.abs(adj(tod(N.bed)) - target) <= 30).length;
  return { wake, autoWake, fixed: !!fixed, lat, target, tonight, cur, dir, steps, sleepT, cyc, tcyc: Math.max(1, Math.round(NEED / 90)), n7: n7.length, onT, n14 };
}

/* ---------- the tonight strip: 6 PM to 2 PM, planned night in cycles, actual sleep, now ---------- */
function strip(tg) {
  const now = Date.now(), F = M.pend ? ts(M.pend) : now, fd = new Date(F), d0 = dstr(fd);
  const ws = ts((fd.getHours() < 14 ? addD(d0, -1) : d0) + "T18:00"), we = ws + 1200 * 60000;
  const x0 = 4, x1 = 316, X = m => x0 + (x1 - x0) * Math.max(0, Math.min(1200, m)) / 1200, off = v => (v - ws) / 60000, y = 22, h = 16;
  let s = '<rect x="' + x0 + '" y="' + y + '" width="' + (x1 - x0) + '" height="' + h + '" rx="8" fill="var(--sky-line)"/>', lab = "Tonight";
  if (P) {
    const bt = tg ? P.target : P.tonight, cy = tg ? P.tcyc : P.cyc, a = bt - 1080, b = P.wake + 360, la = Math.min(b, a + P.lat), len = (b - la) / cy;
    s += '<rect x="' + X(a) + '" y="' + y + '" width="' + Math.max(0, X(la) - X(a) - 1) + '" height="' + h + '" rx="4" fill="var(--sky-acc)" fill-opacity=".18"/>';
    for (let k = 0; k < cy; k++) { const ca = X(la + k * len) + 1, cb = X(la + (k + 1) * len) - 1; s += '<rect x="' + ca + '" y="' + y + '" width="' + Math.max(1, cb - ca) + '" height="' + h + '" rx="4" fill="var(--sky-acc)" fill-opacity=".36"/>'; }
    const bl = Math.max(26, Math.min(294, X(a))), wl = Math.max(26, Math.min(294, X(b)));
    s += '<text x="' + bl + '" y="14" font-size="11" font-weight="800" text-anchor="middle" fill="var(--sky-ink)">' + fmt(bt) + '</text>';
    if (Math.abs(wl - bl) > 56) s += '<text x="' + wl + '" y="14" font-size="11" font-weight="800" text-anchor="middle" fill="var(--sky-dawn)">' + fmt(P.wake) + '</text>';
    lab = "Planned sleep " + fmt(bt) + " to " + fmt(P.wake) + ", about " + cy + " sleep cycles";
  }
  M.es.forEach(e => { const a = Math.max(ts(e.bed), ws), b = Math.min(ts(e.wake), we); if (b > a) s += '<rect x="' + X(off(a)) + '" y="' + y + '" width="' + Math.max(2, X(off(b)) - X(off(a))) + '" height="' + h + '" rx="4" fill="var(--sky-acc)"/>'; });
  if (M.pend) { const a = Math.max(ts(M.pend), ws), b = Math.min(now, we); if (b > a) s += '<rect x="' + X(off(a)) + '" y="' + y + '" width="' + Math.max(2, X(off(b)) - X(off(a))) + '" height="' + h + '" rx="4" fill="var(--sky-acc)"/>'; }
  if (now > ws && now < we) { const nx = X(off(now)); s += '<line x1="' + nx + '" x2="' + nx + '" y1="' + (y - 5) + '" y2="' + (y + h + 5) + '" stroke="var(--sky-dawn)" stroke-width="2.5" stroke-linecap="round"/>'; }
  [[0, "6 PM", "start"], [360, "12 AM", "middle"], [720, "6 AM", "middle"], [1080, "12 PM", "middle"]].forEach(([m, l, an]) => { s += '<text x="' + X(m) + '" y="56" font-size="10" text-anchor="' + an + '" fill="var(--sky-mute)">' + l + '</text>'; });
  return '<svg class="strip" viewBox="0 0 320 62" role="img" aria-label="' + esc(lab) + '">' + s + '</svg>';
}

/* ---------- today ---------- */
function renderTonight() {
  const t = M.t; let h;
  if (M.pend) {
    const el = Math.max(0, (Date.now() - ts(M.pend)) / 60000), pd = M.pend.slice(0, 10);
    h = '<div class="lbl">Sleeping since</div><div class="big">' + fmt(tod(M.pend)) + '</div><div class="meta"><b>' + hm(el) + '</b> so far' + (pd < addD(t, -1) ? ', started ' + esc(dayFull(pd)) : '') + '</div>' + strip(false) +
      '<div class="row2"><button class="btn dawn" id="awake" type="button">I’m awake</button><button class="btn ghost" id="pcancel" type="button">Cancel</button></div>';
  } else if (P) {
    const n = new Date(), nm = n.getHours() * 60 + n.getMinutes(), d = (mod(P.tonight) - nm + 1440) % 1440, past = d > 1440 - 240;
    h = '<div class="lbl">Bedtime tonight</div><div class="big">' + fmt(P.tonight) + '</div><div class="meta">' + (past ? '<b>' + dshort(1440 - d) + '</b> past bedtime.' : d < 5 ? 'It’s bedtime.' : 'In <b>' + dshort(d) + '</b>.') + ' Wake up at <b>' + fmt(P.wake) + '</b>.</div>' +
      (P.steps ? '<div class="meta">A step toward your <b>' + fmt(P.target) + '</b> target.</div>' : '') + strip(false) +
      '<button class="btn block" id="tosleep" type="button">Going to sleep</button>';
  } else {
    h = '<div class="lbl">Bedtime tonight</div><div class="big sm2">Not set yet</div><div class="meta">Log 3 nights, or set your wake-up time in Schedule.</div>' +
      '<div class="row2" style="margin-top:12px"><button class="btn" id="tosleep" type="button">Going to sleep</button><button class="btn ghost" type="button" data-go="schedule">Set wake-up</button></div>';
  }
  $("tonight").innerHTML = h;
}
function renderToday() {
  const t = M.t;
  renderTonight();
  const ln = M.nightW.get(t); let h = "";
  if (ln) {
    const d = ln.mins, diff = NEED - d, C = 2 * Math.PI * 40, pc = Math.min(1, d / NEED);
    h = '<div class="ln"><svg class="ring" viewBox="0 0 96 96" role="img" aria-label="' + hm(d) + ' of your 8 hour goal"><circle cx="48" cy="48" r="40" fill="none" stroke="var(--card2)" stroke-width="10"/>' +
      '<circle cx="48" cy="48" r="40" fill="none" stroke="' + (d >= NEED ? "var(--good)" : "var(--acc)") + '" stroke-width="10" stroke-linecap="round" stroke-dasharray="' + (C * pc).toFixed(1) + ' ' + C.toFixed(1) + '" transform="rotate(-90 48 48)"/>' +
      '<text x="48" y="50" text-anchor="middle" font-size="16" font-weight="800" fill="var(--ink)">' + hm(d) + '</text><text x="48" y="66" text-anchor="middle" font-size="11" fill="var(--mute)">of 8h</text></svg>' +
      '<div class="ln-main"><h2>Last night</h2><span class="t">' + fmt(tod(ln.bed)) + ' – ' + fmt(tod(ln.wake)) + '</span>' +
      '<span class="d ' + cls(diff) + '">' + (Math.abs(diff) < 5 ? "Right on your goal" : dshort(diff) + (diff > 0 ? " under your goal" : " over your goal")) + '</span>' +
      '<span class="sm">' + (ln.segs.length > 1 ? "In " + ln.segs.length + " parts, about " : "About ") + (d / 90).toFixed(1) + ' sleep cycles</span>' +
      (ln.q ? '<span class="stars" aria-label="Rated ' + (Math.round(ln.q * 10) / 10) + ' out of 5">' + "★★★★★".slice(0, Math.round(ln.q)) + '<span style="opacity:.3">' + "★★★★★".slice(Math.round(ln.q)) + '</span></span>' : '') + '</div></div>';
    if (ln.unrated) h += '<div class="rate"><p>How did you sleep?</p>' + [1, 2, 3, 4, 5].map(q => '<button type="button" data-rate="' + q + '" aria-label="Rate ' + q + ' out of 5">' + q + '</button>').join("") + '</div>';
  } else if (!M.pend) {
    h = '<h2>Last night</h2><p class="sm" style="margin-bottom:12px">Not logged yet.</p><button class="btn tonal" type="button" data-fill="' + t + '">Log last night</button>';
  }
  $("lastnight").innerHTML = h; $("lastnight").hidden = !h;
  const miss = missingNights();
  $("missing").hidden = !miss.length;
  $("missChips").innerHTML = miss.map(W => '<button class="chip" type="button" data-fill="' + W + '" aria-label="Log the night of ' + esc(nightName(W)) + '">' + esc(nightName(W)) + '</button>').join("");
  const d14 = []; for (let i = 14; i >= 1; i--) d14.push(addD(t, -i));
  const st = stats(d14); st.miss = [...M.MN].filter(W => W > addD(t, -14)).length;
  if (st.n) { $("debt").textContent = hm(st.bal); $("debt").className = cls(st.bal); $("debtlbl").textContent = st.bal > 0 ? "sleep debt" : "ahead of your goal"; }
  else { $("debt").textContent = "–"; $("debt").className = ""; $("debtlbl").textContent = M.start ? "No complete days yet" : "Log your first night to start"; }
  $("debtstats").innerHTML = st.n ? mstat(hm(st.tot / st.n), "average per day") + mstat(st.met + " of " + st.n, "days at goal") + (st.miss ? mstat(String(st.miss), nn(st.miss), "warn") : "") : "";
  mini(d14);
  renderReminderCard();
}
function mini(days) {
  const W = 320, H = 64, pb = 16, pt = 4, n = days.length, slot = W / n, bw = Math.min(14, slot - 6), max = Math.max(600, ...days.map(d => M.byDay[d] || 0));
  const y = v => H - pb - (Math.min(v, max) / max) * (H - pb - pt);
  let s = '<line x1="0" x2="' + W + '" y1="' + y(NEED) + '" y2="' + y(NEED) + '" stroke="var(--good)" stroke-width="1.2" stroke-dasharray="4 3"/>';
  days.forEach((d, i) => {
    const cx = slot * i + slot / 2, st = M.status(d), v = M.byDay[d] || 0;
    if (st === "done") s += '<rect x="' + (cx - bw / 2) + '" y="' + y(v) + '" width="' + bw + '" height="' + Math.max(2, H - pb - y(v)) + '" rx="3" fill="' + (v >= NEED ? "var(--good)" : "var(--acc)") + '"><title>' + esc(dayFull(d)) + ': ' + hm(v) + '</title></rect>';
    else if (st === "missing") s += '<rect x="' + (cx - 4) + '" y="' + (H - pb - 3) + '" width="8" height="3" rx="1.5" fill="var(--warn)"/>';
    if (i === 0 || i === n - 1) s += '<text x="' + (i === 0 ? 0 : W) + '" y="' + (H - 2) + '" font-size="10" text-anchor="' + (i === 0 ? "start" : "end") + '" fill="var(--mute)">' + esc(dayShort(d)) + '</text>';
  });
  $("mini").innerHTML = s;
}

/* ---------- trends ---------- */
const monthName = p => new Date(+p.slice(0, 4), +p.slice(5, 7) - 1, 1).toLocaleDateString("en-GB", { month: "long", year: "numeric" });
const addM = (p, n) => { const d = new Date(+p.slice(0, 4), +p.slice(5, 7) - 1 + n, 1); return d.getFullYear() + "-" + pad(d.getMonth() + 1); };
const weekName = p => dayShort(p) + " – " + dayShort(addD(p, 6));
const monthDays = p => { const n = new Date(+p.slice(0, 4), +p.slice(5, 7), 0).getDate(); return seq(p + "-01", n); };
const VIEWS = {
  m: { nav: 1, title: monthName, step: addM, cur: t => t.slice(0, 7), first: s => s.slice(0, 7), days: monthDays, H: 184, wk: 0, all: "months", now: "This month", aria: "Sleep each day of the selected month against the 8 hour goal" },
  w: { nav: 1, title: weekName, step: (p, n) => addD(p, 7 * n), cur: wkStart, first: wkStart, days: p => seq(p, 7), H: 200, wk: 1, all: "weeks", now: "This week", aria: "Sleep each day of the selected week against the 8 hour goal" },
  r: { nav: 0, title: p => dayShort(p) + " – " + dayShort(addD(p, 2)), cur: t => addD(t, -2), days: p => seq(p, 3), H: 200, wk: 1, aria: "Sleep on each of the last 3 days against the 8 hour goal" }
};
const CW = 320, CL = 30, CR = 10;
function mono(Pt) { // monotone cubic: smooth, never overshoots the real values
  const n = Pt.length, dx = [], m = [], t = [];
  for (let i = 0; i < n - 1; i++) { dx[i] = Pt[i + 1][0] - Pt[i][0]; m[i] = (Pt[i + 1][1] - Pt[i][1]) / dx[i]; }
  t[0] = m[0]; t[n - 1] = m[n - 2];
  for (let i = 1; i < n - 1; i++) t[i] = m[i - 1] * m[i] <= 0 ? 0 : (m[i - 1] + m[i]) / 2;
  for (let i = 0; i < n - 1; i++) { if (m[i] === 0) { t[i] = 0; t[i + 1] = 0; continue; } const a = t[i] / m[i], b = t[i + 1] / m[i], h = a * a + b * b; if (h > 9) { const k = 3 / Math.sqrt(h); t[i] = k * a * m[i]; t[i + 1] = k * b * m[i]; } }
  let d = "M" + Pt[0][0] + "," + Pt[0][1];
  for (let i = 0; i < n - 1; i++) { const h = dx[i] / 3; d += " C" + (Pt[i][0] + h) + "," + (Pt[i][1] + t[i] * h) + " " + (Pt[i + 1][0] - h) + "," + (Pt[i + 1][1] - t[i + 1] * h) + " " + Pt[i + 1][0] + "," + Pt[i + 1][1]; }
  return d;
}
function draw(c, days) {
  const svg = $("tchart"), t = M.t, N = days.length, wk = c.wk, W = CW, H = c.H, pl = CL, pr = CR, pt = 18, pb = wk ? 32 : 22;
  svg.setAttribute("viewBox", "0 0 " + W + " " + H); svg.setAttribute("aria-label", c.aria);
  const pts = days.map(d => ({ d, s: M.status(d), v: M.byDay[d] || 0 })), vals = pts.filter(p => p.s === "done").map(p => p.v);
  const lo = vals.length ? Math.min(240, Math.floor(Math.min(...vals) / 120) * 120) : 240, hi = Math.max(600, Math.ceil((Math.max(0, ...vals) + 30) / 120) * 120);
  const X = i => pl + 8 + (W - pl - pr - 16) * i / (N - 1), Y = v => H - pb - (v - lo) / (hi - lo) * (H - pb - pt), gy = Y(NEED), base = H - pb;
  let s = '<defs><clipPath id="cd"><rect x="0" y="' + gy + '" width="' + W + '" height="' + (H - gy) + '"/></clipPath><clipPath id="cs"><rect x="0" y="0" width="' + W + '" height="' + gy + '"/></clipPath></defs>';
  for (let g = lo; g <= hi; g += 120) {
    if (g === NEED) continue;
    s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + Y(g) + '" y2="' + Y(g) + '" stroke="' + (g === lo ? "var(--line)" : "var(--grid)") + '"/><text x="' + (pl - 5) + '" y="' + (Y(g) + 3) + '" font-size="8.5" text-anchor="end" fill="var(--mute)">' + (g / 60) + 'h</text>';
  }
  const runs = []; let r = [];
  pts.forEach((p, i) => { if (p.s === "done") r.push([X(i), Y(p.v)]); else { if (r.length) runs.push(r); r = []; } }); if (r.length) runs.push(r);
  runs.forEach(R => {
    if (R.length === 1) { const q = R[0]; s += '<line x1="' + q[0] + '" x2="' + q[0] + '" y1="' + gy + '" y2="' + q[1] + '" stroke="' + (q[1] > gy ? "var(--bad)" : "var(--good)") + '" stroke-width="3" stroke-opacity=".35"/>'; return; }
    const area = mono(R) + " L" + R[R.length - 1][0] + "," + gy + " L" + R[0][0] + "," + gy + " Z";
    s += '<path d="' + area + '" fill="var(--bad-soft)" clip-path="url(#cd)"/><path d="' + area + '" fill="var(--good-soft)" clip-path="url(#cs)"/>';
  });
  s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + gy + '" y2="' + gy + '" stroke="var(--good)" stroke-width="1.5" stroke-dasharray="5 4"/><text x="' + (pl - 5) + '" y="' + (gy + 3) + '" font-size="8.5" text-anchor="end" fill="var(--mute)">8h</text>';
  if (pick >= 0) s += '<line x1="' + X(pick) + '" x2="' + X(pick) + '" y1="' + pt + '" y2="' + base + '" stroke="var(--mute)" stroke-dasharray="2 3" stroke-opacity=".7"/>';
  runs.forEach(R => { if (R.length > 1) s += '<path d="' + mono(R) + '" fill="none" stroke="var(--acc)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>'; });
  const ti = days.indexOf(t);
  pts.forEach((p, i) => {
    const x = X(i);
    if (p.s === "done") {
      const y = Y(p.v), on = i === pick;
      s += '<circle cx="' + x + '" cy="' + y + '" r="' + ((wk ? 3.5 : 2.6) + (on ? 1.5 : 0)) + '" fill="var(--acc)" stroke="var(--card)" stroke-width="' + (on ? 2 : 0) + '"/>';
      if (wk) { let ly = p.v >= NEED ? y - 8 : y + 15; if (ly > base - 3) ly = y - 8; if (ly < pt - 4) ly = y + 15; s += '<text x="' + x + '" y="' + ly + '" font-size="9" text-anchor="middle" fill="var(--ink)">' + (p.v / 60).toFixed(1) + '</text>'; }
    }
    if (M.MN.has(p.d)) {
      const step = (W - pl - pr - 16) / (N - 1), nx = i > 0 ? (X(i - 1) + X(i)) / 2 : Math.max(pl + 3, X(0) - step / 2), w = wk ? 6 : 2;
      s += '<line x1="' + (nx - w) + '" x2="' + (nx + w) + '" y1="' + (base - 3) + '" y2="' + (base - 3) + '" stroke="var(--warn)" stroke-width="3" stroke-linecap="round"/>';
    }
    const isT = p.d === t, fc = isT ? "var(--ink)" : "var(--mute)", fw = isT ? ' font-weight="800"' : '', op = p.s === "future" ? ' fill-opacity=".55"' : '';
    if (wk) s += '<text x="' + x + '" y="' + (H - 18) + '" font-size="8.5" text-anchor="middle" fill="' + fc + '"' + fw + op + '>' + fdate(p.d, { weekday: "short" }) + '</text><text x="' + x + '" y="' + (H - 6) + '" font-size="8.5" text-anchor="middle" fill="' + fc + '"' + fw + op + '>' + dobj(p.d).getDate() + '</text>';
    else { const dn = i + 1; if (isT || ((dn === 1 || dn % 5 === 0) && !(ti >= 0 && Math.abs(i - ti) <= 1))) s += '<text x="' + x + '" y="' + (H - 7) + '" font-size="8.5" text-anchor="middle" fill="' + fc + '"' + fw + '>' + dn + '</text>'; }
  });
  svg.innerHTML = s;
}
function prow(c, p, st) {
  const on = sel[tab] === p, tag = p === c.cur(M.t) ? c.now : "";
  return '<button type="button" class="prow' + (on ? " sel" : "") + '" data-p="' + esc(p) + '" aria-pressed="' + on + '"><span class="rmain"><span class="blk"><b>' + esc(c.title(p)) + '</b>' + (tag ? '<span class="tag">' + tag + '</span>' : '') + '</span>' +
    '<span class="blk sm">' + (st.n ? 'Avg ' + hm(st.tot / st.n) + ' a day, goal met ' + st.met + ' of ' + st.n + ' days' : 'No complete days') + (st.miss ? ', <span class="warn">' + st.miss + ' ' + nn(st.miss) + '</span>' : '') + '</span></span>' +
    '<span class="rend">' + (st.n ? '<b class="blk ' + cls(st.bal) + '">' + hm(st.bal) + '</b><span class="blk sm">' + (st.bal > 0 ? "sleep debt" : "ahead") + '</span>' : '') + '</span></button>';
}
function renderTrends() {
  document.querySelectorAll(".seg [role=tab]").forEach(b => { const on = b.dataset.tab === tab; b.setAttribute("aria-selected", String(on)); b.tabIndex = on ? 0 : -1; });
  $("tpanel").setAttribute("aria-labelledby", "tab-" + tab);
  const c = VIEWS[tab], t = M.t;
  $("tempty").hidden = !!M.start; $("tbody").hidden = !M.start; if (!M.start) return;
  let p;
  if (c.nav) {
    const first = c.first(M.start), last = c.cur(t);
    if (!sel[tab] || sel[tab] > last) sel[tab] = last; if (sel[tab] < first) sel[tab] = first;
    p = sel[tab]; $("tprev").disabled = p <= first; $("tnext").disabled = p >= last;
    $("tprev").setAttribute("aria-label", "Previous " + (tab === "m" ? "month" : "week")); $("tnext").setAttribute("aria-label", "Next " + (tab === "m" ? "month" : "week"));
  } else p = c.cur(t);
  $("tprev").hidden = $("tnext").hidden = !c.nav;
  const days = c.days(p), st = stats(days); tN = days.length; if (pick >= tN) pick = -1;
  $("ttitle").textContent = c.title(p);
  $("tbal").innerHTML = st.n ? '<b class="' + cls(st.bal) + '">' + hm(st.bal) + '</b><span>' + (st.bal > 0 ? "sleep debt" : "ahead of goal") + '</span>' : "";
  if (pick >= 0) {
    const d = days[pick], s = M.status(d), v = M.byDay[d] || 0; let x = '<b>' + esc(d === t ? "Today" : dayFull(d)) + '</b>: ';
    if (s === "done") x += hm(v) + ', <span class="' + cls(NEED - v) + '">' + (Math.abs(NEED - v) < 5 ? "right on your goal" : dshort(NEED - v) + (NEED - v > 0 ? " under your goal" : " over your goal")) + '</span>';
    else if (s === "open") x += v ? hm(v) + " so far" : "day not finished";
    else if (s === "missing") { const W = M.MN.has(d) ? d : M.MN.has(addD(d, 1)) ? addD(d, 1) : null; x += '<span class="warn">incomplete</span>' + (W ? ', the night ' + esc(nightName(W)) + ' isn’t logged. <button class="btn text" style="min-height:0;padding:2px 4px" type="button" data-fill="' + W + '">Log it</button>' : ''); }
    else if (s === "future") x += "upcoming"; else x += "before you started tracking";
    $("tread").innerHTML = '<p class="day">' + x + '</p>';
  } else $("tread").innerHTML = st.n || st.miss ? '<div class="mstats">' + mstat(st.n ? hm(st.tot / st.n) : "–", "average per day") + mstat(st.met + " of " + st.n, "days at goal") + (st.miss ? mstat(String(st.miss), nn(st.miss), "warn") : "") + '</div>' : '<p class="sm day">No complete days yet.</p>';
  $("tlegend").innerHTML = '<span><i class="lg-line"></i>Actual</span><span><i class="lg-goal"></i>Goal 8h</span><span><i class="lg-def"></i>Deficit</span><span><i class="lg-sur"></i>Surplus</span>' + (st.miss ? '<span><i class="lg-miss"></i>Night not logged</span>' : '');
  draw(c, days);
  if (c.nav) {
    const per = [], first = c.first(M.start);
    for (let q = c.cur(t); q >= first; q = c.step(q, -1)) { const s2 = stats(c.days(q)); if (s2.n || s2.miss) per.push([q, s2]); }
    $("tall").hidden = !per.length; $("tall").textContent = (listOpen ? "Hide all " : "Show all ") + c.all; $("tall").setAttribute("aria-expanded", String(listOpen));
    $("tlist").hidden = !listOpen; $("tlist").innerHTML = listOpen ? per.map(x => prow(c, x[0], x[1])).join("") : "";
  } else { $("tall").hidden = true; $("tlist").hidden = true; $("tlist").innerHTML = ""; }
}

/* ---------- patterns ---------- */
const cell = (label, big, sub, best) => '<div class="cell' + (best ? " best" : "") + '"><div class="k">' + label + '</div><b>' + big + '</b><div class="sm">' + sub + '</div></div>';
function steady(v, label) {
  const c = v <= 30 ? "good" : v <= 60 ? "warn" : "bad", w = v <= 30 ? "Steady" : v <= 60 ? "Varies" : "Irregular";
  return '<div class="stat"><b class="' + c + '">' + w + '</b><span>' + label + ' varies ±' + dshort(v) + '</span></div>';
}
function renderPatterns() {
  const t = M.t, recent = k => M.nights.filter(N => N.W > addD(t, -k) && N.W <= t);
  const n14 = recent(14);
  if (n14.length < 3) $("timing").innerHTML = '<p class="sm" style="grid-column:1/3">Appears after 3 nights are logged.</p>';
  else {
    const bed = n14.map(N => adj(tod(N.bed))), wake = n14.map(N => tod(N.wake));
    $("timing").innerHTML = '<div class="stat"><b>' + fmt(med(bed)) + '</b><span>Usual bedtime</span></div><div class="stat"><b>' + fmt(med(wake)) + '</b><span>Usual wake-up</span></div>' + steady(sd(bed), "Bedtime") + steady(sd(wake), "Wake-up");
  }
  $("wkend").value = S.prefs.weekend;
  const wd = WEEKENDS[S.prefs.weekend] || WEEKENDS["fri-sat"], isWE = N => wd.includes(dobj(N.W).getDay());
  const n28 = recent(28), A = n28.filter(N => !isWE(N)), B = n28.filter(isWE);
  if (A.length < 2 || B.length < 2) $("weekend").innerHTML = '<p class="sm">Needs 2 workday and 2 weekend nights from the last 4 weeks.</p>';
  else {
    const sum = G => ({ b: med(G.map(N => adj(tod(N.bed)))), w: med(G.map(N => tod(N.wake))), d: mean(G.map(N => N.mins)), mid: mean(G.map(N => adj(tod(N.bed)) + dur(N) / 2)), n: G.length });
    const a = sum(A), b = sum(B), sh = b.mid - a.mid;
    $("weekend").innerHTML = '<div class="cmp">' + cell("Workdays", fmt(a.b) + " – " + fmt(a.w), hm(a.d) + " average, " + a.n + " nights") + cell("Weekend", fmt(b.b) + " – " + fmt(b.w), hm(b.d) + " average, " + b.n + " nights") + '</div>' +
      '<p class="sm">' + (Math.abs(sh) < 15 ? "Your weekend timing matches your workdays." : 'Your weekend sleep runs <b class="' + (Math.abs(sh) >= 60 ? "warn" : "") + '">' + dshort(sh) + ' ' + (sh > 0 ? "later" : "earlier") + '</b> than on workdays.') + '</p>';
  }
  const R = recent(60).filter(N => N.q);
  if (!R.length) { $("quality").innerHTML = '<p class="sm">Rate your nights to see what goes with good sleep.</p>'; return; }
  const ub = mean(R.map(N => adj(tod(N.bed))));
  let h = '<p class="lead" style="margin-top:-6px">Average <b style="color:var(--ink)">★ ' + mean(R.map(N => N.q)).toFixed(1) + '</b> from ' + R.length + ' rated night' + (R.length > 1 ? "s" : "") + ' in the last 60</p>', any = false;
  [["Bedtime", N => adj(tod(N.bed)) < 1440, "Before 12 AM", "After 12 AM"],
   ["Night length", N => N.mins >= NEED, "8h or more", "Under 8h"],
   ["Bedtime compared with your usual " + fmt(ub), N => Math.abs(adj(tod(N.bed)) - ub) <= 30, "Within 30 min", "Off by more"]
  ].forEach(([lab, f, ya, na]) => {
    const Y = R.filter(f), Nn = R.filter(N => !f(N)); if (Y.length < 3 || Nn.length < 3) return;
    const qa = mean(Y.map(N => N.q)), qb = mean(Nn.map(N => N.q)); any = true;
    h += '<p class="cmp-l">' + esc(lab) + '</p><div class="cmp">' + cell(ya, "★ " + qa.toFixed(1), Y.length + " nights", qa - qb >= .3) + cell(na, "★ " + qb.toFixed(1), Nn.length + " nights", qb - qa >= .3) + '</div>';
  });
  if (!any) h += '<p class="sm">Comparisons appear once there are 3 rated nights on each side.</p>';
  $("quality").innerHTML = h;
}

/* ---------- schedule ---------- */
function diary() {
  const t = M.t, rows = []; for (let i = 0; i < 14; i++) rows.push(addD(t, -i));
  const W = 320, x0 = 46, x1 = W - 34, rowH = 14, gap = 6, top = 24, H = top + rows.length * (rowH + gap) + 2, X = m => x0 + (x1 - x0) * Math.max(0, Math.min(1200, m)) / 1200;
  let s = '';
  if (P) { const a = P.target - 1080, b = P.wake + 360; s += '<rect x="' + X(a) + '" y="' + (top - 5) + '" width="' + Math.max(0, X(b) - X(a)) + '" height="' + (H - top + 3) + '" rx="6" fill="var(--acc-soft)"/>'; }
  [[0, "6 PM"], [180, ""], [360, "12 AM"], [540, ""], [720, "6 AM"], [900, ""], [1080, "12 PM"]].forEach(([m, l]) => { s += '<line x1="' + X(m) + '" x2="' + X(m) + '" y1="' + (top - 5) + '" y2="' + (H - 2) + '" stroke="var(--line)" stroke-opacity="' + (l ? 1 : .5) + '"/>' + (l ? '<text x="' + X(m) + '" y="12" font-size="9.5" text-anchor="middle" fill="var(--mute)">' + l + '</text>' : ''); });
  rows.forEach((Wd, i) => {
    const y = top + i * (rowH + gap), ws = ts(addD(Wd, -1) + "T18:00"), we = ws + 1200 * 60000, N = M.nightW.get(Wd), miss = M.MN.has(Wd), isT = Wd === t;
    s += '<text x="0" y="' + (y + rowH - 3) + '" font-size="10" fill="' + (miss ? "var(--warn)" : isT ? "var(--ink)" : "var(--mute)") + '"' + (isT ? ' font-weight="800"' : '') + '>' + fdate(Wd, { weekday: "short" }) + ' ' + dobj(Wd).getDate() + '</text>';
    M.es.forEach(e => {
      const a = Math.max(ts(e.bed), ws), b = Math.min(ts(e.wake), we); if (b <= a) return; const nap = !isNight(e), xa = X((a - ws) / 60000), xb = X((b - ws) / 60000);
      s += '<rect x="' + xa + '" y="' + (y + (nap ? 4 : 0)) + '" width="' + Math.max(2, xb - xa) + '" height="' + (nap ? rowH - 8 : rowH) + '" rx="3" fill="var(--acc)"' + (nap ? ' fill-opacity=".5"' : '') + '><title>' + esc(fmt(tod(e.bed)) + " – " + fmt(tod(e.wake))) + '</title></rect>';
    });
    if (M.pend) { const a = Math.max(ts(M.pend), ws), b = Math.min(Date.now(), we); if (b > a) { const xa = X((a - ws) / 60000), xb = X((b - ws) / 60000); s += '<rect x="' + xa + '" y="' + (y + 1) + '" width="' + Math.max(2, xb - xa) + '" height="' + (rowH - 2) + '" rx="3" fill="none" stroke="var(--acc)" stroke-width="1.5" stroke-dasharray="3 2"/>'; } }
    if (miss) s += '<text x="' + ((x0 + x1) / 2) + '" y="' + (y + rowH - 3) + '" font-size="10" text-anchor="middle" fill="var(--warn)">Not logged</text>';
    if (N && P) { const dl = adj(tod(N.bed)) - P.target, ok = Math.abs(dl) <= 30; s += '<text x="' + W + '" y="' + (y + rowH - 3) + '" font-size="10" text-anchor="end" font-weight="800" fill="' + (ok ? "var(--good)" : "var(--mute)") + '">' + (ok ? "✓" : (dl > 0 ? "+" : "−") + dtiny(dl)) + '</text>'; }
  });
  return '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="Sleep on each of the last 14 nights, shown from 6 PM to 2 PM, with your planned sleep window shaded">' + s + '</svg>';
}
function renderSchedule() {
  const t = M.t;
  if (!P) {
    $("schSky").innerHTML = '<div class="lbl">Your bedtime</div><div class="big sm2">Not set yet</div><div class="meta">Log at least 3 nights, or set your wake-up time below. Your bedtime appears here.</div>';
    $("schPlan").hidden = true;
  } else {
    $("schSky").innerHTML = '<div class="lbl">Your bedtime</div><div class="big">' + fmt(P.target) + '</div><div class="meta">Wake up at <b>' + fmt(P.wake) + '</b> after <b>8 hours</b> of sleep, about ' + P.tcyc + ' sleep cycles.</div>' + strip(true) +
      (P.steps ? '<div class="meta">Tonight, aim for <b>' + fmt(P.tonight) + '</b>. You’ll get to ' + fmt(P.target) + ' 15 minutes at a time.</div>' : '');
    let h = '<h2>' + (P.steps ? "Getting to your bedtime" : "Keep it steady") + '</h2>';
    h += '<p class="lead" style="margin-top:-4px">Your target is <b style="color:var(--ink)">' + fmt(P.target) + '</b>: 8 hours of sleep plus ' + P.lat + ' minutes to fall asleep before your ' + fmt(P.wake) + ' wake-up.</p>';
    if (P.steps) {
      const shown = Math.min(P.steps, 3); let li = '<li><span class="dot"></span><span>Tonight</span><b>' + fmt(P.tonight) + '</b></li>';
      for (let k = 1; k <= shown; k++) { const last = k === P.steps, tm = last ? P.target : P.tonight - P.dir * 15 * k; li += '<li><span class="dot ' + (last ? "end" : "hollow") + '"></span><span>' + (last ? "Target, from about " : "From about ") + esc(dayFull(addD(t, 4 * k))) + '</span><b>' + fmt(tm) + '</b></li>'; }
      if (P.steps > shown) li += '<li><span class="dot end"></span><span>Target, from about ' + esc(dayFull(addD(t, 4 * P.steps))) + '</span><b>' + fmt(P.target) + '</b></li>';
      h += '<ol class="steps">' + li + '</ol><p class="sm" style="margin-top:6px">Each step is 15 minutes ' + (P.dir > 0 ? "earlier" : "later") + '. The next step comes once your usual bedtime catches up, about every 4 nights.</p>';
    } else h += '<p>Your usual bedtime is already close to your target. Going to bed at the same time every night matters most now.</p>';
    h += '<div class="note">Wind down from <b>' + fmt(P.tonight - 30) + '</b>, 30 minutes before bed.</div>';
    const splitN = P.n14.filter(N => N.segs.length > 1).length;
    if (splitN >= 3) h += '<div class="note">' + splitN + ' of your last 14 nights were split into parts. One block from ' + fmt(P.target) + ' to ' + fmt(P.wake) + ' fits your plan.</div>';
    const wd = WEEKENDS[S.prefs.weekend] || WEEKENDS["fri-sat"], n28 = M.nights.filter(N => N.W > addD(t, -28) && N.W <= t), B = n28.filter(N => wd.includes(dobj(N.W).getDay()));
    if (B.length >= 2) { const bw = med(B.map(N => tod(N.wake))); if (bw - P.wake > 60) h += '<div class="note">On weekends you wake up around ' + fmt(bw) + '. Keeping weekend wake-ups within an hour of ' + fmt(P.wake) + ' makes the plan easier to keep.</div>'; }
    $("schPlan").innerHTML = h; $("schPlan").hidden = false;
  }
  if (M.nights.length) {
    $("schDiary").innerHTML = '<h2>Your last 14 nights</h2>' + (P && P.n7 ? '<p class="lead" style="margin-top:-4px">In bed within 30 minutes of ' + fmt(P.target) + ' on ' + P.onT + ' of your last ' + P.n7 + ' nights.</p>' : '') +
      '<div class="legend" style="margin-top:0"><span><i class="lg-sleep"></i>Sleep</span><span><i class="lg-nap"></i>Nap</span>' + (P ? '<span><i class="lg-plan"></i>Planned sleep</span>' : '') + '</div>' + diary();
    $("schDiary").hidden = false;
  } else $("schDiary").hidden = true;
  const fx = !!S.prefs.wake;
  $("wkAuto").setAttribute("aria-pressed", String(!fx)); $("wkSet").setAttribute("aria-pressed", String(fx));
  $("wkTime").hidden = !fx; if (fx && document.activeElement !== $("wkTime")) $("wkTime").value = S.prefs.wake;
  const n14 = M.nights.filter(N => N.W > addD(t, -14) && N.W <= t), aw = n14.length >= 3 ? r5(med(n14.map(N => tod(N.wake)))) : null;
  $("wkNote").textContent = fx ? "Your plan is built around this time every day." : aw != null ? "Uses your usual wake-up from the last 14 nights, now " + fmt(aw) + "." : "Log 3 nights to find your usual wake-up time, or set one.";
  $("latSel").value = String(S.prefs.lat);
}

/* ---------- history ---------- */
const histWeeks = () => [...new Set(M.es.map(e => wkStart(e.wake.slice(0, 10))))].sort().reverse(); // weeks that have entries, newest first
function renderHistory() {
  const rev = M.es.slice().reverse(), weeks = histWeeks();
  if (!rev.length) { $("histcard").innerHTML = '<h2>No entries yet</h2><p class="sm">Tap Add entry to log a night, or use Going to sleep on Today.</p>'; return; }
  if (!hWeek || !weeks.includes(hWeek)) hWeek = weeks[0];
  const i = weeks.indexOf(hWeek), list = rev.filter(e => wkStart(e.wake.slice(0, 10)) === hWeek), tot = list.reduce((a, e) => a + dur(e), 0);
  const head = '<div class="phead" style="margin:0 0 6px"><button class="nav-btn" type="button" id="hprev" aria-label="Earlier week"' + (i >= weeks.length - 1 ? ' disabled' : '') + '>‹</button><b class="ptitle">' + esc(weekName(hWeek)) + '</b><button class="nav-btn" type="button" id="hnext" aria-label="Later week"' + (i <= 0 ? ' disabled' : '') + '>›</button><div class="pbal"><b>' + hm(tot) + '</b><span>' + list.length + (list.length > 1 ? " entries" : " entry") + '</span></div></div>';
  $("histcard").innerHTML = head + list.map(e => {
    const d = dur(e), sp = split(e), nap = !isNight(e), bd = e.bed.slice(0, 10), wd = e.wake.slice(0, 10);
    return '<div class="hrow' + (edit === e.id ? " editing" : "") + '"><button class="hmain" type="button" data-edit="' + esc(e.id) + '" aria-label="Edit sleep from ' + esc(when(e.bed)) + '">' +
      '<span class="dur">' + hm(d) + (nap ? '<span class="tag">Nap</span>' : '') + '</span><span class="q">' + (e.q ? '★ ' + e.q : '<span class="sm">Not rated</span>') + '</span>' +
      '<span class="sm">' + esc(dayFull(bd)) + ', ' + fmt(tod(e.bed)) + ' – ' + (wd !== bd ? esc(dayFull(wd)) + ', ' : '') + fmt(tod(e.wake)) + '</span><span class="cy">' + (d / 90).toFixed(1) + ' cycles</span>' +
      (sp.length > 1 ? '<span class="sm full">Counts as ' + sp.map(p => hm(p[1]) + ' on ' + esc(dayShort(p[0]))).join(' and ') + '</span>' : '') +
      '</button><button class="del" type="button" data-del="' + esc(e.id) + '" aria-label="Delete sleep from ' + esc(when(e.bed)) + '">✕</button></div>';
  }).join("");
}
function render() { M = model(); P = plan(); lastDay = M.t; renderToday(); renderTrends(); renderPatterns(); renderSchedule(); renderHistory(); queueReminders(); }

/* ---------- navigation ---------- */
function setView(v, keepScroll) {
  if (!VNAMES[v]) v = "today"; view = v;
  document.querySelectorAll("main .view").forEach(s => { s.hidden = s.id !== "v-" + v; });
  document.querySelectorAll(".bnav button").forEach(b => { if (b.dataset.view === v) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current"); });
  $("abTitle").textContent = VNAMES[v];
  $("abSub").hidden = v !== "today"; $("abSub").textContent = fdate(today(), { weekday: "long", day: "numeric", month: "long" });
  $("fab").hidden = v !== "history"; document.body.classList.toggle("has-fab", v === "history");
  try { localStorage.setItem(LS_VIEW, v); } catch (e) { /* ignore */ }
  try { history.replaceState(null, "", "#" + v); } catch (e) { /* ignore */ }
  if (!keepScroll) window.scrollTo(0, 0);
}
document.querySelectorAll(".bnav button").forEach(b => b.addEventListener("click", () => setView(b.dataset.view)));
function startView() { try { const h = (location.hash || "").slice(1), v = localStorage.getItem(LS_VIEW); return VNAMES[h] ? h : VNAMES[v] ? v : "today"; } catch (e) { return "today"; } }

/* ---------- sheets & form ---------- */
function openSheet(id) { const d = $(id); if (d.open) return; try { d.showModal(); } catch (e) { d.setAttribute("open", ""); } }
function closeSheet(id) { const d = $(id); if (!d.open) return; try { d.close(); } catch (e) { d.removeAttribute("open"); } }
["entrySheet", "setSheet"].forEach(id => $(id).addEventListener("click", ev => { if (ev.target === $(id)) closeSheet(id); }));
$("entrySheet").addEventListener("close", () => { if (edit) { edit = null; setMode(); renderHistory(); } });
$("setSheet").addEventListener("close", () => { $("delBox").hidden = true; });
function cur() { return { bed: $("bd").value + "T" + $("bt").value, wake: $("wd").value + "T" + $("wt").value, q: +$("q").value || 0 }; }
function overlap(e, except) { const a = ts(e.bed), b = ts(e.wake); return act().find(x => x.id !== except && ts(x.bed) < b && ts(x.wake) > a); }
function calc() {
  const e = cur(), d = dur(e); let msg = "", ok = false;
  if (!isNaN(d)) {
    if (!valid(e)) msg = '<span class="bad">Wake-up must be after bedtime and within 24 hours.</span>';
    else if (future(e)) msg = '<span class="sm">Wake-up time is still ahead.</span>';
    else { const o = overlap(e, edit); if (o) msg = '<span class="bad">This overlaps your sleep from ' + esc(when(o.bed)) + '. Edit that entry instead.</span>'; else { ok = true; msg = 'Sleep time <b>' + hm(d) + '</b>, about ' + (d / 90).toFixed(1) + ' cycles'; } }
  }
  if (fresh && !ok) msg = '<span class="sm">Set the times for this entry.</span>';
  $("save").disabled = !ok; $("calc").innerHTML = msg;
}
function setBedDate() { const wd = $("wd").value; if (wd) $("bd").value = $("bt").value > $("wt").value ? addD(wd, -1) : wd; }
function fillTimes(W, q) { const u = usual(); $("bt").value = u.bt; $("wt").value = u.wt; $("wd").value = W; bdManual = false; setBedDate(); $("q").value = q; }
function resetForm() {
  const t = M.t, m = missingNights(); fresh = false;
  if (!M.nightW.has(t) && !M.pend) fillTimes(t, "3");
  else if (m.length) fillTimes(m[m.length - 1], "3");
  else { // everything is logged: start from the last hour, like a nap
    const end = new Date(Math.floor(Date.now() / 300000) * 300000), st = new Date(end.getTime() - 3600000), hh = d => pad(d.getHours()) + ":" + pad(d.getMinutes());
    $("bt").value = hh(st); $("bd").value = dstr(st); $("wt").value = hh(end); $("wd").value = dstr(end); bdManual = true; $("q").value = "3"; fresh = true;
  }
  calc();
}
function setMode() { const on = !!edit; $("logtitle").textContent = on ? "Edit sleep" : "Add sleep"; $("save").textContent = on ? "Update" : "Save"; $("delEntry").hidden = !on; }
function openAdd() { edit = null; setMode(); resetForm(); openSheet("entrySheet"); }
function prefill(W) { fresh = false; edit = null; setMode(); fillTimes(W, "3"); calc(); openSheet("entrySheet"); }
function loadForm(e) { fresh = false; edit = null; setMode(); $("bt").value = e.bed.slice(11); $("bd").value = e.bed.slice(0, 10); $("wt").value = e.wake.slice(11); $("wd").value = e.wake.slice(0, 10); $("q").value = "0"; bdManual = true; calc(); openSheet("entrySheet"); }
function startEdit(id) {
  const e = act().find(x => x.id === id); if (!e) return;
  fresh = false; edit = id; setMode();
  $("bt").value = e.bed.slice(11); $("bd").value = e.bed.slice(0, 10); $("wt").value = e.wake.slice(11); $("wd").value = e.wake.slice(0, 10); $("q").value = String(e.q || 0);
  bdManual = true; calc(); renderHistory(); openSheet("entrySheet");
}
function toast(msg, fn) {
  clearTimeout(toastT); $("tmsg").textContent = msg; undoFn = fn || null; $("tundo").hidden = !fn; $("toast").hidden = false;
  toastT = setTimeout(() => { $("toast").hidden = true; undoFn = null; }, fn ? 6000 : 3000);
}
function delEntry(id) {
  const e = S.entries.find(x => x.id === id && !x.del); if (!e) return;
  put({ id, del: 1, t: Date.now() });
  if (edit === id) { edit = null; setMode(); closeSheet("entrySheet"); }
  commit();
  toast("Entry deleted", () => { put({ id: e.id, bed: e.bed, wake: e.wake, q: e.q, t: Date.now() }); commit(); toast("Entry restored"); });
}

/* ---------- actions ---------- */
function goSleep() { S.pending = { bed: nowStr(), t: Date.now() }; commit(); toast("Sleep started at " + fmt(tod(S.pending.bed))); }
function cancelSleep() { S.pending = { bed: null, t: Date.now() }; commit(); toast("Cancelled"); }
function wakeUp() {
  const bed = S.pending.bed; if (!bed) return;
  const e = { id: rid(), bed, wake: nowStr(), q: 0, t: Date.now() }, d = dur(e);
  S.pending = { bed: null, t: Date.now() };
  if (d < 10) { commit(); toast("Under 10 minutes, so nothing was logged"); return; }
  if (!valid(e) || overlap(e, null)) { commit(); loadForm(e); toast("Check these times, then save"); return; }
  put(e); commit(); toast("Logged " + hm(d));
}
function rate(q) { const ln = M.nightW.get(M.t); if (!ln) return; const now = Date.now(); ln.segs.filter(e => !e.q).forEach(e => put({ id: e.id, bed: e.bed, wake: e.wake, q, t: now })); commit(); toast("Rated " + q + " out of 5"); }

$("save").onclick = () => {
  const e = cur(); if (!valid(e) || future(e) || overlap(e, edit)) return;
  const was = !!edit; put({ id: edit || rid(), bed: e.bed, wake: e.wake, q: e.q, t: Date.now() });
  edit = null; setMode(); closeSheet("entrySheet"); commit(); resetForm(); toast(was ? "Entry updated" : "Entry saved");
};
$("cancel").onclick = () => closeSheet("entrySheet");
$("closeEntry").onclick = () => closeSheet("entrySheet");
$("delEntry").onclick = () => { if (edit) delEntry(edit); };
$("now").onclick = () => { fresh = false; const n = new Date(); $("wt").value = pad(n.getHours()) + ":" + pad(n.getMinutes()); $("wd").value = dstr(n); if (!bdManual) setBedDate(); calc(); };
["bt", "wt", "wd"].forEach(i => $(i).addEventListener("change", () => { if (!bdManual) { setBedDate(); calc(); } }));
$("bd").addEventListener("input", () => { bdManual = true; });
["bt", "bd", "wt", "wd", "q"].forEach(i => $(i).addEventListener("input", () => { fresh = false; calc(); }));
$("fab").onclick = openAdd;
$("v-today").addEventListener("click", ev => {
  const b = ev.target.closest("button"); if (!b) return;
  if (b.id === "tosleep") goSleep(); else if (b.id === "awake") wakeUp(); else if (b.id === "pcancel") cancelSleep(); else if (b.dataset.rate) rate(+b.dataset.rate);
});
document.addEventListener("click", ev => {
  const t = ev.target.closest && ev.target.closest("[data-fill],[data-go]"); if (!t) return;
  if (t.dataset.fill) prefill(t.dataset.fill); else setView(t.dataset.go);
});
$("histcard").addEventListener("click", ev => {
  const b = ev.target.closest("button"); if (!b) return;
  if (b.id === "hprev" || b.id === "hnext") { const w = histWeeks(), j = w.indexOf(hWeek) + (b.id === "hprev" ? 1 : -1); if (w[j]) { hWeek = w[j]; renderHistory(); const n = $(b.id); if (n && !n.disabled) n.focus(); } return; }
  if (b.dataset.edit) { startEdit(b.dataset.edit); return; }
  if (b.dataset.del) delEntry(b.dataset.del);
});
$("tundo").onclick = () => { const f = undoFn; undoFn = null; $("toast").hidden = true; if (f) f(); };

/* trends interactions */
function setTab(k, focus) { tab = k; pick = -1; listOpen = false; try { localStorage.setItem(LS_TAB, k); } catch (e) { /* ignore */ } renderTrends(); if (focus) $("tab-" + k).focus(); }
document.querySelectorAll(".seg [role=tab]").forEach(b => b.addEventListener("click", () => setTab(b.dataset.tab)));
document.querySelector("[role=tablist]").addEventListener("keydown", ev => {
  const order = ["m", "w", "r"], i = order.indexOf(tab);
  if (ev.key === "ArrowRight") { ev.preventDefault(); setTab(order[(i + 1) % 3], true); } else if (ev.key === "ArrowLeft") { ev.preventDefault(); setTab(order[(i + 2) % 3], true); }
});
$("tprev").onclick = () => { const c = VIEWS[tab]; sel[tab] = c.step(sel[tab], -1); pick = -1; renderTrends(); };
$("tnext").onclick = () => { const c = VIEWS[tab]; sel[tab] = c.step(sel[tab], 1); pick = -1; renderTrends(); };
$("tall").onclick = () => { listOpen = !listOpen; renderTrends(); };
$("tlist").addEventListener("click", ev => { const b = ev.target.closest("button.prow"); if (!b) return; sel[tab] = b.dataset.p; pick = -1; renderTrends(); $("tab-" + tab).scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "center" }); });
(function () {
  const svg = $("tchart");
  const at = ev => { if (tN < 2) return -1; const b = svg.getBoundingClientRect(), x = (ev.clientX - b.left) / b.width * CW; return Math.max(0, Math.min(tN - 1, Math.round((x - CL - 8) / ((CW - CL - CR - 16) / (tN - 1))))); };
  svg.addEventListener("click", ev => { const i = at(ev); if (i >= 0) { pick = i; renderTrends(); } });
  svg.addEventListener("pointermove", ev => { if (ev.pointerType !== "mouse") return; const i = at(ev); if (i >= 0 && i !== pick) { pick = i; renderTrends(); } });
  svg.addEventListener("pointerleave", ev => { if (ev.pointerType === "mouse" && pick >= 0) { pick = -1; renderTrends(); } });
})();
$("wkend").addEventListener("change", () => { const v = $("wkend").value; if (WEEKENDS[v]) { S.prefs = { ...S.prefs, weekend: v, t: Date.now() }; commit(); } });

/* schedule settings */
$("wkAuto").onclick = () => { if (S.prefs.wake) { S.prefs = { ...S.prefs, wake: null, t: Date.now() }; commit(); toast("Wake-up set to automatic"); } };
$("wkSet").onclick = () => { if (!S.prefs.wake) { const aw = P && P.autoWake != null ? P.autoWake : 420; S.prefs = { ...S.prefs, wake: hhmm(aw), t: Date.now() }; commit(); } $("wkTime").focus(); };
$("wkTime").addEventListener("change", () => { const v = $("wkTime").value; if (HM.test(v) && v !== S.prefs.wake) { S.prefs = { ...S.prefs, wake: v, t: Date.now() }; commit(); toast("Wake-up set to " + fmt(hmin(v))); } });
$("latSel").addEventListener("change", () => { const v = +$("latSel").value; if (LATS.includes(v) && v !== S.prefs.lat) { S.prefs = { ...S.prefs, lat: v, t: Date.now() }; commit(); } });

/* ---------- reminders (Android notifications) ----------
   Reminders are set per phone and re-planned whenever your data or schedule changes:
   wind-down 30 minutes before tonight's bedtime, bedtime, and a morning reminder an hour after wake-up
   when last night isn't logged yet. The next 7 days are scheduled so they still arrive if the app stays closed. */
function remPrefs() {
  try { const r = JSON.parse(localStorage.getItem(LS_REM) || "null"); if (r && typeof r === "object") return { wind: !!r.wind, bed: !!r.bed, morning: !!r.morning, asked: !!r.asked }; } catch (e) { /* ignore */ }
  return { wind: false, bed: false, morning: false, asked: false };
}
function saveRem(p) { try { localStorage.setItem(LS_REM, JSON.stringify(p)); } catch (e) { /* ignore */ } renderReminderUI(); renderReminderCard(); queueReminders(); }
const atClock = (dayOffset, minutes) => { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), n.getDate() + dayOffset, Math.floor(minutes / 60), minutes % 60, 0, 0); };
function reminderList() {
  const r = remPrefs(), out = []; if (!P || !(r.wind || r.bed || r.morning)) return out;
  const now = Date.now(), bm = mod(P.tonight);
  let d0 = atClock(0, bm).getTime() <= now ? 1 : 0;
  if (M.pend && atClock(d0, bm).getTime() - now < 12 * 3600000) d0++; // asleep already: skip tonight's nudges
  for (let i = 0; i < 7; i++) {
    const b = atClock(d0 + i, bm);
    if (r.wind) out.push({ id: 100 + i, kind: "wind", title: "Wind down", body: "Bedtime is at " + fmt(bm) + ". Time to start winding down.", at: new Date(b.getTime() - 30 * 60000) });
    if (r.bed) out.push({ id: 110 + i, kind: "bed", type: "BEDTIME", title: "Time for bed", body: "It's " + fmt(bm) + ". Tap Going to sleep when you lie down.", at: b });
  }
  if (r.morning) {
    const wm = mod(P.wake + 60), m0 = (atClock(0, wm).getTime() <= now || M.nightW.has(M.t)) ? 1 : 0;
    for (let i = 0; i < 7; i++) out.push({ id: 120 + i, kind: "morning", type: "MORNING", title: "How did you sleep?", body: "Log last night's sleep.", at: atClock(m0 + i, wm) });
  }
  return out.filter(n => n.at.getTime() > now + 60000);
}
let remTimer = 0;
function queueReminders() {
  if (!platform.native || !ready) return;
  clearTimeout(remTimer);
  remTimer = setTimeout(() => { platform.scheduleReminders(reminderList()).catch(() => { /* permission revoked */ }); }, 1200);
}
async function setReminder(key, on) {
  const p = remPrefs();
  if (on) {
    const st = await platform.askNotifications();
    if (st !== "granted") { toast("Notifications are blocked for Sleep Tracker. Allow them in Android Settings."); renderReminderUI(); return; }
  }
  p[key] = on; p.asked = true; saveRem(p);
}
async function renderReminderUI() {
  $("remField").hidden = !platform.native;
  if (!platform.native) return;
  const p = remPrefs();
  [["swWind", "wind"], ["swBed", "bed"], ["swMorning", "morning"]].forEach(([id, k]) => $(id).setAttribute("aria-checked", String(p[k])));
  const st = await platform.notificationState();
  $("remNote").textContent = st === "denied" && (p.wind || p.bed || p.morning) ? "Notifications are blocked for Sleep Tracker. Allow them in Android Settings to get reminders." : !P ? "Reminders start once your bedtime is set." : "";
}
function renderReminderCard() {
  const p = remPrefs();
  $("remCard").hidden = !(platform.native && ready && !p.asked && !p.wind && !p.bed && !p.morning);
}
[["swWind", "wind"], ["swBed", "bed"], ["swMorning", "morning"]].forEach(([id, k]) => { $(id).onclick = () => setReminder(k, $(id).getAttribute("aria-checked") !== "true"); });
$("remOn").onclick = async () => {
  const st = await platform.askNotifications();
  const p = remPrefs(); p.asked = true;
  if (st === "granted") { p.wind = p.bed = p.morning = true; saveRem(p); toast("Reminders are on. Change them in Settings."); }
  else { saveRem(p); toast("Notifications are blocked. You can allow them later in Android Settings."); }
};
$("remLater").onclick = () => { const p = remPrefs(); p.asked = true; saveRem(p); };
function onNotification(actionId, extra) {
  if (!ready) { queuedActions.push([actionId, extra]); return; }
  const kind = extra && extra.kind;
  if (actionId === "sleep") { setView("today"); if (!S.pending.bed) goSleep(); return; }
  if (actionId === "log" || kind === "morning") { setView("today"); if (!M.nightW.has(M.t) && !M.pend) prefill(M.t); return; }
  setView("today");
}

/* ---------- settings: account, links, backup ---------- */
function renderAccount() {
  if (!backend.configured) { $("acctInfo").textContent = "Accounts aren't set up in this build. Your entries stay on this phone."; $("acctBtns").hidden = true; return; }
  $("acctBtns").hidden = !account;
  if (!account) { $("acctInfo").textContent = ""; return; }
  const google = account.providers.includes("google.com");
  $("acctInfo").textContent = "Signed in as " + (account.email || account.name || "your account") + (google ? " with Google" : "") + ".";
}
$("openSettings").onclick = () => { status(); renderAccount(); renderReminderUI(); openSheet("setSheet"); };
$("closeSet").onclick = () => closeSheet("setSheet");
$("signOutBtn").onclick = async () => {
  if (syncState === "pending" || syncState === "err" || writing) { toast("Connect to the internet first, so your latest entries reach your account."); return; }
  const old = uid; closeSheet("setSheet"); stopSync();
  try { await backend.signOut(); } catch (e) { /* signed out locally anyway */ }
  clearLocal(old); platform.scheduleReminders([]).catch(() => {});
};
$("delAcct").onclick = () => {
  $("delBox").hidden = false; $("delErr").textContent = "";
  const pw = account && account.providers.includes("password");
  $("delAuth").innerHTML = pw ? '<label for="delPass">Enter your password to confirm</label><input id="delPass" type="password" autocomplete="current-password">' : '<p class="sm">You’ll confirm with your Google account.</p>';
  $("delBox").scrollIntoView({ block: "nearest" });
};
$("delCancel").onclick = () => { $("delBox").hidden = true; };
let delBusy = false;
$("delGo").onclick = async () => {
  if (delBusy) return; delBusy = true; $("delGo").disabled = true; $("delErr").textContent = "";
  const old = uid;
  try {
    const pwEl = $("delPass"); await backend.reauth(pwEl ? pwEl.value : undefined);
    stopSync();
    await backend.deleteAccount();
    clearLocal(old); platform.scheduleReminders([]).catch(() => {});
    closeSheet("setSheet"); toast("Your account and sleep data were deleted.");
  } catch (e) {
    $("delErr").textContent = backend.message(e) || "Deletion was cancelled.";
    if (uid === old && !live) resumeSync();
  } finally { delBusy = false; $("delGo").disabled = false; }
};
function setLink(id, url) { const a = $(id); a.href = url; if (!platform.native) { a.target = "_blank"; a.rel = "noopener"; } }
setLink("aPrivacy", links.privacy); setLink("lnkPrivacy", links.privacy); setLink("lnkDelete", links.deletion);
$("ver").textContent = "Version " + appVersion;

function csv() {
  const rows = [["bed_date", "bed_time", "wake_date", "wake_time", "minutes", "quality"]];
  act().sort((a, b) => ts(a.bed) - ts(b.bed)).forEach(e => rows.push([e.bed.slice(0, 10), e.bed.slice(11), e.wake.slice(0, 10), e.wake.slice(11), dur(e), e.q || ""]));
  return rows.map(r => r.join(",")).join("\n") + "\n";
}
function showCsv(data) { const ta = $("csvout"); ta.value = data; ta.hidden = false; ta.focus(); ta.select(); toast("Select all the text below and copy it"); }
$("exp").onclick = async () => {
  if (!act().length) { toast("Nothing to export yet"); return; }
  const data = csv(), name = "sleep-log-" + today() + ".csv";
  if (platform.native) { try { await platform.shareFile(name, data); } catch (e) { if (!/cancel/i.test(String(e && e.message))) showCsv(data); } return; }
  try { const url = URL.createObjectURL(new Blob([data], { type: "text/csv" })), a = document.createElement("a"); a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 2000); toast("Exported"); }
  catch (e) { showCsv(data); }
};
// Android file pickers often label CSV files as plain text or unknown, so the phone shows every file
if (platform.native) $("impfile").accept = "*/*";
$("imp").onclick = () => $("impfile").click();
$("impfile").addEventListener("change", () => {
  const f = $("impfile").files && $("impfile").files[0]; if (!f) return;
  const r = new FileReader();
  r.onload = () => { importCsv(String(r.result || "")); $("impfile").value = ""; };
  r.onerror = () => { toast("Couldn't read that file"); $("impfile").value = ""; };
  r.readAsText(f);
});
function importCsv(txt) {
  const lines = txt.replace(/^\uFEFF/, "").split(/\r?\n/).filter(l => l.trim());
  if (lines.length < 2) { toast("That file has no entries"); return; }
  const cells = l => l.split(",").map(s => s.trim().replace(/^"|"$/g, ""));
  const head = cells(lines[0].toLowerCase()), ix = n => head.indexOf(n);
  const bi = ix("bed_date"), bti = ix("bed_time"), wi = ix("wake_date"), wti = ix("wake_time"), qi = ix("quality");
  if (bi < 0 || bti < 0 || wi < 0 || wti < 0) { toast("Columns not recognised. Use a CSV exported from Sleep Tracker."); return; }
  const tm = s => { const m = /^(\d{1,2}):(\d\d)$/.exec(s || ""); return m ? pad(+m[1]) + ":" + m[2] : ""; };
  let add = 0, skip = 0; const now = Date.now();
  lines.slice(1).forEach(l => {
    const c = cells(l), q = qi >= 0 ? +c[qi] : 0;
    const e = { id: rid(), bed: (c[bi] || "") + "T" + tm(c[bti]), wake: (c[wi] || "") + "T" + tm(c[wti]), q: q >= 1 && q <= 5 ? Math.round(q) : 0, t: now };
    if (!valid(e) || future(e) || overlap(e, null)) { skip++; return; }
    put(e); add++;
  });
  if (add) commit();
  toast(add ? "Imported " + add + (skip ? ", skipped " + skip + " already logged or invalid" : "") : "Nothing new to import");
}

/* ---------- sign in ---------- */
let authMode = "in";
function setAuthMode(m) {
  authMode = m;
  $("aSubmit").textContent = m === "in" ? "Sign in" : "Create account";
  $("aMode").textContent = m === "in" ? "Create an account" : "I already have an account";
  $("aForgot").hidden = m !== "in";
  $("aPass").setAttribute("autocomplete", m === "in" ? "current-password" : "new-password");
  $("aErr").textContent = "";
}
async function busy(btn, fn) {
  btn.disabled = true; $("aErr").textContent = "";
  try { await fn(); } catch (e) { $("aErr").textContent = backend.message(e); } finally { btn.disabled = false; }
}
$("aSubmit").onclick = () => busy($("aSubmit"), async () => {
  const email = $("aEmail").value.trim(), pw = $("aPass").value;
  if (!email) throw { code: "auth/missing-email" };
  if (!pw) throw { code: "auth/missing-password" };
  if (authMode === "in") await backend.signInEmail(email, pw); else await backend.signUpEmail(email, pw);
});
$("aPass").addEventListener("keydown", ev => { if (ev.key === "Enter") $("aSubmit").click(); });
$("aGoogle").onclick = () => busy($("aGoogle"), () => backend.signInGoogle());
$("aForgot").onclick = () => busy($("aForgot"), async () => {
  const email = $("aEmail").value.trim(); if (!email) throw { code: "auth/missing-email" };
  await backend.resetPassword(email); toast("If there's an account for " + email + ", a reset email is on its way.");
});
$("aMode").onclick = () => setAuthMode(authMode === "in" ? "up" : "in");
$("aShow").onclick = () => { const p = $("aPass"), show = p.type === "password"; p.type = show ? "text" : "password"; $("aShow").textContent = show ? "Hide" : "Show"; $("aShow").setAttribute("aria-label", show ? "Hide password" : "Show password"); };

/* ---------- sessions ---------- */
function showAuth() { $("boot").hidden = true; $("appShell").hidden = true; $("auth").hidden = false; document.body.classList.add("signed-out"); setAuthMode(authMode); }
function showApp() { $("boot").hidden = true; $("auth").hidden = true; $("appShell").hidden = false; document.body.classList.remove("signed-out"); }
function stopSync() { if (unsub) { try { unsub(); } catch (e) { /* ignore */ } } unsub = null; live = false; }
function resumeSync() { if (!uid) return; live = true; unsub = backend.subscribe(uid, onServer, () => { syncState = "err"; status(); }); }
function startSession(user) {
  account = user; uid = user ? user.uid : null;
  S = loadLocal(); serverCanon = null; writing = false; again = false; syncState = user ? "pending" : "local";
  hWeek = null; edit = null; pick = -1; ready = true;
  render(); resetForm(); showApp(); setView(startView(), true); status(); renderAccount(); renderReminderUI();
  if (user) resumeSync();
  while (queuedActions.length) { const [a, x] = queuedActions.shift(); onNotification(a, x); }
}
function endSession() { stopSync(); ready = false; uid = null; account = null; S = blank(); serverCanon = null; writing = false; again = false; }

/* ---------- keep "today" current ---------- */
function tick() {
  if (!ready) return;
  if (today() !== lastDay) { pick = -1; render(); if (!$("entrySheet").open) resetForm(); setView(view, true); }
  else { renderTonight(); if (view === "schedule") renderSchedule(); }
}
setInterval(tick, 30000);
document.addEventListener("visibilitychange", () => { if (!document.hidden) { tick(); push(); } });
window.addEventListener("online", push);
platform.onResume(() => { tick(); push(); queueReminders(); });
platform.onBack(() => {
  if ($("entrySheet").open) { closeSheet("entrySheet"); return; }
  if ($("setSheet").open) { closeSheet("setSheet"); return; }
  if (!$("appShell").hidden && view !== "today") { setView("today"); return; }
  platform.exitApp();
});
platform.setupNotifications(onNotification);

/* ---------- start ---------- */
try { const v = localStorage.getItem(LS_TAB); if (VIEWS[v]) tab = v; } catch (e) { /* ignore */ }
if (!backend.configured) startSession(null);
else {
  let resolved = false;
  setTimeout(() => { if (!resolved) showAuth(); }, 8000);
  backend.init(user => {
    resolved = true;
    if (user) { if (uid !== user.uid) { if (uid) endSession(); startSession(user); } else { account = user; renderAccount(); } }
    else { if (ready) endSession(); showAuth(); }
  });
}
