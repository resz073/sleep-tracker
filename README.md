# Sleep Tracker for Android

Log your sleep in two taps, see your sleep debt against a goal that fits your age, and get a bedtime worked out from your usual wake-up time. Accounts and sync run on Firebase. Reminders are scheduled on the phone.

The app is written in Kotlin with Jetpack Compose, in `native/`. GitHub builds, tests and publishes it, so you don't need Android Studio.

## Get the app

Install it from your Google Play test track, or from GitHub: open the [Releases](https://github.com/resz073/sleep-tracker/releases) page on your phone, tap the newest release, and download `sleep-tracker.apk`. Android will ask you to allow installs from your browser. Play Protect may say it doesn't recognise the app: tap **More details → Install anyway**.

Since 30 September 2026, phones in Brazil, Indonesia, Singapore and Thailand only install apps from verified developers. There, install the app from Google Play.

A copy installed from GitHub and one installed from Google Play are signed with different keys, so Android won't update one with the other. Uninstall first to switch.

## How a build works

Every change to the app on `main` starts a build. You can also start one yourself: **Actions → Build Android app → Run workflow**. A build:

1. runs the tests of the sleep engine, the screens and sync;
2. builds the app and signs it with your upload key;
3. publishes a release, **Sleep Tracker 2.0.0 (build N)**, with `sleep-tracker.apk` (for phones) and `sleep-tracker.aab` (for Google Play);
4. uses the APK on two Android emulators (see [Tests](#tests)).

It takes about 20 minutes, and the release is ready after about 10. Each build's version code is 1000 plus its build number, so every build can go to Google Play. The version name is set in `native/app/build.gradle.kts`.

The build's summary page says which Firebase project the app uses and which signing keys are registered there for Google sign-in. If a secret is wrong, the build stops and the summary says which one and how to fix it.

## Release on Google Play

Upload `sleep-tracker.aab` from the newest release to your track in [Play Console](https://play.google.com/console). The release notes and store listing are in [`store/listing.md`](store/listing.md), with the icon, feature graphic and screenshots in [`store/`](store/). People updating from the earlier version of the app sign in again once, and their entries come back from their account.

- **Google sign-in in the Play version.** Google Play signs the app with its own key, and Google sign-in only works when that key is registered in Firebase. In Play Console, open **Test and release → App integrity → App signing** and copy the **SHA-1** under **App signing key certificate**. In Firebase, open **Project settings**, the Android app **com.resz073.sleeptracker**, then **Add fingerprint**. It works within minutes, without a new build.
- **Health Connect.** In **Policy and programs → App content → Health apps**, choose **Sleep Management** and answer the Health Connect questions: the app **writes Sleep** sessions, so other health apps the person allows can see their sleep, and reads nothing. Google reviews the answers, and until then the Play version can't connect to Health Connect.
- **Production.** New personal developer accounts must run a closed test with at least 12 testers who stay opted in for 14 days in a row. After that, **Dashboard → Apply for production**.

The answers for **App content**:

- **Privacy policy:** `https://resz073.github.io/sleep-tracker/privacy.html` (also the policy Health Connect links to)
- **App access:** sign-in is needed, so give reviewers the email and password of an account made in the app.
- **Ads:** no ads.
- **Target audience:** 13–15, 16–17 and 18 and over. Not under 13.
- **Data safety:** data is encrypted in transit; users can ask for deletion; nothing is shared. Collected, for app functionality and account management:
  - Personal info → **Email address** (required)
  - Personal info → **Name** (optional: only with Google sign-in)
  - Personal info → **User IDs** (required)
  - Personal info → **Other info** (birth year, optional)
  - Health and fitness → **Health info** (sleep times and ratings, required)

  What the app saves to Health Connect stays on the phone, so it isn't collected.
- **Delete account URL:** `https://resz073.github.io/sleep-tracker/delete-account.html`

## What it's set up with

All of this is already done. It's here in case you need to set it up again.

- **Website.** The privacy policy and account-deletion pages are in `docs/`, published with GitHub Pages (**Settings → Pages**: branch **main**, folder **/docs**). Google Play links to them.
- **Firebase** project `sleep-tracker-81097`:
  - **Authentication** with **Email/Password** and **Google** turned on.
  - **Firestore Database** with the rules in [`firestore.rules`](firestore.rules): each person can only read and write their own data.
  - The Android app **com.resz073.sleeptracker**, with the SHA-1 of every key that signs it: the upload key `55:7A:E9:C2:97:4C:FA:45:90:EA:02:46:7B:2B:60:9C:D1:18:13:1B`, the public test key `7B:B3:FD:4A:2C:B3:C2:EF:25:47:5D:88:A1:D3:69:3C:3E:35:3B:4C`, and Google Play's app signing key (see above).
- **GitHub secrets** (**Settings → Secrets and variables → Actions**):

| Name | Value |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | Everything in `google-services.json`, downloaded from the Android app's page in Firebase |
| `KEYSTORE_BASE64` | The upload key file in base64: one line of about 3,500 characters (in `sleep-tracker-signing.txt`) |
| `KEYSTORE_PASSWORD` | The upload key's password (in `sleep-tracker-signing.txt`) |
| `KEY_ALIAS` | Optional: the key's name in the key file, if it isn't `upload` |

Without the key secrets, builds are test builds (`sleep-tracker-test.apk`), signed with the public test key in `ci/test-key.p12` (password `android`). Never use that key for Google Play. Without `GOOGLE_SERVICES_JSON`, the app can't sign in, and says so.

The emulator tests create a throwaway account (`sleep-ci-…@example.com`) and delete it at the end. If a test stops halfway, a cleanup step deletes it anyway.

## Keep these safe

- **The upload key and its password.** Store both in a password manager. Google Play only accepts updates signed with this key. If it's lost, you can ask Google for an upload key reset in Play Console.
- **The package name** `com.resz073.sleeptracker` can't change once the app is on Google Play.

## How the app works

The app has five tabs (Today, Trends, Schedule, History and Settings) and a form to add and edit entries. You can set your goal, birth year, wake-up time, time to fall asleep and weekend days. Dark mode uses warm, dim colours with no blue in them. Light mode takes its colours from your wallpaper on Android 12 and later.

- **Reminders** (Settings, or the card on Today): wind-down 30 minutes before tonight's bedtime, bedtime with a **Going to sleep** button, and a morning reminder with **Log it** while last night isn't logged. They're planned for the next 7 days and planned again whenever your data or schedule changes, when one arrives, after the phone restarts, and when the clock or time zone changes, so they keep coming without opening the app. On Android 14 and later, on-time reminders need the **Alarms & reminders** permission. Settings offers it; without it, Android may send them up to an hour late.
- **Backup**: **Export CSV** shares a file of your entries; **Import CSV** adds entries from such a file, skipping ones already logged.
- **Delete account**: after you confirm with your password (or Google), your sleep data and your account are deleted, along with this phone's copy, its reminders and what the app saved to Health Connect.
- **Home screen widget** (Bedtime): tonight's bedtime (or the sleep in progress), the last 14 days, and a **Going to sleep** / **I'm awake** button.
- **Shortcuts**: long-press the app icon for **Going to sleep** (or **I'm awake** while asleep) and **Add entry**.
- **Quick Settings tile**: **Going to sleep**, which changes to **I'm awake** while you're asleep.
- **Health Connect** (Settings, Android 9 and later): your entries are saved there as sleep sessions, so other health apps you allow can see them. Sessions are updated when you edit an entry and removed when you delete one. The app only writes to Health Connect; it reads nothing.

The widget's button, the shortcuts, the tile and the reminders' buttons open the app to start or end the sleep, so the change saves and syncs as usual.

### Sync and offline

Each account's data is one Firestore document (`users/{uid}`). The app keeps a copy on the phone, so it opens without internet (`engine/Sync.kt`):

- Every change is saved on the phone first, so nothing is lost offline or if the app closes.
- Whenever the account's copy arrives, it's merged in entry by entry. The newest change of each entry wins, so changes made on any of your devices are kept.
- When the phone has something the account lacks, the merged copy is written back in a single step (a Firestore transaction) that re-reads the account first. If another device saves at the same moment, both changes are kept.
- Settings shows whether everything is synced. Signing out is refused while some changes are only on the phone.

### The code

In `native/app/src/main/java/com/resz073/sleeptracker/`:

| Folder | What it does |
| --- | --- |
| `engine/` | Every sleep calculation: how sleep splits across midnight, nights and naps, complete days, nights not logged, sleep debt and averages, the goal by age, the bedtime plan, patterns, trends periods, history weeks, reminder times, CSV, and the entry form's checks |
| `show/` | What each screen shows, worked out from the engine: its text and every chart's shapes |
| `ui/` | The Compose screens, light and dark |
| `data/` | Firebase accounts and sync, Google sign-in, the copy on the phone, Health Connect, CSV files |
| `remind/` | Reminders and their notifications |
| `widget/` | The widget, shortcuts and tile |

## Tests

Every build runs these before it publishes anything:

- **Sleep engine and screens**: 78 test accounts in 6 time zones, including daylight-saving changes, with every result saved in `native/app/src/test/resources/golden/` (recorded from the earlier version of the app, so the two work the same). Each screen's text, its bold and coloured parts, and for 19 accounts every chart, shape by shape, must match. When you change a calculation on purpose, update the saved results it differs from: the failing test lists each difference.
- **Sync**: every button and setting (what it saves, and its message), merging two copies, and offline situations against a stand-in account: reconnecting, another device saving at the same moment, restarts, a damaged copy on the phone, and no endless writes.

Then the signed APK is installed on two Android emulators and used the way a person would (`native/scripts/emulator/drive.py`):

- **Android 14**: checks that **Continue with Google** opens Google's window and that the app works again after it closes, creates an account, turns on reminders, imports a month of sleep from a CSV file, looks at every screen and the entry form, turns on Health Connect and checks every entry was saved there, exports a CSV file, checks the reminders and shortcuts Android has, starts and ends a sleep with the tile and with a reminder's button, looks at every tab and the entry form in dark mode, and deletes the account.
- **Android 9**: the same Google check, then creates an account, looks at each screen in light and dark mode, and deletes the account.

A crash anywhere fails the test. The screenshots and the text on each screen are kept on the `emulator-api-34` and `emulator-api-28` branches, replaced by each build.

## What's in this repository

| Path | What it is |
| --- | --- |
| `native/` | The app |
| `.github/workflows/native.yml` | The build: tests, build, signing, release, emulator tests |
| `scripts/` | Signing (`sign.sh`) and the build's helpers |
| `firestore.rules` | Database rules |
| `docs/` | Website: home page, privacy policy, account deletion |
| `store/` | Google Play listing text, icon, feature graphic and screenshots |
| `ci/test-key.p12` | Public key for test builds |

## Building on your own computer (optional)

You need JDK 21, Node 22+ and Android Studio (or the Android SDK).

```sh
cp ~/Downloads/google-services.json native/app/   # optional: without it the app can't sign in
node native/scripts/firebase-config.mjs           # writes native/app/firebase.properties
cd native
./gradlew assembleDebug                           # the APK is in app/build/outputs/apk/debug/
bash scripts/unit-tests.sh                        # the engine, screen and sync tests
```

Or open `native/` in Android Studio. `google-services.json` and `firebase.properties` stay out of git.

Sleep Tracker is not a medical device and does not give medical advice.
