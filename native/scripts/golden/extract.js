// Runs inside the app sandbox (see sandbox.mjs) after src/app.js. It calls the app's own functions and
// records their results as plain data: these are the "expect" values the Kotlin engine is tested against.
// Where the app works something out inside a screen function, the same lines are repeated here, and the
// result is checked against what that screen actually showed, so this file can't drift from the app.
(function () {
  const textOf = id => { const e = __els.get(id); const v = e && e.textContent; return typeof v === "string" ? v : ""; };
  const htmlOf = id => { const e = __els.get(id); const v = e && e.innerHTML; return typeof v === "string" ? v : ""; };
  const must = (ok, msg) => { if (!ok) throw new Error("harness check failed: " + msg); };
  const shows = (id, s) => must(htmlOf(id).includes(s), id + " should show " + JSON.stringify(s) + " but has " + JSON.stringify(htmlOf(id).slice(0, 400)));
  const local = d => dstr(d) + "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
  const CODE = { before: "B", future: "F", open: "O", done: "D", missing: "M" };

  function load(scn) { return fromRaw(scn.doc == null ? null : JSON.parse(JSON.stringify(scn.doc))); }
  function resetUi() { sel.m = null; sel.w = null; pick = -1; listOpen = false; tab = "m"; hWeek = null; edit = null; fresh = false; }

  globalThis.__run = function (scn) {
    __setNow(scn.nowMs);
    __ls.clear();
    if (scn.reminders) __ls.set(LS_REM, JSON.stringify(scn.reminders));
    resetUi();
    S = load(scn);
    render();
    const t = M.t, R = {};
    must(t === scn.now.slice(0, 10), "today is " + t + ", expected " + scn.now);

    if (scn.formats) R.formats = formats(scn.formats);

    /* data */
    R.canon = canon(S);
    R.entries = M.es.map(e => [e.id, e.bed, e.wake, e.q, e.t, dur(e), nightOf(e), split(e)]);
    R.byDay = Object.keys(M.byDay).sort().map(k => [k, M.byDay[k]]);
    R.nights = M.nights.map(N => [N.W, N.bed, N.wake, N.mins, dur(N), N.q, N.unrated, N.segs.map(e => e.id)]);
    R.start = M.start; R.pend = M.pend; R.today = t;
    let from = M.start ? addD(M.start, -2) : addD(t, -2);
    if (from < addD(t, -400)) from = addD(t, -400);
    let codes = ""; for (let d = from; d <= addD(t, 2); d = addD(d, 1)) codes += CODE[M.status(d)];
    R.status = [from, codes];
    R.missing = [...M.MN].sort();
    R.missingRecent = missingNights();
    R.usual = usual();

    /* goal */
    const probe = new Set([t, FIRST_DAY, addD(t, -30)]);
    S.prefs.goals.forEach(g => { probe.add(g.from); if (/^\d{4}-\d\d-\d\d$/.test(g.from) && okDT(g.from + "T12:00")) probe.add(addD(g.from, -1)); });
    R.goal = { age: ageNow(), band: band() ? band().name : null, on: [...probe].sort().map(d => [d, goalOn(d)]) };

    /* today */
    const ln = M.nightW.get(t);
    R.last = ln ? { goal: goalOn(t), mins: ln.mins, parts: ln.segs.length, cycles: (ln.mins / 90).toFixed(1), q: ln.q, q10: Math.round(ln.q * 10) / 10, stars: Math.round(ln.q), unrated: ln.unrated, bed: ln.bed, wake: ln.wake } : null;
    if (ln) shows("lastnight", hm(ln.mins));
    const d14 = []; for (let i = 14; i >= 1; i--) d14.push(addD(t, -i));
    const st = stats(d14); st.miss = [...M.MN].filter(W => W > addD(t, -14)).length;
    R.d14 = st;
    must(textOf("debt") === (st.n ? hm(st.bal) : "–"), "debt " + textOf("debt"));

    if (M.pend) {
      const el = Math.max(0, (Date.now() - ts(M.pend)) / 60000);
      R.tonight = { kind: "asleep", since: M.pend, elapsed: el, early: M.pend.slice(0, 10) < addD(t, -1) };
      shows("tonight", fmt(tod(M.pend))); shows("tonight", hm(el));
    } else if (P) {
      const n = new Date(), nm = n.getHours() * 60 + n.getMinutes(), d = (mod(P.tonight) - nm + 1440) % 1440;
      R.tonight = { kind: "plan", until: d, past: d > 1440 - 240 };
      shows("tonight", fmt(P.tonight));
    } else R.tonight = { kind: "none" };

    /* bedtime plan and schedule */
    R.plan = P ? { wake: P.wake, autoWake: P.autoWake, fixed: P.fixed, goal: P.goal, lat: P.lat, target: P.target, tonight: P.tonight, cur: P.cur, dir: P.dir, steps: P.steps, sleepT: P.sleepT, cyc: P.cyc, tcyc: P.tcyc, n7: P.n7, onT: P.onT, n14: P.n14.map(N => N.W) } : null;
    if (P) {
      const steps = [], shown = Math.min(P.steps, 3);
      for (let k = 1; k <= shown; k++) { const last = k === P.steps; steps.push([addD(t, 4 * k), last ? P.target : P.tonight - P.dir * 15 * k, last]); }
      if (P.steps > shown) steps.push([addD(t, 4 * P.steps), P.target, true]);
      const splitN = P.n14.filter(N => N.segs.length > 1).length;
      const wd = WEEKENDS[S.prefs.weekend] || WEEKENDS["fri-sat"], n28 = M.nights.filter(N => N.W > addD(t, -28) && N.W <= t), B = n28.filter(N => wd.includes(dobj(N.W).getDay()));
      const bw = B.length >= 2 ? med(B.map(N => tod(N.wake))) : null;
      R.schedule = { steps, windDown: P.tonight - 30, splitN, weekendWake: bw != null && bw - P.wake > 60 ? bw : null };
      shows("schSky", fmt(P.target)); shows("schPlan", fmt(P.tonight - 30));
      steps.forEach(s => shows("schPlan", fmt(s[1])));
      if (R.schedule.weekendWake != null) shows("schPlan", fmt(bw));
    } else R.schedule = null;
    R.diary = M.nights.length ? Array.from({ length: 14 }, (_, i) => { const Wd = addD(t, -i), N = M.nightW.get(Wd); return [Wd, M.MN.has(Wd), N && P ? adj(tod(N.bed)) - P.target : null]; }) : null;
    const n14 = M.nights.filter(N => N.W > addD(t, -14) && N.W <= t);
    R.autoWake = n14.length >= 3 ? r5(med(n14.map(N => tod(N.wake)))) : null;

    /* patterns */
    const recent = k => M.nights.filter(N => N.W > addD(t, -k) && N.W <= t);
    if (n14.length < 3) R.timing = null;
    else {
      const bed = n14.map(N => adj(tod(N.bed))), wake = n14.map(N => tod(N.wake));
      R.timing = { bed: med(bed), wake: med(wake), sdBed: sd(bed), sdWake: sd(wake) };
      shows("timing", fmt(R.timing.bed)); shows("timing", fmt(R.timing.wake)); shows("timing", "±" + dshort(R.timing.sdBed)); shows("timing", "±" + dshort(R.timing.sdWake));
    }
    const wdays = WEEKENDS[S.prefs.weekend] || WEEKENDS["fri-sat"], isWE = N => wdays.includes(dobj(N.W).getDay());
    const n28 = recent(28), A = n28.filter(N => !isWE(N)), B = n28.filter(isWE);
    if (A.length < 2 || B.length < 2) R.weekend = null;
    else {
      const sum = G => ({ b: med(G.map(N => adj(tod(N.bed)))), w: med(G.map(N => tod(N.wake))), d: mean(G.map(N => N.mins)), mid: mean(G.map(N => adj(tod(N.bed)) + dur(N) / 2)), n: G.length });
      const a = sum(A), b = sum(B);
      R.weekend = { a, b, sh: b.mid - a.mid };
      shows("weekend", fmt(a.b) + " – " + fmt(a.w)); shows("weekend", fmt(b.b) + " – " + fmt(b.w)); shows("weekend", hm(a.d) + " average");
    }
    const Q = recent(60).filter(N => N.q);
    if (!Q.length) R.quality = null;
    else {
      const ub = mean(Q.map(N => adj(tod(N.bed)))), rows = [];
      [["bedtime", N => adj(tod(N.bed)) < 1440], ["length", N => N.mins >= goalOn(N.W)], ["usual", N => Math.abs(adj(tod(N.bed)) - ub) <= 30]].forEach(([k, f]) => {
        const Y = Q.filter(f), No = Q.filter(N => !f(N)); if (Y.length < 3 || No.length < 3) return;
        rows.push([k, mean(Y.map(N => N.q)), Y.length, mean(No.map(N => N.q)), No.length]);
      });
      R.quality = { n: Q.length, avg: mean(Q.map(N => N.q)), ub, rows };
      shows("quality", "★ " + R.quality.avg.toFixed(1));
      if (rows.some(r => r[0] === "usual")) shows("quality", fmt(ub));
      rows.forEach(r => { shows("quality", "★ " + r[1].toFixed(1)); shows("quality", "★ " + r[3].toFixed(1)); });
    }

    /* trends */
    if (!M.start) R.trends = null;
    else {
      R.trends = {};
      ["m", "w", "r"].forEach(k => {
        const c = VIEWS[k]; let p, first = null, last = null;
        if (c.nav) { first = c.first(M.start); last = c.cur(t); p = last; } else p = c.cur(t);
        const days = c.days(p), s = stats(days), gs = days.map(goalOn);
        const out = {
          first, last, p, title: c.title(p), stats: s,
          days: days.map(d => { const x = M.status(d); return [d, x, M.byDay[d] || 0, goalOn(d), M.MN.has(d), x === "missing" ? (M.MN.has(d) ? d : M.MN.has(addD(d, 1)) ? addD(d, 1) : null) : null]; }),
          live: days.includes(t) && M.status(t) === "open" && M.byDay[t] ? M.byDay[t] : 0,
          goalLabel: gs.every(g => g === gs[0]) ? "Goal " + dshort(gs[0]) : "Goal (changed)",
          anyLive: days.some(d => M.status(d) === "open" && !!M.byDay[d])
        };
        if (c.nav) {
          out.periods = [];
          for (let q = c.cur(t); q >= first; q = c.step(q, -1)) { const s2 = stats(c.days(q)); if (s2.n || s2.miss) out.periods.push([q, c.title(q), s2]); }
        }
        R.trends[k] = out;
      });
      const m = R.trends.m;
      must(textOf("ttitle") === m.title, "trends title " + textOf("ttitle"));
      if (m.stats.n) shows("tbal", hm(m.stats.bal));
      shows("tlegend", m.goalLabel);
    }

    /* history */
    R.history = histWeeks().map(w => {
      const list = M.es.slice().reverse().filter(e => wkStart(e.wake.slice(0, 10)) === w);
      return [w, weekName(w), list.reduce((a, e) => a + dur(e), 0), list.map(e => { const sp = split(e); return [e.id, !isNight(e), (dur(e) / 90).toFixed(1), sp.length > 1 ? sp : null]; })];
    });
    if (R.history.length) shows("histcard", hm(R.history[0][2]));

    /* reminders, export */
    R.reminders = scn.reminders ? reminderList().map(n => [n.id, n.kind, n.type || null, n.title, n.body, local(n.at), n.at.getTime()]) : null;
    R.csv = csv();

    /* the add-entry form: what it suggests, and what it says about some times */
    resetForm();
    R.suggest = [$("bd").value, $("bt").value, $("wd").value, $("wt").value, fresh];
    R.checks = (scn.checks || []).concat([[R.suggest[0], R.suggest[1], R.suggest[2], R.suggest[3], null]]).map(([bd, bt, wd, wt, ed]) => {
      $("bd").value = bd; $("bt").value = bt; $("wd").value = wd; $("wt").value = wt; $("q").value = "0"; fresh = false; edit = ed;
      calc();
      const h = htmlOf("calc"); let kind, detail = null;
      if (h === "") kind = "incomplete";
      else if (h.includes("Wake-up must be after bedtime")) kind = "invalid";
      else if (h.includes("Wake-up time is still ahead")) kind = "future";
      else if (h.includes("This overlaps your sleep from ")) { kind = "overlap"; detail = h.split("This overlaps your sleep from ")[1].split(". Edit that entry")[0]; }
      else if (h.startsWith("Sleep time <b>")) { kind = "ok"; detail = dur(cur()); must(h === "Sleep time <b>" + hm(detail) + "</b>, about " + (detail / 90).toFixed(1) + " cycles", h); }
      else throw new Error("unknown form message " + h);
      must($("save").disabled === (kind !== "ok"), "save button");
      edit = null;
      return [bd, bt, wd, wt, ed, kind, detail];
    });
    resetUi();

    /* "I'm awake" (changes the data, so it runs last) */
    if (S.pending.bed) { __els.get("tmsg") && (__els.get("tmsg").textContent = ""); wakeUp(); R.wake = textOf("tmsg"); }
    else R.wake = null;

    if (scn.csvIn != null) {
      S = load(scn); render();
      const before = S.entries.length;
      importCsv(scn.csvIn);
      R.csvImport = { message: textOf("tmsg"), added: S.entries.slice(before).map(e => [e.bed, e.wake, e.q, e.t]) };
    }

    if (scn.goalOps) {
      S = load(scn);
      R.goalOps = scn.goalOps.map(op => {
        __setNow(op.nowMs);
        let res;
        if (op.kind === "born") { // the birth year menu
          const v = normBorn(op.value);
          if (!v || v === S.prefs.born) res = "skip";
          else { S.prefs = { ...S.prefs, born: v, t: Date.now() }; res = fitGoal() ? "fitted" : "set"; }
        } else if (op.kind === "nudge") res = applyGoal(goalOn(today()) + op.value) ? "changed" : "same"; // the − and + buttons
        else if (op.kind === "set") res = applyGoal(op.value) ? "changed" : "same";
        else if (op.kind === "fit") res = fitGoal() ? "changed" : "same"; // app start and each new day
        else throw new Error("unknown goal op " + op.kind);
        return [res, S.prefs.born, S.prefs.goals.map(g => [g.from, g.min, g.on]), S.prefs.t, goalOn(today()), band() ? band().name : null];
      });
      __setNow(scn.nowMs);
    }
    return R;
  };

  function formats(F) {
    return {
      mins: F.mins.map(m => [m, fmt(m), hm(m), dshort(m), dtiny(m), r5(m), mod(m), hhmm(m), adj(mod(m))]),
      goals: F.goals.map(m => [m, glong(m), hrs(m)]),
      days: F.days.map(k => [k, dayFull(k), dayShort(k), nightName(k), weekName(k), fdate(k, { weekday: "long", day: "numeric", month: "long" }), monthName(k.slice(0, 7)), wkStart(k), dobj(k).getDay()]),
      fixed: F.fixed.map(x => [x, x.toFixed(1), x.toFixed(2), String(Math.round(x * 10) / 10)]),
      whens: F.whens.map(s => [s, when(s)]),
      ages: AGES.map(b => [b.lo, b.hi, b.name, b.rec, b.ok, b.def, rangeText(b.rec), rangeText(b.ok)]),
      words: [1, 2, 3].map(n => [n, nn(n)]),
      stats: F.lists.map(a => [a, med(a), mean(a), sd(a)])
    };
  }
})();
