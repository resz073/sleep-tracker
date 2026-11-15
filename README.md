# Sleep Tracker for Android

Log your sleep in two taps, see your sleep debt against a goal that fits your age, and get a bedtime worked out from your usual wake-up time. Accounts and sync run on Firebase. Reminders are scheduled on the phone.

The app is a native Android app written in Kotlin with Jetpack Compose, in `native/`. GitHub builds and tests it for you, so you don't need Android Studio.

Version 1 was a web app wrapped for Android with [Capacitor](https://capacitorjs.com), in `src/`. Version 2, the native app, replaced it with the same package name, so it installs as an update. The web app's code stays here because the native app is tested against it (see [Tests](#tests)).

## Get the app on your phone

Every change to the app on `main` builds it. You can also start a build yourself: **Actions → Build Android app → Run workflow**. A build takes about 10 minutes, then the app is tried on two Android emulators.

When it finishes, open the [Releases](https://github.com/resz073/sleep-tracker/releases) page on your phone, tap the newest release, and download `sleep-tracker.apk`. Android will ask you to allow installs from your browser. Play Protect may say it doesn't recognise the app: tap **More details → Install anyway**.

Since 30 September 2026, phones in Brazil, Indonesia, Singapore and Thailand only install apps from verified developers. If you're there, install the app from a Google Play test track instead (see [Publish on Google Play](#publish-on-google-play)).

Until you finish the setup below, builds are **test builds** (`sleep-tracker-test.apk`), signed with a public test key. Without the Firebase settings the app can't sign in, and says so.

> If you move from a test build to a build signed with your upload key, uninstall the test build first (Android won't update an app signed with a different key). Export your entries before uninstalling: **Settings → Backup → Export CSV**.

## One-time setup

### 1. Turn on the website

The privacy policy and account-deletion pages live in `docs/`. Google Play needs their links.

**Settings → Pages →** Source: **Deploy from a branch**, Branch: **main**, folder **/docs → Save**.

A minute later these work:

- https://resz073.github.io/sleep-tracker/privacy.html
- https://resz073.github.io/sleep-tracker/delete-account.html

### 2. Create the Firebase project

1. Go to the [Firebase console](https://console.firebase.google.com) and create a project called **Sleep Tracker**. Google Analytics isn't needed.
2. Open **Authentication** from the left menu and click **Get started**. On **Sign-in method**, turn on **Email/Password**, then **Google** (pick your support email). Save each one.
3. Open **Firestore Database** and click **Create database**. Choose the location closest to you (it can't be changed later) and **production mode**. Then open the **Rules** tab, replace everything with the contents of [`firestore.rules`](firestore.rules), and **Publish**.
4. **Project settings** (gear icon) → **Your apps → Add app → Android.**
   - Package name: `com.resz073.sleeptracker`
   - SHA-1: the **upload key** SHA-1 from `sleep-tracker-signing.txt` (the signing-key notes you were given). The form may call this field "Debug signing certificate"; paste it there anyway.
   - Register, then **download `google-services.json`**. Skip the remaining steps.
   - Back on the Android app's card, **Add fingerprint** and also add the **test key** SHA-1: `7B:B3:FD:4A:2C:B3:C2:EF:25:47:5D:88:A1:D3:69:3C:3E:35:3B:4C`
5. **Add app → Web** (`</>`), nickname **Sleep Tracker web**, register, and copy the `firebaseConfig` snippet it shows.

Turn on Google sign-in (step 2) before downloading `google-services.json`. If you downloaded it first, download it again.

### 3. Add the secrets to GitHub

In this repository, **Settings → Secrets and variables → Actions → New repository secret.** Add these four:

| Name | Value |
| --- | --- |
| `FIREBASE_WEB_CONFIG` | The `firebaseConfig` snippet from step 2.5 (paste all of it). Only the web app (version 1) uses it. |
| `GOOGLE_SERVICES_JSON` | Everything in `google-services.json` (open it in a text editor) |
| `KEYSTORE_BASE64` | Everything in `KEYSTORE_BASE64.txt`: one line of about 3,500 characters (it's also in `sleep-tracker-signing.txt`) |
| `KEYSTORE_PASSWORD` | The `KEYSTORE_PASSWORD` from `sleep-tracker-signing.txt` |

If a secret is wrong, the build stops and its summary page says which one and how to fix it. To change a secret, open it and choose **Update secret**.

Then run the build again (**Actions → Build Android app → Run workflow**). The new release has `sleep-tracker.apk` (for your phone) and `sleep-tracker.aab` (for Google Play), signed with your upload key, with accounts turned on.

The emulator tests create a throwaway account (`sleep-ci-…@example.com`) and delete it at the end. If a test stops halfway, a cleanup step deletes the account anyway.

### 4. Move your old entries over

In the Sleep Tracker you used before, open **Settings → Backup → Export CSV**. In the app, sign in, then **Settings → Backup → Import CSV** and pick that file. Entries already in the app are skipped.

## Publish on Google Play

1. **Create a developer account** at [Play Console](https://play.google.com/console) (one-time fee and identity check).
2. **Create app:** name *Sleep Tracker*, default language, **App**, **Free**.
3. **Fill in App content** (Policy and programs → App content):
   - **Privacy policy:** `https://resz073.github.io/sleep-tracker/privacy.html`
   - **App access:** sign-in is needed, so create an email account in the app for reviewers and enter its email and password here.
   - **Ads:** no ads.
   - **Content rating:** fill in the questionnaire.
   - **Target audience:** 13–15, 16–17 and 18 and over. Don't include under 13.
   - **Data safety:** data is encrypted in transit; users can ask for deletion; nothing is shared. Collected, for app functionality and account management:
     - Personal info → **Email address** (required)
     - Personal info → **Name** (optional: only with Google sign-in)
     - Personal info → **User IDs** (required)
     - Personal info → **Other info** (birth year, optional)
     - Health and fitness → **Health info** (sleep times and ratings, required)
   - **Delete account URL:** `https://resz073.github.io/sleep-tracker/delete-account.html`
   - **Health apps:** choose **Sleep Management**.
   - **Health Connect:** in the same **Health apps** form, answer the Health Connect questions: the app **writes Sleep** sessions, so other health apps the person allows can see their sleep, and reads nothing. After you upload an AAB that uses Health Connect, **App content** lists it under **Needs attention**. Google reviews the answers, and until then the Play version can't connect to Health Connect. The privacy policy covers it (`docs/privacy.html#health-connect`); it has to be the same policy as on the store listing.
4. **Store listing:** text and images are ready in [`store/`](store/).
5. **Closed test first.** New personal developer accounts must run a closed test with at least 12 testers who stay opted in for 14 days in a row before they can apply for production. **Test and release → Testing → Closed testing → Create track**, upload `sleep-tracker.aab`, add your testers' emails, and send them the opt-in link.
6. **Add Google's signing key to Firebase.** Google Play re-signs the app with its own key, so Google sign-in won't work in the Play version until you do this. In Play Console, open **App integrity** (in the Test and release section) → **App signing**, and copy the **App signing key certificate SHA-1**. In Firebase, add it as another fingerprint on the Android app, download `google-services.json` again, and update the `GOOGLE_SERVICES_JSON` secret. Rebuild and upload the new AAB.
7. After 14 days, **Dashboard → Apply for production**.

Every upload to Play needs a higher version code. Each build uses 1000 plus its build number (above every version 1 build), so just upload a newer build.

### Updating from version 1 on Play

Upload `sleep-tracker.aab` from a new build to the track that has version 1, like any update. Release notes you can use:

> Sleep Tracker is now a native Android app: faster, with a home screen widget, a Quick Settings tile, shortcuts, and the option to save your sleep to Health Connect. After updating, sign in again. Your entries are in your account.

Things to know:

- **Everyone signs in again once.** Version 1 kept its sign-in inside the web view, where the native app can't read it. Entries come back from the account as soon as they sign in.
- **Changes not yet synced are lost.** Version 1 saves to the account whenever the phone is online, so this only affects entries made offline just before updating. Opening version 1 once while online before updating avoids it.
- Reminders, the birth-year card's "Not now" and the open tab are kept on the phone, so they start fresh. Reminders are offered again on Today.

## Keep these safe

- **The upload key and its password.** Store both in a password manager. Google Play only accepts updates signed with this key. If it's lost, you can ask Google for an upload key reset in Play Console.
- **The package name** `com.resz073.sleeptracker` can't change once the app is on Play.

`ci/test-key.p12` is a public test key (password `android`) used only for test builds. Never use it for Google Play.

## What's in this repository

| Path | What it is |
| --- | --- |
| `native/` | The app (Kotlin, Jetpack Compose): see [How the app works](#how-the-app-works) |
| `.github/workflows/native.yml` | The build that runs on GitHub: tests, build, signing, release, emulator tests |
| `src/` | Version 1, the web app: screens (`index.html`, `styles.css`) and logic (`app.js`), Firebase (`backend.js`), Android features (`platform.js`). The native app is tested against `app.js`. |
| `scripts/` | Signing (`sign.sh`), and build helpers for the web app |
| `.github/workflows/android.yml` | The web app's build, kept for reference: it only runs with **Run workflow** |
| `firestore.rules` | Database rules: each person can only read and write their own data |
| `docs/` | Website: home page, privacy policy, account deletion |
| `store/` | Google Play listing text, icon, feature graphic and screenshots |
| `ci/test-key.p12` | Public key for test builds |

## How the app works

The app has four screens (Today, Trends, Schedule and History), adding and editing entries, and settings for the goal, birth year, wake-up time, time to fall asleep and weekend days. Entries save to your account in the same format version 1 used. It also has reminders, CSV backup and account deletion, a home screen widget, shortcuts and a Quick Settings tile, and saving your sleep to Health Connect.

### The sleep engine

All of the app's sleep calculations are in `native/app/src/main/java/com/resz073/sleeptracker/engine/`, ported from version 1's `src/app.js`. That covers how sleep splits across midnight, nights and naps, complete and incomplete days, nights not logged, sleep debt and averages, the goal by age, the bedtime plan and its steps, patterns, trends periods, history weeks, reminder times, CSV import and export, and the add-entry checks. The screens are drawn from it.

It's tested against version 1 itself. `native/scripts/golden/make.mjs` runs `src/app.js` on 78 test accounts in 6 time zones, including daylight-saving changes, and saves every result in `native/app/src/test/resources/golden/`. The Kotlin tests must match every value. Before each native build, GitHub runs those tests and checks that the saved results still match `src/app.js`. A change to `src/app.js` therefore fails the build until the Kotlin engine matches again.

To change how the app calculates something, change it in both, then refresh the saved results with `node native/scripts/golden/make.mjs`.

### Screens

Each screen shows what version 1 showed. `native/app/src/main/java/com/resz073/sleeptracker/show/` works out every screen's text and charts from the sleep engine, and the Compose screens in `ui/` lay them out in version 1's style, light or dark.

The same tests cover the screens. For every test account, the saved results include each screen's text as version 1 shows it (which parts are bold or coloured), and for 19 of them every chart, shape by shape: the bedtime strip, last night's ring, the 14-day bars, the Trends chart for each month, week and day picked, and the 14-night diary. The Kotlin side must produce the same.

### Reminders, backup and account deletion

These work as in version 1:

- **Reminders** (Settings, or the card on Today): wind-down 30 minutes before tonight's bedtime, bedtime with a **Going to sleep** button, and a morning reminder with **Log it** while last night isn't logged. They're planned for the next 7 days and planned again whenever your data or schedule changes. The app also plans them again when one arrives, after the phone restarts, and when the clock or time zone changes, so they keep coming without opening the app. On Android 14 and later, on-time reminders need **Alarms & reminders** permission. Settings offers it, and without it Android may send them up to an hour late.
- **Backup**: **Export CSV** shares a file in version 1's format; **Import CSV** adds entries from a file exported by either version, skipping ones already logged.
- **Delete account**: after you confirm with your password (or Google), your sleep data and your account are deleted, along with this phone's copy and its reminders.

### Widget, shortcuts, tile and Health Connect

These are new in version 2:

- **Home screen widget** (Bedtime): tonight's bedtime (or the sleep in progress), the last 14 days, and a **Going to sleep** / **I'm awake** button. It updates whenever your data changes.
- **Shortcuts**: long-press the app icon for **Going to sleep** (or **I'm awake** while asleep) and **Add entry**.
- **Quick Settings tile**: add **Going to sleep** to Quick Settings. While you're asleep it changes to **I'm awake**.
- **Health Connect** (Settings, Android 9 and later): your entries are saved there as sleep sessions, so other health apps you allow can see them. Saved sessions are updated when you edit an entry and removed when you delete one. The app only writes to Health Connect; it reads nothing. Deleting your account removes what the app saved there too.

The widget's button, the shortcuts and the tile open the app to start or end the sleep, like a reminder's buttons do. That way the change saves and syncs as usual.

Publishing with Health Connect needs a declaration in Play Console: see [Publish on Google Play](#publish-on-google-play).

### Sync and offline

The app keeps a copy of your data on the phone, so it opens without internet. It keeps that copy in step with your account the way version 1 did (`engine/Sync.kt`):

- Every change is saved on the phone first, so nothing is lost offline or if the app closes.
- Whenever your account's copy arrives, it's merged in entry by entry. The newest change of each entry wins, so changes saved on any of your devices are kept.
- When the phone has something your account lacks, the merged copy is written back in version 1's exact format, in a single step (a Firestore transaction) that re-reads the account first. If another device saves at the same moment, both changes are kept.
- Settings shows whether everything is synced. Signing out is refused while some changes are only on the phone.

The tests check every button and setting against version 1 (what each one saves, and its message), merges of two copies, and offline situations against a stand-in account: reconnecting, another device saving at the same moment, restarts, a damaged copy on the phone, and no endless writes.

### Tests

Each build runs the tests above, then installs the signed APK on two Android emulators and uses it the way a person would (`native/scripts/emulator/drive.py`):

- **Android 14**: checks that **Continue with Google** opens Google's window and that the app works again after it closes, creates an account, turns on reminders, imports a month of sleep from a CSV file, looks at every screen and the entry form, turns on Health Connect and checks every entry was saved there, exports a CSV file, checks the reminders and shortcuts Android has, starts and ends a sleep with the tile and with a reminder's button, and deletes the account.
- **Android 9**: the same Google check, then creates an account, looks at each screen, and deletes the account.

A crash anywhere fails the test. The screenshots and the text on each screen are kept on the `emulator-api-34` and `emulator-api-28` branches, replaced by each build.

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

The web app (version 1) still builds with `npm install`, `npm run config`, `npm run build` and `npm run android`.

Sleep Tracker is not a medical device and does not give medical advice.
