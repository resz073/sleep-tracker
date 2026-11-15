#!/usr/bin/env python3
"""
Uses the app on an Android emulator the way a person would, from the signed release APK: creates a
throwaway account, imports a month of sleep from a CSV file, visits every screen and the entry sheet,
turns on reminders and Health Connect, uses the tile, a reminder's button and the shortcuts, then deletes
the account. Screenshots, the screen text and the log go to emulator-results/.

    python3 native/scripts/emulator/drive.py dist/sleep-tracker.apk [package]

FULL=0 (an older Android version) only signs up, looks at each screen, and deletes the account.
Fails if the app crashes, or if a check doesn't pass.
"""
import datetime, os, random, re, subprocess, sys, time
import xml.etree.ElementTree as ET

APK = sys.argv[1]
PKG = sys.argv[2] if len(sys.argv) > 2 else "com.resz073.sleeptracker"
ACT = PKG + "/com.resz073.sleeptracker.MainActivity"
OUT = "emulator-results"
FULL = os.environ.get("FULL", "1") == "1"
API = os.environ.get("API", "?")
os.makedirs(OUT, exist_ok=True)
results = []  # (step, ok, note)


class Fail(Exception):
    pass


def run(args, timeout=90):
    return subprocess.run(args, capture_output=True, text=True, errors="replace", timeout=timeout)


def sh(cmd, timeout=90):
    return run(["adb", "shell", cmd], timeout).stdout


DUMP_ERROR = [""]  # why the screen couldn't be read, the last time it couldn't


def dump():
    """The screen's accessibility tree. A system "isn't responding" dialog in the way is waited out first."""
    last = None
    for _ in range(4):
        root = read_screen()
        if root is None or not system_dialogs(root):
            return root if root is not None else last
        last = root
    return last


def read_screen():
    for _ in range(3):
        try:
            r = run(["adb", "shell", "uiautomator dump /sdcard/ui.xml"], 40)
        except subprocess.TimeoutExpired:
            DUMP_ERROR[0] = "uiautomator dump took over 40 seconds"
            try:
                sh("pkill -f uiautomator", 20)
            except subprocess.TimeoutExpired:
                pass
            continue
        out = (r.stdout + r.stderr).strip()
        if "dumped" in out:
            xml = run(["adb", "exec-out", "cat /sdcard/ui.xml"], 30).stdout
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                DUMP_ERROR[0] = "the dump wasn't readable"
        else:
            DUMP_ERROR[0] = " ".join(out.split())[-300:] or "uiautomator printed nothing"
        time.sleep(1)
    return None


DIAGNOSED = [0]


def diagnose(why):
    """When the screen can't be read: the accessibility events of 4 seconds and the focused window, to see what keeps it busy"""
    if DIAGNOSED[0] >= 3:
        return
    DIAGNOSED[0] += 1
    try:
        events = run(["adb", "shell", "timeout 4 uiautomator events"], 30).stdout
    except subprocess.TimeoutExpired:
        events = "(uiautomator events didn't stop)"
    window = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
    with open(os.path.join(OUT, "diag-%d.txt" % DIAGNOSED[0]), "w") as f:
        text = why + "\n" + DUMP_ERROR[0] + "\n\n" + window + "\n" + "\n".join(l[:400] for l in events.splitlines()[:150]) + "\n"
        f.write(text.replace(PASSWORD, "<password>"))  # results are published


def nodes(root):
    for n in root.iter("node"):
        b = [int(x) for x in re.findall(r"-?\d+", n.get("bounds", ""))]
        if len(b) == 4 and b[2] > b[0] and b[3] > b[1]:
            yield n, b


