package com.resz073.sleeptracker.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Runs the Kotlin engine on one reference case and lays out the results exactly as
 * native/scripts/golden/extract.js lays out the current app's, so the two can be compared value by value.
 */
internal object Snapshot {
    private fun day(d: LocalDate) = Time.day(d)
    private fun text(t: LocalDateTime) = Time.text(t)
    private fun stats(s: Stats) = mapOf("tot" to s.minutes, "n" to s.days, "met" to s.metGoal, "miss" to s.missing, "bal" to s.balance)
    private val STATUS = mapOf(DayStatus.BEFORE to "before", DayStatus.FUTURE to "future", DayStatus.OPEN to "open", DayStatus.DONE to "done", DayStatus.MISSING to "missing")
    private val CODE = mapOf(DayStatus.BEFORE to 'B', DayStatus.FUTURE to 'F', DayStatus.OPEN to 'O', DayStatus.DONE to 'D', DayStatus.MISSING to 'M')

    fun of(c: Map<String, Any?>): Map<String, Any?> {
        val zone = ZoneId.of(c["zone"] as String)
        val now = Now((c["nowMs"] as Number).toLong(), zone)
        val doc = SleepDoc.read(c["doc"], now)
        val m = SleepModel(doc, now)
        val plan = Planner.plan(m)
        val t = m.today
        val r = LinkedHashMap<String, Any?>()

        (c["formats"] as? Map<*, *>)?.let { r["formats"] = formats(it, zone) }

        /* data */
        r["canon"] = doc.canon()
        r["entries"] = m.entries.map { e ->
            listOf(e.id, text(e.bed), text(e.wake), e.q, e.t, Time.minutes(e.bed, e.wake, zone), Time.nightOf(e.bed)?.let(::day),
                Time.split(e.bed, e.wake, zone).map { listOf(day(it.first), it.second) })
        }
        r["byDay"] = m.byDay.keys.sorted().map { listOf(day(it), m.byDay[it]) }
        r["nights"] = m.nights.map { n -> listOf(day(n.wakeDay), text(n.bed), text(n.wake), n.minutes, n.span, n.quality, n.unrated, n.parts.map { it.id }) }
        r["start"] = m.start?.let(::day)
        r["pend"] = m.pending?.let(::text)
        r["today"] = day(t)
        var from = m.start?.minusDays(2) ?: t.minusDays(2)
        if (from < t.minusDays(400)) from = t.minusDays(400)
        val codes = StringBuilder()
        var d = from
        while (d <= t.plusDays(2)) { codes.append(CODE[m.status(d)]); d = d.plusDays(1) }
        r["status"] = listOf(day(from), codes.toString())
        r["missing"] = m.missing.map(::day)
        r["missingRecent"] = m.missingRecent().map(::day)
        val (bt, wt) = m.usual()
        r["usual"] = mapOf("bt" to bt, "wt" to wt)

        /* goal */
        val probe = sortedSetOf(day(t), Goals.FIRST_DAY, day(t.minusDays(30)))
        for (g in doc.prefs.goals) {
            probe += g.from
            Time.parse(g.from + "T12:00", zone)?.let { probe += day(it.toLocalDate().minusDays(1)) }
        }
        r["goal"] = mapOf("age" to Goals.age(doc.prefs.born, now.year), "band" to Goals.band(doc.prefs.born, now.year)?.name,
            "on" to probe.map { listOf(it, Goals.goalOn(doc.prefs.goals, it)) })

        /* today */
        val ln = m.nightByWake[t]
        r["last"] = ln?.let {
            mapOf("goal" to m.goalOn(t), "mins" to it.minutes, "parts" to it.parts.size, "cycles" to Words.cycles(it.minutes), "q" to it.quality,
                "q10" to Js.round(it.quality * 10) / 10.0, "stars" to Js.round(it.quality), "unrated" to it.unrated, "bed" to text(it.bed), "wake" to text(it.wake))
        }
        r["d14"] = stats(m.twoWeeks())
        val pend = m.pending
        r["tonight"] = when {
            pend != null -> mapOf("kind" to "asleep", "since" to text(pend), "elapsed" to m.asleepFor(), "early" to (pend.toLocalDate() < t.minusDays(1)))
            plan != null -> Planner.untilBedtime(plan, now).let { u -> mapOf("kind" to "plan", "until" to u, "past" to Planner.pastBedtime(u)) }
            else -> mapOf("kind" to "none")
        }

        /* bedtime plan and schedule */
        r["plan"] = plan?.let { p ->
            mapOf("wake" to p.wake, "autoWake" to p.autoWake, "fixed" to p.fixed, "goal" to p.goal, "lat" to p.latency, "target" to p.target, "tonight" to p.tonight,
                "cur" to p.usualBed, "dir" to p.direction, "steps" to p.steps, "sleepT" to p.sleepMinutes, "cyc" to p.cycles, "tcyc" to p.targetCycles,
                "n7" to p.recentNights, "onT" to p.onTarget, "n14" to p.nights14.map { day(it.wakeDay) })
        }
        r["schedule"] = plan?.let { p ->
            mapOf("steps" to Planner.steps(p, t).map { listOf(day(it.from), it.bedtime, it.isTarget) }, "windDown" to Planner.windDown(p),
                "splitN" to Planner.splitNights(p), "weekendWake" to Planner.lateWeekendWake(m, p))
        }
        r["diary"] = Planner.diary(m, plan)?.map { listOf(day(it.wakeDay), it.missing, it.offTarget) }
        r["autoWake"] = Planner.autoWake(m)

        /* patterns */
        r["timing"] = Patterns.timing(m)?.let { mapOf("bed" to it.bed, "wake" to it.wake, "sdBed" to it.bedSpread, "sdWake" to it.wakeSpread) }
        fun side(s: WeekSide) = mapOf("b" to s.bed, "w" to s.wake, "d" to s.minutes, "mid" to s.middle, "n" to s.nights)
        r["weekend"] = Patterns.weekend(m)?.let { mapOf("a" to side(it.workdays), "b" to side(it.weekend), "sh" to it.shift) }
        val key = mapOf(QualityTest.BEDTIME_BEFORE_MIDNIGHT to "bedtime", QualityTest.MET_GOAL to "length", QualityTest.NEAR_USUAL_BEDTIME to "usual")
        r["quality"] = Patterns.quality(m)?.let { q ->
            mapOf("n" to q.nights, "avg" to q.average, "ub" to q.usualBed, "rows" to q.splits.map { listOf(key[it.test], it.yes, it.yesNights, it.no, it.noNights) })
        }

        /* trends */
        r["trends"] = m.start?.let { start ->
            linkedMapOf("m" to PeriodKind.MONTH, "w" to PeriodKind.WEEK, "r" to PeriodKind.LAST_3_DAYS).mapValues { (_, kind) ->
                val name: (LocalDate) -> String = { p -> if (kind == PeriodKind.MONTH) day(p).substring(0, 7) else day(p) }
                val nav = kind != PeriodKind.LAST_3_DAYS
                val p = Periods.current(kind, t)
                val days = Periods.days(kind, p)
                val out = linkedMapOf<String, Any?>(
                    "first" to (if (nav) name(Periods.first(kind, start)) else null), "last" to (if (nav) name(p) else null), "p" to name(p),
                    "title" to Periods.title(kind, p), "stats" to stats(m.stats(days)),
                    "days" to days.map { x ->
                        val s = m.status(x)
                        listOf(day(x), STATUS[s], m.sleptOn(x), m.goalOn(x), x in m.missing, if (s == DayStatus.MISSING) m.nightToLog(x)?.let(::day) else null)
                    },
                    "live" to Periods.liveToday(m, days), "goalLabel" to Periods.goalLabel(m, days), "anyLive" to Periods.anyLive(m, days)
                )
                if (nav) out["periods"] = Periods.withData(m, kind).map { (q, s) -> listOf(name(q), Periods.title(kind, q), stats(s)) }
                out
            }
        }

        /* history */
        r["history"] = History.weeks(m).map { w ->
            listOf(day(w.start), Words.week(w.start), w.minutes, w.entries.map { e ->
                val sp = Time.split(e.bed, e.wake, zone)
                listOf(e.id, History.isNap(e), Words.cycles(Time.minutes(e.bed, e.wake, zone)), if (sp.size > 1) sp.map { listOf(day(it.first), it.second) } else null)
            })
        }

        /* reminders, export */
        r["reminders"] = (c["reminders"] as? Map<*, *>)?.let { s ->
            Reminders.plan(m, plan, ReminderSwitches(s["wind"] == true, s["bed"] == true, s["morning"] == true))
                .map { listOf(it.id, it.kind.key, it.kind.type, it.title, it.body, text(Time.at(it.at, zone)), it.at) }
        }
        r["csv"] = Csv.export(m)

        /* the add-entry form */
        val sg = Entries.suggest(m)
        r["suggest"] = listOf(sg.bedDate, sg.bedTime, sg.wakeDate, sg.wakeTime, sg.fresh)
        val checks = ((c["checks"] as? List<*>) ?: emptyList<Any?>()).map { it as List<*> } + listOf(listOf(sg.bedDate, sg.bedTime, sg.wakeDate, sg.wakeTime, null))
        r["checks"] = checks.map { x ->
            val ch = Entries.check(x[0] as String, x[1] as String, x[2] as String, x[3] as String, x[4] as String?, m)
            val (kind, detail) = when (ch) {
                EntryCheck.Incomplete -> "incomplete" to null
                EntryCheck.Invalid -> "invalid" to null
                EntryCheck.Future -> "future" to null
                is EntryCheck.Overlaps -> "overlap" to Words.moment(ch.other.bed)
                is EntryCheck.Fine -> "ok" to ch.minutes
            }
            listOf(x[0], x[1], x[2], x[3], x[4], kind, detail)
        }
        r["wake"] = Entries.wakeUpMessage(Entries.wakeUp(m, "test"))

        (c["csvIn"] as? String)?.let { csv ->
            var n = 0
            val imp = Csv.import(csv, m) { "csv" + n++ }
            r["csvImport"] = mapOf("message" to imp.message, "added" to imp.added.map { listOf(text(it.bed), text(it.wake), it.q, it.t) })
        }

        (c["goalOps"] as? List<*>)?.let { ops ->
            var prefs = doc.prefs
            r["goalOps"] = ops.map { o ->
                o as Map<*, *>
                val at = Now((o["nowMs"] as Number).toLong(), zone)
                val res = when (o["kind"]) {
                    "born" -> Goals.setBorn(prefs, o["value"], at)?.let { prefs = it.prefs; if (it.goalChanged) "fitted" else "set" } ?: "skip"
                    "nudge" -> Goals.nudge(prefs, (o["value"] as Number).toInt(), at)?.let { prefs = it; "changed" } ?: "same"
                    "set" -> Goals.change(prefs, (o["value"] as Number).toDouble(), at)?.let { prefs = it; "changed" } ?: "same"
                    "fit" -> Goals.fit(prefs, at)?.let { prefs = it; "changed" } ?: "same"
                    else -> error("unknown goal op")
                }
                listOf(res, prefs.born, prefs.goals.map { listOf(it.from, it.min, it.on) }, prefs.t, Goals.goalOn(prefs.goals, at.today), Goals.band(prefs.born, at.year)?.name)
            }
        }
        return r
    }

