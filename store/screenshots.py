#!/usr/bin/env python3
"""
Screenshots for the Google Play listing: the newest release on an Android 14 emulator, with seven weeks of
made-up sleep, each tab in light and dark mode, and a clean status bar. The raw screenshots go to
emulator-results/ (the store-screenshots workflow keeps them on the store-raw branch); store/compose.py
puts them in frames with captions.

    python3 store/screenshots.py sleep-tracker.apk

It uses the emulator test's helpers (native/scripts/emulator/drive.py), and its throwaway account is
deleted at the end, as in the test.
"""
import datetime, os, random, sys, time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "scripts", "emulator"))
import drive as d  # noqa: E402

CSV_NAME = "sleep-sample.csv"


def sample_csv():
    """Seven weeks of nights ending last night, none missed: bedtimes around 11:30 PM, wake-ups around 7 AM and
    later at weekends (a few hours of sleep debt, and a plan to go to bed a little earlier), most nights rated,
    and a nap on two Sundays"""
    today = datetime.date.fromisoformat(d.sh("date +%Y-%m-%d").strip())
    r = random.Random(247)
    rows = ["bed_date,bed_time,wake_date,wake_time,minutes,quality"]

    def add(bed, wake, q):
        rows.append("%s,%s,%s,%s,%d,%s" % (bed.date(), bed.strftime("%H:%M"), wake.date(), wake.strftime("%H:%M"),
                                           int((wake - bed).total_seconds() // 60), q))

    for i in range(49, -1, -1):  # i = 0: last night, waking up today
        day = today - datetime.timedelta(days=i)
        bed = datetime.datetime.combine(day - datetime.timedelta(days=1), datetime.time(23, 30)) + \
            datetime.timedelta(minutes=r.randint(-30, 30) // 5 * 5)
        weekend = day.weekday() in (5, 6)
        wake = datetime.datetime.combine(day, datetime.time(7, 5)) + \
            datetime.timedelta(minutes=(r.randint(25, 70) if weekend else r.randint(-10, 25)) // 5 * 5)
        q = 4 if i == 0 else (r.randint(2, 5) if r.random() < 0.85 else "")
        add(bed, wake, q)
        if day.weekday() == 6 and i in (7, 21):
            nap = datetime.datetime.combine(day, datetime.time(15, 10))
            add(nap, nap + datetime.timedelta(minutes=40), "")
    return "\n".join(rows) + "\n", len(rows) - 1


@d.step("Sage wallpaper colours")
def s_palette():
    # What Android makes from a green wallpaper, so light mode looks as it does with the app's own fallback
    d.sh("settings put secure theme_customization_overlay_packages "
         "'{\"android.theme.customization.system_palette\":\"4C662B\",\"android.theme.customization.accent_color\":\"4C662B\","
         "\"android.theme.customization.color_source\":\"preset\",\"android.theme.customization.theme_style\":\"TONAL_SPOT\"}'")
    time.sleep(5)


@d.step("Import seven weeks of made-up sleep", critical=True)
def s_sample():
    data, n = sample_csv()
    with open(os.path.join(d.OUT, CSV_NAME), "w") as f:
        f.write(data)
    d.run(["adb", "push", os.path.join(d.OUT, CSV_NAME), "/sdcard/Download/" + CSV_NAME])
    d.sh("content call --uri content://media --method scan_volume --arg external_primary", 120)
    d.sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/" + CSV_NAME)
    time.sleep(2)
    d.nav("Today")
    d.nav("Settings")
    for _ in range(6):
        if d.present("Import CSV"):
            break
        d.swipe_up()
    d.tap("Import CSV")
    time.sleep(3)
    if not d.present(CSV_NAME):
        if d.present(desc="Show roots"):
            d.tap(desc="Show roots")
            d.tap("Downloads")
        time.sleep(2)
    d.tap(CSV_NAME, timeout=20)
    d.wait("Imported %d" % n, contains=True, timeout=20)
    return "%d entries" % n


@d.step("Answer Today's questions (goal and reminders)")
def s_prompts():
    d.nav("Today")
    time.sleep(1)
    for _ in range(3):
        if not d.present("Not now"):
            break
        d.tap("Not now")
        time.sleep(1)


def demo(on):
    """Android's demo mode: a fixed status bar (the time stays the phone's own, to match what the app shows)"""
    if not on:
        d.sh("am broadcast -a com.android.systemui.demo -e command exit")
        return
    d.sh("settings put global sysui_demo_allowed 1")
    for extra in ["-e command enter",
                  "-e command clock -e hhmm " + d.sh("date +%H%M").strip(),
                  "-e command battery -e level 100 -e plugged false",
                  "-e command network -e wifi show -e level 4 -e fully true",
                  "-e command network -e mobile show -e datatype none -e level 4 -e fully true",
                  "-e command notifications -e visible false"]:
        d.sh("am broadcast -a com.android.systemui.demo " + extra)
    time.sleep(1)


def shots(mode):
    d.nav("Today")
    d.wait("Bedtime tonight", timeout=15)
    time.sleep(1.5)
    d.shot(mode + "-today")
    d.swipe_up()
    d.shot(mode + "-today-lower")
    d.nav("Trends")
    d.tap("Month")
    time.sleep(1)
    d.shot(mode + "-trends")
    d.tap(desc="Earlier month")
    time.sleep(1.5)
    d.shot(mode + "-trends-earlier")
    d.nav("Schedule")
    time.sleep(1.5)
    d.shot(mode + "-schedule")
    d.swipe_up()
    d.shot(mode + "-schedule-lower")
    d.nav("History")
    d.tap(desc="Earlier week")
    time.sleep(1.5)
    d.shot(mode + "-history")
    d.tap(desc="Edit sleep from", contains=True)
    d.wait("Edit sleep")
    time.sleep(1)
    d.shot(mode + "-edit")
    d.tap("Cancel")
    time.sleep(1)


@d.step("Light mode")
def s_light():
    demo(True)
    shots("light")


@d.step("Dark mode")
def s_dark_shots():
    d.sh("cmd uimode night yes")
    time.sleep(4)
    demo(True)
    try:
        shots("dark")
    finally:
        d.sh("cmd uimode night no")
        time.sleep(3)
        demo(False)


def main():
    try:
        for s in [s_palette, d.s_open, d.s_signup, s_sample, s_prompts, s_light, s_dark_shots, d.s_delete]:
            s()
    except Exception:
        pass
    lines = ["### Store screenshots", "", "| Step | Result | Note |", "|---|---|---|"]
    lines += ["| %s | %s | %s |" % (name, "✅" if ok else "❌", note.replace("|", "/")[:200]) for name, ok, note in d.results]
    summary = "\n".join(lines) + "\n"
    print(summary)
    with open(os.path.join(d.OUT, "summary.md"), "w") as f:
        f.write(summary)
    sys.exit(1 if any(not ok for _, ok, _ in d.results) else 0)


if __name__ == "__main__":
    main()