def find(text=None, desc=None, contains=False, lowest=False, root=None, nocase=False):
    root = root if root is not None else dump()
    if root is None:
        return None
    norm = (lambda s: s.lower()) if nocase else (lambda s: s)
    hits = []
    for n, b in nodes(root):
        t, d = norm(n.get("text", "")), norm(n.get("content-desc", ""))
        ok = False
        if text is not None:
            ok = norm(text) in t if contains else t == norm(text)
        if not ok and desc is not None:
            ok = norm(desc) in d if contains else d == norm(desc)
        if ok:
            hits.append(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
    if not hits:
        return None
    return max(hits, key=lambda h: h[1]) if lowest else hits[0]


ANRS = [0]


def save_anr(title):
    """What a hung app was doing: the system's report from the log, and the main thread from its trace file"""
    if ANRS[0] >= 2:
        return
    ANRS[0] += 1
    try:
        ensure_root()
        log = run(["adb", "logcat", "-d", "-v", "time"], 90).stdout
        i = log.rfind("ANR in ")
        report = log[max(0, i - 3000):i + 5000] if i >= 0 else "(no ANR report in the log)\n" + "\n".join(log.splitlines()[-150:])
        name = sh("ls -t /data/anr/ 2>/dev/null | head -1").strip()
        main = "(no trace file)"
        if name:
            t = sh("cat /data/anr/" + name, 90)
            j = t.find('"main"')
            main = name + "\n" + (t[j:j + 8000] if j >= 0 else t[:8000])
        with open(os.path.join(OUT, "diag-anr-%d.txt" % ANRS[0]), "w") as f:
            f.write((title + "\n\n" + report + "\n\n" + main).replace(PASSWORD, "<password>"))  # results are published
    except Exception as e:
        print("couldn't save the ANR report:", e, flush=True)


def system_dialogs(root):
    """Slow emulators show "X isn't responding" for other apps (the launcher): wait it out. Returns True if one was there."""
    if root is None:
        return False
    for n, _ in nodes(root):
        if "isn't responding" in n.get("text", ""):
            save_anr(n.get("text", ""))
            h = find("Wait", root=root)
            if h:
                sh("input tap %d %d" % h)
                time.sleep(2)
                return True
    return False


def wait(text=None, desc=None, contains=False, timeout=25, lowest=False, nocase=False, quiet=False):
    end = time.time() + timeout
    read = False
    while time.time() < end:
        root = dump()
        if root is None:
            continue
        read = True
        if system_dialogs(root):
            continue
        h = find(text, desc, contains, lowest, root=root, nocase=nocase)
        if h:
            return h
        time.sleep(1)
    if quiet:
        return None
    what = repr(text if text is not None else desc)
    if not read:
        diagnose("looking for " + what)
        raise Fail("couldn't read the screen while looking for %s: %s" % (what, DUMP_ERROR[0][:160]))
    raise Fail("didn't see " + what)


def tap(text=None, desc=None, contains=False, timeout=25, lowest=False, nocase=False):
    x, y = wait(text, desc, contains, timeout, lowest, nocase)
    sh("input tap %d %d" % (x, y))
    time.sleep(1.2)


def fields(root=None):
    """Text fields on screen, top to bottom (found by kind: a password field hides its label from this tree)"""
    root = root if root is not None else dump()
    if root is None:
        return []
    out = [((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) for n, b in nodes(root) if n.get("class") == "android.widget.EditText"]
    return sorted(out, key=lambda h: h[1])


def present(text=None, desc=None, contains=False):
    return find(text, desc, contains) is not None


def keyboard_shown():
    return "mInputShown=true" in sh("dumpsys input_method")


def hide_keyboard():
    if keyboard_shown():
        sh("input keyevent KEYCODE_BACK")
        time.sleep(0.8)


def type_text(s):
    sh("input text '%s'" % s)
    time.sleep(0.5)


def fill(handle, value, check=True):
    """Types into the field at handle. A system dialog popping up half way takes the keys, so when the text
    isn't all there (check), the field is emptied and typed again. Older Androids can't read the screen while a
    text field's cursor blinks, so there it's typed once."""
    check = check and API.isdigit() and int(API) >= 30
    for _ in range(3):
        sh("input tap %d %d" % handle)
        time.sleep(0.8)
        type_text(value)
        if not check or value in texts(dump()):  # dump() waits out a system dialog
            return
        sh("input tap %d %d" % handle)
        time.sleep(0.5)
        sh("input keyevent KEYCODE_MOVE_END " + " ".join(["KEYCODE_DEL"] * 80))
        time.sleep(0.5)


def swipe_up():
    sh("input swipe 540 1750 540 750 450")
    time.sleep(1)


def swipe_down():
    sh("input swipe 540 750 540 1750 450")
    time.sleep(1)


def shot(name):
    data = subprocess.run(["adb", "exec-out", "screencap -p"], capture_output=True, timeout=60).stdout
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(data)
    root = dump()
    if root is not None:
        texts = [n.get("text") or n.get("content-desc") for n, _ in nodes(root) if (n.get("text") or n.get("content-desc"))]
        with open(os.path.join(OUT, name + ".txt"), "w") as f:
            f.write(("\n".join(texts) + "\n").replace(PASSWORD, "<password>"))


def crashed():
    log = run(["adb", "logcat", "-d", "-b", "crash"], 60).stdout
    return [l for l in log.splitlines() if PKG in l or "FATAL EXCEPTION" in l]


def step(name, critical=False):
    def wrap(fn):
        def go():
            try:
                note = fn() or ""
                results.append((name, True, note))
                print("PASS", name, note, flush=True)
                return True
            except Exception as e:
                results.append((name, False, str(e)))
                print("FAIL", name, e, flush=True)
                try:
                    shot("FAILED-" + re.sub(r"[^a-z0-9]+", "-", name.lower()))
                except Exception:
                    pass
                if critical:
                    raise
                return False
        go.__name__ = name
        return go
    return wrap


ROOTED = [False]
NIGHTS = [0]  # nights imported from the CSV file


def ensure_root():
    """adb as root (the emulator allows it): to send the app's own reminder and to read what it noted"""
    if not ROOTED[0]:
        run(["adb", "root"], 60)
        run(["adb", "wait-for-device"], 120)
        time.sleep(3)
        ROOTED[0] = True


def back_to_app_main():
    # Back out of anything (sheets, settings) to the main screen, without leaving the app
    for _ in range(4):
        if present("Today") and present("History"):
            return
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1)
    if not (present("Today") and present("History")):
        sh("am start -n " + ACT)
        time.sleep(2)


def nav(tab):
    tap(tab, lowest=True)


rnd = random.Random(int(time.time()))
EMAIL = "sleep-ci-%d-%s@example.com" % (int(time.time()), API)
PASSWORD = "Ci" + "".join(rnd.choice("abcdefghjkmnpqrstuvwxyz23456789") for _ in range(14))
# The test account's password is kept out of the results (they're published), only for cleanup.py
with open(os.environ.get("ACCOUNTS", os.path.join(OUT, "accounts.txt")), "a") as f:
    f.write(EMAIL + " " + PASSWORD + "\n")


def csv_for_device():
    # A month of nights ending on the phone's today: bedtimes around 11 PM, wake-ups around 7 AM, a few missed
    today = datetime.date.fromisoformat(sh("date +%Y-%m-%d").strip())
    r = random.Random(7)
    rows = ["bed_date,bed_time,wake_date,wake_time,minutes,quality"]
    for i in range(35, 0, -1):
        wake = today - datetime.timedelta(days=i)
        if r.random() < 0.1:
            continue
        bed_dt = datetime.datetime.combine(wake - datetime.timedelta(days=1), datetime.time(23, 0)) + datetime.timedelta(minutes=r.randint(-60, 75) // 5 * 5)
        wake_dt = datetime.datetime.combine(wake, datetime.time(7, 0)) + datetime.timedelta(minutes=r.randint(-50, 60) // 5 * 5)
        q = r.randint(1, 5) if r.random() < 0.6 else ""
        mins = int((wake_dt - bed_dt).total_seconds() // 60)
        rows.append("%s,%s,%s,%s,%d,%s" % (bed_dt.date(), bed_dt.strftime("%H:%M"), wake_dt.date(), wake_dt.strftime("%H:%M"), mins, q))
    return "\n".join(rows) + "\n", len(rows) - 1


# ---------------------------------------------------------------- steps

@step("Install and open", critical=True)
def s_open():
    # Let the emulator finish starting (its launcher is slow at first), and clear any dialog it shows
    sh("input keyevent KEYCODE_HOME")
    time.sleep(20)
    system_dialogs(dump())
    run(["adb", "logcat", "-c", "-b", "all"])
    out = run(["adb", "install", "-r", APK], 240)
    if "Success" not in out.stdout:
        raise Fail("install: " + out.stdout + out.stderr)
    sh("am start -W -n " + ACT, 60)
    wait("Continue with Google", timeout=40)
    shot("01-sign-in")


def focus():
    """The window in front, like com.resz073.sleeptracker/com.resz073.sleeptracker.MainActivity"""
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}]+)\}", sh("dumpsys window | grep mCurrentFocus"))
    return m.group(1).strip() if m else ""


