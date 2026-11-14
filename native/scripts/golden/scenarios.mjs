// The accounts the reference results are made from: generated sleep histories, plus hand-made edge cases
// (day and night boundaries, odd or broken data, daylight saving changes, goals, the bedtime plan, CSV).
// Times are local wall-clock times in each scenario's zone, exactly as the app stores them.

const day = (k, n) => new Date(Date.UTC(+k.slice(0, 4), +k.slice(5, 7) - 1, +k.slice(8, 10) + n)).toISOString().slice(0, 10);
const at = (dt, n) => new Date(Date.UTC(+dt.slice(0, 4), +dt.slice(5, 7) - 1, +dt.slice(8, 10), +dt.slice(11, 13), +dt.slice(14, 16) + n)).toISOString().slice(0, 16);
const wday = k => new Date(Date.UTC(+k.slice(0, 4), +k.slice(5, 7) - 1, +k.slice(8, 10))).getUTCDay();
const T0 = 1767225600000; // 1 Jan 2026, a base for "last changed" times

function rng(seed) { // mulberry32
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

// A sleep history ending at `now`: one night a day (sometimes missed, sometimes split in two), some naps
function history(seed, o) {
  const r = rng(seed), out = [], today = o.now.slice(0, 10), step = o.round ?? 5;
  let n = 0;
  const add = (bed, wake) => {
    if (wake > o.now) return;
    const e = { id: "g" + seed.toString(36) + "-" + (n++).toString(36) + Math.floor(r() * 46656).toString(36), bed, wake };
    const x = r();
    if (x < (o.pRate ?? 0.5)) e.q = 1 + Math.floor(r() * 5); else if (x < 0.8) e.q = 0;
    e.t = T0 + out.length * 3600000 + Math.floor(r() * 1000);
    out.push(e);
  };
  for (let i = o.days; i >= 0; i--) {
    const W = day(today, -i);
    if (i === 0 && o.lastNight === false) continue;
    if (i > 0 && r() < (o.pMiss ?? 0.08)) continue;
    if (o.gap && i <= o.gap[0] && i >= o.gap[1]) continue;
    const we = (o.weekendDays || [5, 6]).includes(wday(W));
    const bedMin = Math.round(((o.bed ?? 1395) + (r() - 0.5) * (o.spread ?? 90) + (we ? (o.weekendShift ?? 60) : 0)) / step) * step;
    const bed = at(day(W, -1) + "T00:00", bedMin);
    const len = Math.round(((o.len ?? 440) + (r() - 0.5) * (o.lenSpread ?? 120)) / step) * step;
    if (r() < (o.pSplit ?? 0.1)) {
      const a = Math.round(len * (0.3 + r() * 0.4)), gap = 10 + Math.round(r() * 50);
      add(bed, at(bed, a)); add(at(bed, a + gap), at(bed, len + gap));
    } else add(bed, at(bed, len));
    if (r() < (o.pNap ?? 0.1)) { const nb = at(W + "T00:00", 780 + Math.round(r() * 300)); add(nb, at(nb, 15 + Math.round(r() * 75))); }
  }
  return out;
}

const prefs = (p = {}) => ({ weekend: "fri-sat", wake: null, lat: 15, born: null, goals: [], t: T0 + 5000, ...p });
const goal = (from, min, on = from === "0000-01-01" ? "2026-01-05" : from) => ({ from, min, on });
const E = (id, bed, wake, q = 0, t = T0) => ({ id, bed, wake, q, t });
const ALL_ON = { wind: true, bed: true, morning: true, asked: true };

export function scenarios() {
  const list = [];
  const add = (name, zone, now, doc, extra = {}) => list.push({ name, zone, now, doc, ...extra });
  const hist = (seed, now, o = {}) => history(seed, { now, days: 40, ...o });

  /* text formats */
  const mins = [-1500, -721, -90.5, -60, -59.5, -30, -0.5, -0.4, 0, 0.4, 0.5, 1, 4.5, 5, 7.5, 29.5, 59, 59.5, 60, 61, 90, 125.5, 600, 719, 720, 721, 1402.5, 1439, 1439.5, 1440, 1441, 2160, 2879.5, 3000];
  for (let m = 0; m <= 2900; m += 37) mins.push(m);
  const goals = [241, 449, 496, 1, 59, 60, 61, 90, 125];
  for (let m = 240; m <= 720; m += 5) goals.push(m);
  const days = ["2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20", "2026-09-05", "2026-09-30",
    "2026-12-31", "2027-01-01", "2028-02-29", "2028-03-01", "2026-01-01", "2026-02-28", "2026-03-01", "2026-05-31", "2026-06-01", "2026-07-04", "2026-08-31", "2026-10-25"];
  const fixed = [0, 0.05, 0.15, 0.25, 0.35, 0.45, 1.25, 1.45, 2.675, 4.444444, 5.55, 6.666666666666667, 7.75, 9.95, 99.95, 1 / 3, 2 / 3, 4.35, 0.95, 5.05, 3.25, 3.75, 4.85];
  add("formats", "Asia/Dhaka", "2026-11-14T21:00", null, {
    formats: {
      mins, goals, days, fixed,
      whens: ["2026-11-14T23:05", "2026-01-01T00:00", "2026-09-06T12:30", "2027-12-31T12:00"],
      lists: [[1], [1, 2], [3, 1, 2], [1440, 1500, 1380, 1410], [5, 5, 5, 5], [1, 2, 3, 4, 5, 6, 7, 8, 9, 10.5], [1395, 1402, 1388], [420.5, 380, 445, 410, 405]]
    }
  });

  /* no data */
  add("empty, no document", "Asia/Dhaka", "2026-11-14T21:00", null, { reminders: ALL_ON });
  add("empty document, morning", "Asia/Dhaka", "2026-11-14T09:00", {}, { checks: [["", "", "", "", null]] });

  /* typical histories */
  add("dhaka 120 days, evening", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: history(1, { now: "2026-11-14T21:00", days: 120, pNap: 0.15, pSplit: 0.12 }),
    pending: { bed: null, t: T0 + 10 },
    prefs: prefs({ born: 1994, goals: [goal("0000-01-01", 480), goal("2026-10-10", 465)] })
  }, { reminders: ALL_ON });
  add("dhaka, morning, last night not logged", "Asia/Dhaka", "2026-11-14T08:30", {
    entries: hist(2, "2026-11-14T08:30", { lastNight: false }), prefs: prefs({ born: 1988, goals: [goal("0000-01-01", 450)] })
  }, { reminders: { wind: false, bed: true, morning: true } });
  add("dhaka, morning, last night logged", "Asia/Dhaka", "2026-11-14T08:30", { entries: hist(3, "2026-11-14T08:30"), prefs: prefs() }, { reminders: { wind: true, bed: false, morning: true } });
  add("dhaka, exactly noon", "Asia/Dhaka", "2026-11-14T12:00", { entries: hist(3, "2026-11-14T12:00", { lastNight: false }), prefs: prefs() });
  add("dhaka, 11:59", "Asia/Dhaka", "2026-11-14T11:59", { entries: hist(3, "2026-11-14T11:59", { lastNight: false }), prefs: prefs() }, { reminders: ALL_ON });
  add("dhaka, long gap", "Asia/Dhaka", "2026-11-14T22:00", { entries: hist(4, "2026-11-14T22:00", { days: 70, gap: [40, 21] }), prefs: prefs({ born: 1979 }) });
  add("dhaka, ratings on most nights", "Asia/Dhaka", "2026-11-14T20:00", { entries: hist(5, "2026-11-14T20:00", { days: 75, pRate: 0.95, spread: 150, lenSpread: 180 }), prefs: prefs({ born: 2001, goals: [goal("0000-01-01", 510)] }) });
  add("dhaka, late weekends", "Asia/Dhaka", "2026-11-14T20:00", { entries: hist(6, "2026-11-14T20:00", { days: 35, weekendShift: 120, pRate: 0.7 }), prefs: prefs({ born: 1999 }) });
  add("dhaka, sat-sun weekend", "Asia/Dhaka", "2026-11-14T20:00", { entries: hist(7, "2026-11-14T20:00", { days: 35, weekendDays: [6, 0], weekendShift: 90 }), prefs: prefs({ weekend: "sat-sun", born: 1985 }) });
  add("dhaka, friday weekend", "Asia/Dhaka", "2026-11-14T20:00", { entries: hist(8, "2026-11-14T20:00", { days: 35, weekendDays: [5], weekendShift: 150 }), prefs: prefs({ weekend: "fri", born: 1985 }) });
  add("kathmandu, 6 AM", "Asia/Kathmandu", "2026-11-14T06:00", { entries: hist(9, "2026-11-14T06:00", { days: 30, round: 1, lastNight: false }), prefs: prefs({ born: 1970, lat: 30 }) }, { reminders: ALL_ON });

  /* sleep in progress */
  const base = hist(10, "2026-11-14T21:00", { days: 20 });
  add("asleep since 11:10 PM", "Asia/Dhaka", "2026-11-15T02:00", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs({ born: 1990 }) }, { reminders: ALL_ON });
  add("asleep 5 minutes", "Asia/Dhaka", "2026-11-14T23:15", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs() }, { reminders: ALL_ON });
  add("asleep from 4 minutes ahead", "Asia/Dhaka", "2026-11-14T23:06", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs() });
  add("asleep from 5 minutes ahead", "Asia/Dhaka", "2026-11-14T23:05", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs() }, { reminders: ALL_ON });
  add("asleep from 6 minutes ahead", "Asia/Dhaka", "2026-11-14T23:04", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs() }, { reminders: ALL_ON });
  const steady = []; for (let i = 10; i >= 1; i--) steady.push(E("st" + i, day("2026-11-15", -i - 1) + "T22:45", day("2026-11-15", -i) + "T07:00", 3, T0 + i));
  add("asleep, bedtime 11 hours 15 minutes away", "Asia/Dhaka", "2026-11-15T11:30", { entries: steady, pending: { bed: "2026-11-14T23:00", t: T0 + 9e6 }, prefs: prefs({ wake: "07:00" }) }, { reminders: ALL_ON });
  add("asleep from 2 hours ahead", "Asia/Dhaka", "2026-11-14T21:10", { entries: base, pending: { bed: "2026-11-14T23:10", t: T0 + 9e6 }, prefs: prefs() }, { reminders: ALL_ON });
  add("asleep since 3 days ago", "Asia/Dhaka", "2026-11-17T07:00", { entries: base, pending: { bed: "2026-11-14T22:00", t: T0 + 9e6 }, prefs: prefs() });
  add("asleep since the afternoon", "Asia/Dhaka", "2026-11-14T16:00", { entries: base, pending: { bed: "2026-11-14T15:00", t: T0 + 9e6 }, prefs: prefs() });
  add("asleep, overlapping an entry", "Asia/Dhaka", "2026-11-14T07:30", { entries: base.concat([E("over", "2026-11-14T05:00", "2026-11-14T06:30", 3, T0 + 5)]), pending: { bed: "2026-11-13T23:30", t: T0 + 9e6 }, prefs: prefs() });
  add("pending saved without a time", "Asia/Dhaka", "2026-11-14T23:30", { entries: base, pending: { bed: "2026-11-14T23:10", t: 0 }, prefs: prefs() });
  add("pending not a valid time", "Asia/Dhaka", "2026-11-14T23:30", { entries: base, pending: { bed: "2026-11-14T25:10", t: 5 }, prefs: prefs() });

  /* boundaries of the night and the day */
  add("night and nap boundaries", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: [
      E("b1", "2026-11-01T11:59", "2026-11-01T12:30"), E("b2", "2026-11-02T12:00", "2026-11-02T13:00", 2), E("b3", "2026-11-03T18:59", "2026-11-03T20:00"),
      E("b4", "2026-11-04T19:00", "2026-11-05T03:00", 5), E("b5", "2026-11-06T00:00", "2026-11-06T08:00"), E("b6", "2026-11-06T23:59", "2026-11-07T07:00", 1),
      E("b7", "2026-11-08T10:00", "2026-11-08T11:30"), E("b8", "2026-11-08T22:00", "2026-11-09T06:00", 4), E("b9", "2026-11-10T00:00", "2026-11-11T00:00"),
      E("b10", "2026-11-11T23:30", "2026-11-12T23:30"), E("b11", "2026-11-13T21:00", "2026-11-14T05:00", 3), E("b12", "2026-11-14T13:00", "2026-11-14T13:45")
    ], prefs: prefs({ born: 2000 })
  }, { reminders: ALL_ON });
  add("split nights", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: [
      E("s1", "2026-11-08T22:30", "2026-11-09T02:00", 4), E("s2", "2026-11-09T02:40", "2026-11-09T06:30", 2),
      E("s3", "2026-11-09T23:00", "2026-11-10T01:00"), E("s4", "2026-11-10T01:30", "2026-11-10T04:00", 3), E("s5", "2026-11-10T05:00", "2026-11-10T08:00"),
      E("s6", "2026-11-10T23:30", "2026-11-11T07:00", 5), E("s7", "2026-11-11T22:00", "2026-11-12T03:00"), E("s8", "2026-11-12T04:00", "2026-11-12T07:30"),
      E("s9", "2026-11-12T23:00", "2026-11-13T06:00", 3), E("s10", "2026-11-13T09:00", "2026-11-13T11:00", 1), E("s11", "2026-11-13T22:45", "2026-11-14T03:15", 2), E("s12", "2026-11-14T03:45", "2026-11-14T06:45")
    ], prefs: prefs({ born: 1960, wake: "06:45" })
  }, { reminders: ALL_ON });
  add("only naps", "Asia/Dhaka", "2026-11-14T21:00", { entries: [E("n1", "2026-11-10T14:00", "2026-11-10T15:00", 3), E("n2", "2026-11-12T13:00", "2026-11-12T14:30"), E("n3", "2026-11-14T15:00", "2026-11-14T15:20")], prefs: prefs() }, { reminders: ALL_ON });
  add("entry ending in the future", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(11, "2026-11-14T21:00", { days: 15 }).concat([E("fut", "2026-11-14T22:00", "2026-11-15T06:00", 0, T0 + 99)]), prefs: prefs() });
  add("overlapping entries", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: [E("o1", "2026-11-11T23:00", "2026-11-12T07:00", 3), E("o2", "2026-11-12T05:00", "2026-11-12T09:00", 4), E("o3", "2026-11-12T23:00", "2026-11-13T07:00"), E("o4", "2026-11-13T23:00", "2026-11-14T06:00", 2)],
    prefs: prefs()
  }, {
    checks: [["2026-11-11", "23:30", "2026-11-12", "06:00", null], ["2026-11-11", "23:30", "2026-11-12", "06:00", "o1"], ["2026-11-13", "23:00", "2026-11-14", "06:00", "o4"],
      ["2026-11-14", "22:00", "2026-11-15", "06:00", null], ["2026-11-14", "20:00", "2026-11-14", "20:59", null], ["2026-11-14", "21:00", "2026-11-14", "21:01", null],
      ["2026-11-14", "23:00", "2026-11-14", "22:00", null], ["2026-11-13", "06:00", "2026-11-14", "06:01", null], ["2026-02-30", "23:00", "2026-03-01", "07:00", null],
      ["2026-11-1", "23:00", "2026-11-12", "07:00", null], ["2026-11-14", "", "2026-11-14", "10:00", null], ["0050-01-01", "23:00", "0050-01-02", "07:00", null], ["1000-01-05", "23:00", "1000-01-06", "07:00", null],
      ["0999-01-05", "23:00", "0999-01-06", "07:00", null]]
  });

  /* broken and odd data, as it might arrive from old versions or other devices */
  add("invalid and legacy entries", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: [
      E("ok1", "2026-11-10T23:00", "2026-11-11T07:00", 4, 5), { id: "ok1", del: 1, t: 4 },
      E("wake-before-bed", "2026-11-11T23:00", "2026-11-11T22:00"), E("zero", "2026-11-11T23:00", "2026-11-11T23:00"),
      E("too-long", "2026-11-01T06:00", "2026-11-02T06:01"), E("24h", "2026-11-03T00:00", "2026-11-04T00:00"),
      E("feb30", "2026-02-30T23:00", "2026-03-01T07:00"), E("month13", "2026-13-01T23:00", "2026-13-02T07:00"), E("h24", "2026-11-05T24:00", "2026-11-06T07:00"),
      E("m60", "2026-11-05T23:60", "2026-11-06T07:00"), E("y999", "0999-11-05T23:00", "0999-11-06T07:00"), E("y50", "0050-11-05T23:00", "0050-11-06T07:00"),
      E("space", " 2026-11-06T23:00", "2026-11-07T07:00"), E("lower-t", "2026-11-06t23:00", "2026-11-07t07:00"),
      E("seconds", "2026-11-06T23:00:00", "2026-11-07T07:00:00"), E("slash", "2026/11/06T23:00", "2026/11/07T07:00"),
      { bed: "2026-11-07T23:10", wake: "2026-11-08T06:40", q: 3, t: 9 }, { id: "", bed: "2026-11-08T23:10", wake: "2026-11-09T06:40", t: 9 },
      { id: 42, bed: "2026-11-09T23:10", wake: "2026-11-10T06:10", t: 9 },
      { d: "2026-11-12", bed: "23:30", wake: "06:30", q: 2 }, { d: "2026-11-13", bed: "01:00", wake: "07:15" }, { d: "2026-11-13", bed: "1:00", wake: "07:15" },
      { d: "2026-02-30", bed: "23:30", wake: "06:30" }, { d: "", bed: "2026-10-01T23:00", wake: "2026-10-02T07:00", id: "d-empty" },
      { id: "q-string", bed: "2026-10-02T23:00", wake: "2026-10-03T07:00", q: "4", t: 3 }, { id: "q-frac", bed: "2026-10-03T23:00", wake: "2026-10-04T07:00", q: 3.6, t: 3 },
      { id: "q-half", bed: "2026-10-04T23:00", wake: "2026-10-05T07:00", q: 2.5, t: 3 }, { id: "q-6", bed: "2026-10-05T23:00", wake: "2026-10-06T07:00", q: 6, t: 3 },
      { id: "q-neg", bed: "2026-10-06T23:00", wake: "2026-10-07T07:00", q: -1, t: 3 }, { id: "q-true", bed: "2026-10-07T23:00", wake: "2026-10-08T07:00", q: true, t: 3 },
      { id: "q-x", bed: "2026-10-08T23:00", wake: "2026-10-09T07:00", q: "x", t: 3 }, { id: "q-null", bed: "2026-10-09T23:00", wake: "2026-10-10T07:00", q: null, t: 3 },
      { id: "q-0.5", bed: "2026-10-10T23:00", wake: "2026-10-11T07:00", q: 0.99, t: 3 }, { id: "q-5.4", bed: "2026-10-11T23:00", wake: "2026-10-12T07:00", q: 5.4, t: 3 },
      { id: "q-hex", bed: "2026-10-12T23:00", wake: "2026-10-13T07:00", q: "0x3", t: 3 }, { id: "q-space", bed: "2026-10-13T23:00", wake: "2026-10-14T07:00", q: " 2 ", t: 3 },
      { id: "q-list", bed: "2026-10-14T23:00", wake: "2026-10-15T07:00", q: [4], t: 3 }, { id: "q-exp", bed: "2026-10-15T23:00", wake: "2026-10-16T07:00", q: "3e0", t: 3 },
      { id: "t-string", bed: "2026-10-16T23:00", wake: "2026-10-17T07:00", t: "1700000000000" }, { id: "t-bad", bed: "2026-10-17T23:00", wake: "2026-10-18T07:00", t: "abc" },
      { id: "t-null", bed: "2026-10-18T23:00", wake: "2026-10-19T07:00", t: null }, { id: "t-frac", bed: "2026-10-19T23:00", wake: "2026-10-20T07:00", t: 12.5 },
      { id: "t-neg", bed: "2026-10-20T23:00", wake: "2026-10-21T07:00", t: -7 },
      { id: "del-1", del: 1, t: 10 }, { id: "del-true", del: true, t: 10 }, { del: 1, t: 10 }, { id: "del-0", del: 0, bed: "2026-10-21T23:00", wake: "2026-10-22T07:00", t: 10 },
      { id: "del-str", del: "yes", t: 11 }, { id: "del-empty", del: "", bed: "2026-10-22T23:00", wake: "2026-10-23T07:00", t: 11 },
      E("gone", "2026-11-02T23:00", "2026-11-03T07:00", 0, 5), { id: "gone", del: 1, t: 6 },
      null, 5, "text", [], {}, [1, 2], true
    ],
    pending: [], prefs: prefs({ born: 1993 })
  });
  add("duplicates and tie-breaks", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: [
      E("a", "2026-11-10T23:00", "2026-11-11T07:00", 3, 100), E("a", "2026-11-10T23:30", "2026-11-11T07:00", 3, 200),
      E("b", "2026-11-11T23:00", "2026-11-12T07:00", 2, 100), E("b", "2026-11-11T23:00", "2026-11-12T07:30", 2, 100),
      E("c1", "2026-11-12T23:00", "2026-11-13T07:00", 4, 100), E("c2", "2026-11-12T23:00", "2026-11-13T07:00", 1, 100),
      E("d1", "2026-11-13T23:00", "2026-11-14T07:00", 4, 100), E("d2", "2026-11-13T23:00", "2026-11-14T07:00", 1, 300),
      { id: "z", del: 1, t: 50 }, E("z", "2026-11-09T23:00", "2026-11-10T07:00", 1, 50),
      E("y", "2026-11-08T23:00", "2026-11-09T07:00", 1, 50), { id: "y", del: 1, t: 50 },
      E("x", "2026-11-07T23:00", "2026-11-08T07:00", 2, 70), E("x", "2026-11-07T23:00", "2026-11-08T07:00", 3, 70),
      E("Zeta", "2026-11-06T23:00", "2026-11-07T07:00", 2, 1), E("é", "2026-11-05T23:00", "2026-11-06T07:00", 2, 1), E("😀", "2026-11-04T23:00", "2026-11-05T07:00", 2, 1),
      E("q\"\\\u0001\n ", "2026-11-03T23:00", "2026-11-04T07:00", 2, 1), E("ﬀ", "2026-11-02T23:00", "2026-11-03T07:00", 2, 1)
    ],
    pending: { bed: "2026-11-14T23:00", t: 0 }, prefs: { weekend: "fri-sat", wake: "07:00", lat: 15, born: null, goals: [], t: 0 }
  });

  /* settings, as saved */
  add("settings variants 1", "Asia/Dhaka", "2026-11-14T21:00", {
    entries: hist(12, "2026-11-14T21:00", { days: 25 }),
    prefs: {
      weekend: "sat-sun", wake: "06:45", lat: 30, born: "1990", t: T0 + 3,
      goals: [goal("2026-10-01", 450), goal("0000-01-01", 480, "2026-09-01"), goal("2026-11-01", 239), goal("2026-11-02", "510"), goal("2026-13-45", 500, "2026-11-03"),
        goal("2026-11-05", 495.5), goal("2026-11-06", 720.4), goal("2026-11-07", 720), goal("2026-11-01", 600, "2026-10-30"), { from: "2026-11-08", min: 300 }, { from: "2026-11-09", min: 300, on: "x" }, null, 7, goal("2026-11-12", 240)]
    }
  });
  const many = []; for (let i = 0; i < 205; i++) many.push(goal(day("2026-03-01", i), 300 + (i % 20) * 15));
  add("settings variants 2", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(13, "2026-11-14T21:00", { days: 25 }), prefs: { weekend: "fri", wake: "7:30", lat: "45", born: 2014, goals: many, t: "12345" } });
  add("settings variants 3", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(14, "2026-11-14T21:00", { days: 25 }), prefs: { weekend: "sun", wake: "24:00", lat: 25, born: 1906, goals: "480", t: T0 } });
  add("settings variants 4", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(15, "2026-11-14T21:00", { days: 25 }), prefs: { weekend: "sat-sun", wake: "05:30", lat: 5, born: 1990.5, goals: [goal("0000-01-01", 420)], t: 0 } });
  add("settings variants 5", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(16, "2026-11-14T21:00", { days: 25 }), prefs: { weekend: "fri-sat", wake: "09:00", lat: 10, born: 1905, goals: [], t: 0 } });
  add("settings not an object", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(17, "2026-11-14T21:00", { days: 10 }), prefs: "fri-sat", pending: "none" });

  /* the bedtime plan */
  add("plan: bedtime much later than target", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(18, "2026-11-14T21:00", { days: 20, bed: 1530, spread: 30, len: 420 }), prefs: prefs({ wake: "07:00", born: 1992 }) }, { reminders: ALL_ON });
  add("plan: bedtime much earlier than target", "Asia/Dhaka", "2026-11-14T19:30", { entries: hist(19, "2026-11-14T19:30", { days: 20, bed: 1260, spread: 30, len: 600 }), prefs: prefs({ wake: "08:00", born: 1992 }) }, { reminders: ALL_ON });
  add("plan: close to target", "Asia/Dhaka", "2026-11-14T22:30", { entries: hist(20, "2026-11-14T22:30", { days: 20, bed: 1365, spread: 10, len: 480, pMiss: 0 }), prefs: prefs({ wake: "07:00", born: 1992, goals: [goal("0000-01-01", 480)] }) }, { reminders: ALL_ON });
  add("plan: target after midnight", "Asia/Dhaka", "2026-11-14T23:50", { entries: hist(21, "2026-11-14T23:50", { days: 20, bed: 1500, spread: 40, len: 500 }), prefs: prefs({ wake: "09:30", lat: 45, born: 1992 }) }, { reminders: ALL_ON });
  const week3 = [];
  for (let i = 13; i >= 8; i--) week3.push(E("w3-" + i, day("2026-11-14", -i) + "T01:00", day("2026-11-14", -i) + "T08:00", 0, T0 + i));
  for (let i = 2; i >= 0; i--) week3.push(E("w3-" + i, day("2026-11-14", -i - 1) + "T22:30", day("2026-11-14", -i) + "T06:30", 0, T0 + i));
  add("plan: exactly 3 nights this week", "Asia/Dhaka", "2026-11-14T21:00", { entries: week3, prefs: prefs({ wake: "06:30" }) }, { reminders: ALL_ON });
  const h120 = history(1, { now: "2026-11-14T21:00", days: 120, pNap: 0.15, pSplit: 0.12 });
  const p120 = prefs({ born: 1994, goals: [goal("0000-01-01", 480), goal("2026-10-10", 465)] });
  add("dhaka 120 days, a minute before wind-down", "Asia/Dhaka", "2026-11-14T22:54", { entries: h120, prefs: p120 }, { reminders: ALL_ON });
  add("dhaka 120 days, at bedtime", "Asia/Dhaka", "2026-11-14T23:25", { entries: h120, prefs: p120 }, { reminders: ALL_ON });
  add("plan: fixed wake, 2 nights", "Asia/Dhaka", "2026-11-14T21:00", { entries: [E("p1", "2026-11-12T23:00", "2026-11-13T07:00"), E("p2", "2026-11-13T23:30", "2026-11-14T07:15")], prefs: prefs({ wake: "06:30" }) }, { reminders: ALL_ON });
  add("plan: automatic, 2 nights", "Asia/Dhaka", "2026-11-14T21:00", { entries: [E("p1", "2026-11-12T23:00", "2026-11-13T07:00"), E("p2", "2026-11-13T23:30", "2026-11-14T07:15")], prefs: prefs() }, { reminders: ALL_ON });
  add("plan: automatic, wake near midnight", "Asia/Dhaka", "2026-11-14T21:00", { entries: [E("w1", "2026-11-11T16:00", "2026-11-11T23:58"), E("w2", "2026-11-12T19:00", "2026-11-12T23:59"), E("w3", "2026-11-13T19:30", "2026-11-13T23:57"), E("w4", "2026-11-14T19:10", "2026-11-14T20:00")], prefs: prefs() }, { reminders: ALL_ON });
  add("plan: bedtime passed", "Asia/Dhaka", "2026-11-15T01:30", { entries: hist(22, "2026-11-14T21:00", { days: 20 }), prefs: prefs({ wake: "07:00" }) }, { reminders: ALL_ON });

  /* the sleep goal by age */
  const ops = [
    { at: "2026-11-14T21:00", kind: "nudge", value: 15 }, { at: "2026-11-14T21:00", kind: "born", value: "2011" }, { at: "2026-11-14T21:01", kind: "nudge", value: 15 },
    { at: "2026-11-14T21:02", kind: "nudge", value: 15 }, { at: "2026-11-14T21:03", kind: "nudge", value: -15 }, { at: "2026-11-14T21:04", kind: "nudge", value: -15 },
    { at: "2026-11-15T09:00", kind: "fit" }, { at: "2026-11-15T09:00", kind: "nudge", value: -15 }, { at: "2026-11-15T09:01", kind: "nudge", value: 15 },
    { at: "2026-11-15T09:02", kind: "nudge", value: 15 }, { at: "2026-11-16T09:00", kind: "nudge", value: 15 }, { at: "2026-11-16T09:01", kind: "set", value: 700 },
    { at: "2026-11-16T09:02", kind: "set", value: 100 }, { at: "2026-11-16T09:03", kind: "set", value: 487 }, { at: "2026-11-17T09:00", kind: "born", value: 2011 },
    { at: "2026-11-17T09:00", kind: "born", value: 2012 }, { at: "2026-11-17T09:00", kind: "born", value: 2014 }, { at: "2027-01-01T09:00", kind: "fit" }
  ];
  add("goal: teenager setting a goal", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(23, "2026-11-14T21:00", { days: 15 }), prefs: prefs() }, { goalOps: ops });
  add("goal: birthday into 65 and over", "Asia/Dhaka", "2026-12-31T21:00", { entries: hist(24, "2026-12-31T21:00", { days: 15 }), prefs: prefs({ born: 1962, goals: [goal("0000-01-01", 480), goal("2026-12-01", 600)] }) }, {
    goalOps: [{ at: "2026-12-31T21:00", kind: "fit" }, { at: "2027-01-01T08:00", kind: "fit" }, { at: "2027-01-01T08:01", kind: "nudge", value: 15 }, { at: "2027-01-01T08:02", kind: "nudge", value: -15 },
      { at: "2027-01-01T08:03", kind: "nudge", value: -15 }, { at: "2027-01-02T08:00", kind: "set", value: 540 }, { at: "2027-01-02T08:01", kind: "born", value: 1990 }, { at: "2027-01-02T08:02", kind: "born", value: 1950 }]
  });
  add("goal: birth year without goals", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(25, "2026-11-14T21:00", { days: 15 }), prefs: prefs({ born: 1958 }) }, {
    goalOps: [{ at: "2026-11-14T21:00", kind: "fit" }, { at: "2026-11-14T21:00", kind: "fit" }, { at: "2026-11-14T21:05", kind: "set", value: 420 }, { at: "2026-11-14T21:06", kind: "set", value: 450 }, { at: "2026-11-14T21:07", kind: "set", value: 420 }]
  });

  add("goal: two first goals from synced data", "Asia/Dhaka", "2026-11-14T21:00", { entries: hist(39, "2026-11-14T21:00", { days: 15 }), prefs: prefs({ born: 1990, goals: [goal("0000-01-01", 480, "2026-01-05"), goal("0000-01-01", 450, "2026-11-14")] }) }, {
    goalOps: [{ at: "2026-11-14T21:00", kind: "set", value: 480 }, { at: "2026-11-14T21:01", kind: "nudge", value: -15 }, { at: "2026-11-14T21:02", kind: "set", value: 480 }]
  });

  /* calendar edges */
  add("new year", "Asia/Dhaka", "2027-01-02T07:00", { entries: history(26, { now: "2027-01-02T07:00", days: 50 }), prefs: prefs({ born: 1997 }) }, { reminders: ALL_ON });
  add("leap day", "Asia/Dhaka", "2028-03-02T22:00", { entries: history(27, { now: "2028-03-02T22:00", days: 45 }), prefs: prefs({ born: 1997, goals: [goal("0000-01-01", 465), goal("2028-02-29", 495)] }) });

  /* CSV import */
  const csvRows = [
    "﻿Bed_Date,Bed_Time,Wake_Date,Wake_Time,Minutes,Quality",
    "2026-11-01,23:00,2026-11-02,7:05,485,4", '"2026-11-02","23:30","2026-11-03","06:45","","4.5"', "2026-11-03,23:30,2026-11-04,06:45,,x",
    "2026-11-01,23:00,2026-11-02,7:05,485,4", "2026-11-04,25:00,2026-11-05,07:00,,", "2026-11-05,23:00,2026-11-06,7:5,,", "2026-11-20,23:00,2026-11-21,07:00,,3",
    ",,,,,", "   ", "2026-10-20,22:00,2026-10-21,06:00,480,2", " 2026-11-06 , 22:15 , 2026-11-07 , 06:00 , , 3 ", "2026-11-07,9:00,2026-11-07,10:30", "2026-11-08,23:00,2026-11-09,07:00,,0x2"
  ];
  const old = hist(28, "2026-11-14T21:00", { days: 40 }).filter(e => e.bed < "2026-10-25");
  add("csv import", "Asia/Dhaka", "2026-11-14T21:00", { entries: old, prefs: prefs() }, { csvIn: csvRows.join("\r\n") + "\r\n" });
  add("csv import, quality column missing", "Asia/Dhaka", "2026-11-14T21:00", { entries: [], prefs: prefs() }, { csvIn: "wake_time,bed_date,bed_time,wake_date\n06:30,2026-11-10,22:00,2026-11-11\n7:00,2026-11-11,23:00,2026-11-12\n" });
  add("csv import, wrong columns", "Asia/Dhaka", "2026-11-14T21:00", { entries: [], prefs: prefs() }, { csvIn: "date,start,end\n2026-11-10,22:00,06:00\n" });
  add("csv import, header only", "Asia/Dhaka", "2026-11-14T21:00", { entries: [], prefs: prefs() }, { csvIn: "bed_date,bed_time,wake_date,wake_time\n\n" });

  /* daylight saving changes */
  add("new york, clocks back 1 Nov", "America/New_York", "2026-11-10T07:30", {
    entries: hist(29, "2026-11-10T07:30", { days: 30 }).filter(e => e.bed.slice(0, 10) < "2026-10-31" || e.bed.slice(0, 10) > "2026-11-01")
      .concat([E("fb1", "2026-10-31T23:30", "2026-11-01T07:00", 4, T0 + 1), E("fb2", "2026-11-01T22:45", "2026-11-02T01:30", 0, T0 + 1), E("fb3", "2026-11-02T01:40", "2026-11-02T06:50", 3, T0 + 1),
        E("fb4", "2026-11-01T01:10", "2026-11-01T01:50", 0, T0 + 1)]),
    prefs: prefs({ born: 1980, weekend: "sat-sun" })
  }, { reminders: ALL_ON, checks: [["2026-11-01", "00:30", "2026-11-01", "01:30", null], ["2026-10-31", "23:00", "2026-11-01", "06:00", "fb1"]] });
  add("new york, reminders across clocks back", "America/New_York", "2026-10-29T20:00", { entries: hist(30, "2026-10-29T20:00", { days: 20 }), prefs: prefs({ wake: "06:30", born: 1980 }) }, { reminders: ALL_ON });
  add("new york, clocks forward 8 Mar", "America/New_York", "2026-03-15T09:00", {
    entries: hist(31, "2026-03-15T09:00", { days: 25 }).filter(e => e.bed.slice(0, 10) !== "2026-03-07").concat([E("sf1", "2026-03-07T23:00", "2026-03-08T07:00", 5, T0 + 1), E("sf2", "2026-03-08T02:30", "2026-03-08T08:00", 0, T0 + 1), E("sf3", "2026-03-08T13:00", "2026-03-08T14:00", 0, T0 + 1)]),
    pending: { bed: "2026-03-08T02:15", t: 3 }, prefs: prefs({ born: 1980, weekend: "sat-sun", wake: "07:00" })
  }, { reminders: ALL_ON, checks: [["2026-03-08", "01:30", "2026-03-08", "02:30", null], ["2026-03-08", "01:30", "2026-03-08", "03:30", null]] });
  add("new york, reminders across clocks forward", "America/New_York", "2026-03-04T23:30", { entries: hist(32, "2026-03-04T23:30", { days: 20, bed: 1530, len: 400 }), prefs: prefs({ born: 1980 }) }, { reminders: ALL_ON });
  add("london, clocks back 25 Oct", "Europe/London", "2026-10-28T20:00", {
    entries: hist(33, "2026-10-28T20:00", { days: 30 }).filter(e => e.bed.slice(0, 10) !== "2026-10-24").concat([E("l1", "2026-10-24T23:50", "2026-10-25T01:30", 2, T0 + 1), E("l2", "2026-10-25T01:20", "2026-10-25T07:10", 4, T0 + 1)]),
    prefs: prefs({ born: 1975, weekend: "sat-sun" })
  }, { reminders: ALL_ON });
  add("santiago, no midnight on 6 Sep", "America/Santiago", "2026-09-10T08:00", {
    entries: hist(34, "2026-09-10T08:00", { days: 30 }).filter(e => !["2026-09-05", "2026-09-06"].includes(e.bed.slice(0, 10))).concat([E("cl1", "2026-09-05T23:00", "2026-09-06T07:00", 3, T0 + 1), E("cl2", "2026-09-06T00:30", "2026-09-06T02:00", 0, T0 + 1), E("cl3", "2026-09-06T23:40", "2026-09-07T06:30", 0, T0 + 1)]),
    prefs: prefs({ born: 1995, weekend: "sat-sun" })
  }, { reminders: ALL_ON, checks: [["2026-09-05", "22:00", "2026-09-06", "00:15", null]] });
  add("santiago, reminders across 6 Sep", "America/Santiago", "2026-09-03T20:00", { entries: hist(35, "2026-09-03T20:00", { days: 20, bed: 1450, spread: 20 }), prefs: prefs({ born: 1995, wake: "07:30" }) }, { reminders: ALL_ON });
  add("santiago, 11 PM twice on 4 Apr", "America/Santiago", "2026-04-08T08:00", {
    entries: hist(36, "2026-04-08T08:00", { days: 30 }).filter(e => !["2026-04-04", "2026-04-05"].includes(e.bed.slice(0, 10))).concat([E("ca1", "2026-04-04T23:30", "2026-04-05T07:00", 4, T0 + 1), E("ca2", "2026-04-05T22:30", "2026-04-06T06:30", 0, T0 + 1)]),
    prefs: prefs({ born: 1995 })
  }, { reminders: ALL_ON });
  add("lord howe, clocks forward 30 minutes", "Australia/Lord_Howe", "2026-10-08T07:00", {
    entries: hist(37, "2026-10-08T07:00", { days: 25 }).filter(e => e.bed.slice(0, 10) !== "2026-10-03").concat([E("lh1", "2026-10-03T23:00", "2026-10-04T07:00", 5, T0 + 1), E("lh2", "2026-10-04T02:10", "2026-10-04T03:00", 0, T0 + 1)]),
    prefs: prefs({ born: 1983 })
  }, { reminders: ALL_ON });
  add("lord howe, clocks back 30 minutes", "Australia/Lord_Howe", "2026-04-08T07:00", {
    entries: hist(38, "2026-04-08T07:00", { days: 25 }).filter(e => e.bed.slice(0, 10) !== "2026-04-04").concat([E("lh3", "2026-04-04T23:00", "2026-04-05T01:45", 2, T0 + 1), E("lh4", "2026-04-05T01:50", "2026-04-05T07:00", 3, T0 + 1)]),
    prefs: prefs({ born: 1983, wake: "07:00" })
  }, { reminders: ALL_ON });
  return list;
}