    private fun formats(f: Map<*, *>, zone: ZoneId): Map<String, Any?> {
        fun nums(k: String) = (f[k] as List<*>).map { (it as Number).toDouble() }
        return mapOf(
            "mins" to nums("mins").map { x -> listOf(x, Words.clock(x), Words.hm(x), Words.short(x), Words.tiny(x), r5(x), mod(x), hhmm(x), adj(mod(x))) },
            "goals" to nums("goals").map { x -> listOf(x, Words.goal(x.toInt()), Words.hours(x.toInt())) },
            "days" to (f["days"] as List<*>).map { s ->
                val d = Time.parseDay(s as String)!!
                listOf(s, Words.day(d), Words.dayShort(d), Words.night(d), Words.week(d), Words.dayLong(d), Words.month(d.withDayOfMonth(1)), day(Time.weekStart(d)), Time.weekday(d))
            },
            "fixed" to nums("fixed").map { x -> listOf(x, Js.toFixed(x, 1), Js.toFixed(x, 2), Js.num(Js.round(x * 10) / 10.0)) },
            "whens" to (f["whens"] as List<*>).map { s -> listOf(s, Words.moment(Time.parse(s as String, zone)!!)) },
            "ages" to Goals.BANDS.map { b -> listOf(b.lo, b.hi, b.name, listOf(b.recLo, b.recHi), listOf(b.okLo, b.okHi), b.default, Words.range(b.recLo, b.recHi), Words.range(b.okLo, b.okHi)) },
            "words" to listOf(1, 2, 3).map { listOf(it, Words.nightsNotLogged(it)) },
            "stats" to (f["lists"] as List<*>).map { a -> val v = (a as List<*>).map { (it as Number).toDouble() }; listOf(a, median(v), mean(v), sd(v)) }
        )
    }
}