def texts(root):
    return [n.get("text", "") for n, _ in nodes(root)] if root is not None else []


def play_services_prompt(root):
    """Google Play services asking to be updated (the emulators' copies are old)"""
    return any("update Google Play services" in t or t == "Update Google Play services" for t in texts(root))


@step("Continue with Google opens Google's window, and the app works after it closes")
def s_google():
    since = sh("date +'%m-%d %H:%M:%S.000'").strip()
    tap("Continue with Google")
    # The emulator has no Google account: Google's window opens (to add one), or the app says what happened
    seen, message, update = [], None, False
    for _ in range(25):
        time.sleep(1)
        root = dump()
        f = focus()
        if f and f not in seen:
            seen.append(f)
        if play_services_prompt(root):
            update = True
            break
        # A hung launcher can hold the focus while the app is in front: the app's own text says where we are
        if f and "MainActivity" not in f and "Continue with Google" not in texts(root):
            break
        message = next((t for t in texts(root) if "Google" in t and ("sign-in" in t.lower() or "account" in t)
                        and t != "Continue with Google"), None)
        if message:
            break
    shot("01c-google")
    # Everything logged since the tap: Google Play services' reasons only appear there
    log = run(["adb", "logcat", "-d", "-v", "time", "-T", since], 60).stdout
    with open(os.path.join(OUT, "01c-google-log.txt"), "w") as f:
        f.write("\n".join(log.splitlines()[:2000]).replace(PASSWORD, "<password>") + "\n")
    for _ in range(3):  # close Google's window, or Play services' prompt to update
        root = dump()
        if ("MainActivity" in focus() or "Continue with Google" in texts(root)) and not play_services_prompt(root):
            break
        sh("input keyevent KEYCODE_BACK")
        time.sleep(3)
    # Back in the app, it must not be left waiting: the Sign in button shows its text again
    if not wait("Sign in", timeout=40, quiet=True):
        shot("01d-google-stuck")
        sh("am force-stop " + PKG)
        sh("am start -W -n " + ACT, 60)
        wait("Continue with Google", timeout=40)
        raise Fail("still waiting for Google after its window closed (windows: %s)" % ", ".join(seen))
    shot("01d-google-closed")
    if update:
        return "this emulator's Google Play services is too old for Google sign-in (it asks to be updated)"
    return ("message: " + message) if message else "windows: " + ", ".join(seen)


@step("Create an account", critical=True)
def s_signup():
    tap("Create an account")
    wait("I have an account")
    # Where the fields and the button are, read before a field has focus: older Androids can't read the
    # screen while a text field's cursor blinks
    root = dump()
    f, button = fields(root), find("Create account", root=root)
    if len(f) < 2 or button is None:
        raise Fail("didn't find the email and password fields and the Create account button")
    fill(f[0], EMAIL)
    fill(f[1], PASSWORD, check=False)  # hidden, so it can't be checked
    sh("input keyevent KEYCODE_ENTER")  # Done on the keyboard creates the account
    if not wait("History", timeout=30, quiet=True):
        hide_keyboard()
        sh("input tap %d %d" % button)
        wait("History", timeout=60)
    time.sleep(3)
    shot("02-today-new-account")


@step("Turn on reminders from the card on Today")
def s_reminders():
    wait("Get reminders", timeout=10)
    tap("Turn on")
    time.sleep(2)
    if present("Allow"):
        tap("Allow", lowest=True)
    wait("Reminders are on", contains=True, timeout=15)
    shot("03-reminders-on")


@step("Import a month of sleep from a CSV file")
def s_import():
    data, n = csv_for_device()
    with open(os.path.join(OUT, "sleep-ci.csv"), "w") as f:
        f.write(data)
    run(["adb", "push", os.path.join(OUT, "sleep-ci.csv"), "/sdcard/Download/sleep-ci.csv"])
    sh("content call --uri content://media --method scan_volume --arg external_primary", 120)
    sh("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sleep-ci.csv")
    time.sleep(2)
    nav("Settings")
    for _ in range(6):
        if present("Import CSV"):
            break
        swipe_up()
    tap("Import CSV")
    time.sleep(3)
    if not present("sleep-ci.csv"):
        if present(desc="Show roots"):
            tap(desc="Show roots")
            tap("Downloads")
        time.sleep(2)
    shot("04a-file-picker")
    tap("sleep-ci.csv", timeout=20)
    wait("Imported %d" % n, contains=True, timeout=20)
    shot("04b-imported")
    NIGHTS[0] = n
    return "%d nights in the file" % n


@step("Today, with data")
def s_today():
    back_to_app_main()
    nav("Today")
    time.sleep(2)
    shot("05a-today")
    swipe_up()
    shot("05b-today-lower")


@step("Trends: month, week, 3 days, patterns")
def s_trends():
    nav("Trends")
    time.sleep(1)
    shot("06a-trends-month")
    tap("Week")
    shot("06b-trends-week")
    tap("3 days")
    shot("06c-trends-3-days")
    tap("Month")
    swipe_up()
    shot("06d-trends-patterns")
    swipe_up()
    shot("06e-trends-quality")


@step("Schedule")
def s_schedule():
    nav("Schedule")
    time.sleep(1)
    shot("07a-schedule")
    swipe_up()
    shot("07b-schedule-diary")
    swipe_up()
    shot("07c-schedule-settings")


@step("History, editing an entry, and Add entry")
def s_history():
    nav("History")
    time.sleep(1)
    shot("08a-history")
    # The short run has no entries to edit
    if FULL or present(desc="Edit sleep from", contains=True):
        tap(desc="Edit sleep from", contains=True)
        wait("Edit sleep")
        shot("08b-edit-entry")
        tap("Cancel")
    tap(desc="Add entry")
    wait("Add sleep")
    shot("08c-add-entry")
    tap("Cancel")


@step("Settings, all of it")
def s_settings():
    nav("Settings")
    time.sleep(1)
    shot("09a-settings")
    for i in range(4):
        swipe_up()
        shot("09%s-settings" % "bcde"[i])


@step("Health Connect: turn on saving")
def s_health():
    if present("Health Connect isn't available on this phone."):
        return "not on this phone"
    for _ in range(4):
        if present(desc="Save sleep to Health Connect"):
            break
        swipe_up()
    tap(desc="Save sleep to Health Connect")
    time.sleep(3)
    shot("10a-health-permission")
    for label in ["Get started", "Allow all", "Allow"]:
        if present(label):
            tap(label, lowest=True)
            time.sleep(2)
    if present("Allow"):
        tap("Allow", lowest=True)
    wait("Nothing is read from Health Connect", contains=True, timeout=30)
    shot("10b-health-on")
    # What the app has saved there, from its own notes (each entry's id and version)
    ensure_root()
    n = 0
    for _ in range(20):
        prefs = sh("cat /data/data/%s/shared_prefs/health.xml" % PKG)
        m = re.search(r'<set name="saved">(.*?)</set>', prefs, re.S)
        n = len(re.findall(r"<string>", m.group(1))) if m else 0
        if n >= NIGHTS[0] > 0:
            break
        time.sleep(2)
    if n < NIGHTS[0]:
        raise Fail("%d of %d entries were saved to Health Connect" % (n, NIGHTS[0]))
    return "%d entries saved" % n


@step("Export CSV opens the share sheet")
def s_export():
    # Backup is above Health Connect: look further down, then back up
    for _ in range(4):
        if present("Export CSV"):
            break
        swipe_up()
    for _ in range(6):
        if present("Export CSV"):
            break
        swipe_down()
    tap("Export CSV")
    time.sleep(3)
    shot("11-export-share-sheet")
    pkg = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1)
    return pkg.strip().splitlines()[0][:160] if pkg.strip() else ""


@step("Reminders are scheduled with Android")
def s_alarms():
    back_to_app_main()
    time.sleep(3)
    n = sh("dumpsys alarm").count(PKG)
    if n == 0:
        raise Fail("no alarms for " + PKG)
    return "%d alarm lines" % n


@step("Shortcuts are set")
def s_shortcuts():
    d = sh("dumpsys shortcut")
    i = d.find("Package: " + PKG)
    part = d[i:i + 6000] if i >= 0 else ""
    ids = re.findall(r"id=(sleep|awake|add)\b", part)
    if "add" not in ids:
        raise Fail("dynamic shortcuts not found")
    sh("am start -n %s -a com.resz073.sleeptracker.OPEN_add --es reminder-action add" % ACT)
    wait("Add sleep", timeout=15)
    shot("12-shortcut-add-entry")
    tap("Cancel")
    return "ids " + ",".join(sorted(set(ids)))


@step("Quick Settings tile starts and ends a sleep")
def s_tile():
    comp = PKG + "/com.resz073.sleeptracker.widget.SleepTile"
    sh("cmd statusbar add-tile " + comp)
    time.sleep(1)
    sh("cmd statusbar click-tile " + comp)
    wait("Sleep started at", contains=True, timeout=20)
    shot("13a-tile-sleep-started")
    wait("Sleeping since", timeout=10)
    sh("cmd statusbar click-tile " + comp)
    wait("Under 10 minutes", contains=True, timeout=20)
    shot("13b-tile-awake")


@step("A bedtime reminder, and its Going to sleep button")
def s_notification():
    ensure_root()
    sh("am broadcast -n %s/com.resz073.sleeptracker.remind.ReminderReceiver -a com.resz073.sleeptracker.REMIND "
       "--ei reminder-id 110 --es reminder-title 'Time for bed' --es reminder-body 'Tap Going to sleep when you lie down.' "
       "--es reminder-kind bed --es reminder-type BEDTIME" % PKG)
    time.sleep(2)
    sh("cmd statusbar expand-notifications")
    wait("Time for bed", timeout=15)
    shot("14a-reminder")
    tap("Going to sleep", nocase=True, lowest=True)
    wait("Sleep started at", contains=True, timeout=20)
    shot("14b-reminder-going-to-sleep")
    tap("Cancel")
    time.sleep(1)


@step("Dark mode: every tab and the entry form")
def s_dark():
    back_to_app_main()
    sh("cmd uimode night yes")
    time.sleep(4)
    try:
        for tab, name in [("Today", "16a-dark-today"), ("Trends", "16b-dark-trends"), ("Schedule", "16c-dark-schedule"),
                          ("History", "16d-dark-history"), ("Settings", "16e-dark-settings")]:
            nav(tab)
            time.sleep(1.5)
            shot(name)
        nav("History")
        tap(desc="Add entry")
        wait("Add sleep")
        shot("16f-dark-add-entry")
        tap("Cancel")
        nav("Today")
        time.sleep(1)
        swipe_up()
        shot("16g-dark-today-lower")
    finally:
        sh("cmd uimode night no")
        time.sleep(4)


@step("Delete the account", critical=True)
def s_delete():
    back_to_app_main()
    # Settings keeps its place while it's open: from another tab it opens at the top, where the account is
    nav("Today")
    nav("Settings")
    for _ in range(8):
        if present("Delete account"):
            break
        swipe_down()
    tap("Delete account")
    time.sleep(1)
    # Read before the password field has focus (see Create an account)
    root = dump()
    f, button = fields(root), find("Delete account", root=root, lowest=True)
    if f:
        sh("input tap %d %d" % f[-1])
        time.sleep(0.8)
        type_text(PASSWORD)
        hide_keyboard()
    shot("15a-delete-confirm")
    if button:
        sh("input tap %d %d" % button)
    else:
        tap("Delete account", lowest=True)
    wait("Your account and sleep data were deleted.", timeout=60)
    shot("15b-deleted")


@step("No crashes")
def s_crashes():
    c = crashed()
    if c:
        raise Fail("crash log: " + " | ".join(c[:6]))


def main():
    steps = [s_open, s_google, s_signup, s_reminders, s_import, s_today, s_trends, s_schedule, s_history, s_settings,
             s_health, s_export, s_alarms, s_shortcuts, s_tile, s_notification, s_dark, s_delete, s_crashes] if FULL else \
            [s_open, s_google, s_signup, s_today, s_trends, s_schedule, s_history, s_settings, s_dark, s_delete, s_crashes]
    try:
        for s in steps:
            s()
    except Exception:
        pass
    finally:
        log = run(["adb", "logcat", "-d"], 120).stdout
        with open(os.path.join(OUT, "logcat.txt"), "w") as f:
            f.write(log)
        with open(os.path.join(OUT, "crash-log.txt"), "w") as f:
            f.write(run(["adb", "logcat", "-d", "-b", "crash"], 60).stdout)
    lines = ["### Emulator test, Android API %s%s" % (API, "" if FULL else " (short)"), "", "| Step | Result | Note |", "|---|---|---|"]
    for name, ok, note in results:
        lines.append("| %s | %s | %s |" % (name, "✅" if ok else "❌", note.replace("|", "/")[:200]))
    summary = "\n".join(lines) + "\n"
    print(summary)
    with open(os.path.join(OUT, "summary.md"), "w") as f:
        f.write(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(summary)
    bad = [r for r in results if not r[1]]
    for name, _, note in bad:
        print("::error title=Emulator API %s: %s::%s" % (API, name, note[:300]))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
