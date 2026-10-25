# Sleep Tracker for Android

Log your sleep in two taps, see your sleep debt against an 8-hour goal, and get a bedtime worked out from your usual wake-up time. Accounts and sync run on Firebase. Reminders are scheduled on the phone.

The app is a web app wrapped for Android with [Capacitor](https://capacitorjs.com). GitHub builds it for you, so you don't need Android Studio.

## Get the app on your phone

Every change to `main` builds the app. You can also start a build yourself: **Actions → Build Android app → Run workflow**. A build takes about 10 minutes.

When it finishes, open the [Releases](https://github.com/resz073/sleep-tracker/releases) page on your phone, tap the newest release, and download the `.apk` file. Android will ask you to allow installs from your browser. Play Protect may say it doesn't recognise the app: tap **More details → Install anyway**.

Since 30 September 2026, phones in Brazil, Indonesia, Singapore and Thailand only install apps from verified developers. If you're there, install the app from a Google Play test track instead (see [Publish on Google Play](#publish-on-google-play)).

Until you finish the setup below, builds are **test builds** (`sleep-tracker-test.apk`). They work fully, but without accounts: entries stay on the phone.

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
| `FIREBASE_WEB_CONFIG` | The `firebaseConfig` snippet from step 2.5 (paste all of it) |
| `GOOGLE_SERVICES_JSON` | Everything in `google-services.json` (open it in a text editor) |
| `KEYSTORE_BASE64` | Everything in `KEYSTORE_BASE64.txt`: one line of about 3,550 characters (it's also in `sleep-tracker-signing.txt`) |
| `KEYSTORE_PASSWORD` | The `KEYSTORE_PASSWORD` from `sleep-tracker-signing.txt` |

If a secret is wrong, the build stops and its summary page says which one and how to fix it. To change a secret, open it and choose **Update secret**.

Then run the build again (**Actions → Build Android app → Run workflow**). The new release has `sleep-tracker.apk` (for your phone) and `sleep-tracker.aab` (for Google Play), signed with your upload key, with accounts turned on.

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
   - **Target audience:** 18 and over (add 13–17 if you want teens). Don't include under 13.
   - **Data safety:** data is encrypted in transit; users can ask for deletion; nothing is shared. Collected, for app functionality and account management:
     - Personal info → **Email address** (required)
     - Personal info → **Name** (optional: only with Google sign-in)
     - Health and fitness → **Health info** (sleep times and ratings, required)
   - **Delete account URL:** `https://resz073.github.io/sleep-tracker/delete-account.html`
   - **Health apps:** choose **Sleep Management**.
4. **Store listing:** text and images are ready in [`store/`](store/).
5. **Closed test first.** New personal developer accounts must run a closed test with at least 12 testers who stay opted in for 14 days in a row before they can apply for production. **Test and release → Testing → Closed testing → Create track**, upload `sleep-tracker.aab`, add your testers' emails, and send them the opt-in link.
6. **Add Google's signing key to Firebase.** Google Play re-signs the app with its own key, so Google sign-in won't work in the Play version until you do this. In Play Console, open **App integrity** (in the Test and release section) → **App signing**, and copy the **App signing key certificate SHA-1**. In Firebase, add it as another fingerprint on the Android app, download `google-services.json` again, and update the `GOOGLE_SERVICES_JSON` secret. Rebuild and upload the new AAB.
7. After 14 days, **Dashboard → Apply for production**.

Every upload to Play needs a higher version code. Each build uses its build number, so just upload a newer build.

## Keep these safe

- **The upload key and its password.** Store both in a password manager. Google Play only accepts updates signed with this key. If it's lost, you can ask Google for an upload key reset in Play Console.
- **The package name** `com.resz073.sleeptracker` can't change once the app is on Play.

`ci/test-key.p12` is a public test key (password `android`) used only for test builds. Never use it for Google Play.

## What's in this repository

| Path | What it is |
| --- | --- |
| `src/` | The app: screens (`index.html`, `styles.css`) and logic (`app.js`), Firebase (`backend.js`), Android features (`platform.js`) |
| `scripts/` | Build helpers: Firebase settings, bundling, Android icons and settings |
| `.github/workflows/android.yml` | The build that runs on GitHub |
| `firestore.rules` | Database rules: each person can only read and write their own data |
| `docs/` | Website: home page, privacy policy, account deletion |
| `store/` | Google Play listing text, icon, feature graphic and screenshots |
| `ci/test-key.p12` | Public key for test builds |

## Building on your own computer (optional)

You need Node 22+, JDK 21 and Android Studio.

```sh
npm install
npm run config         # Firebase settings: put google-services.json in this folder first, or a placeholder is used
npm run build          # bundles src/ into www/
npm run android        # creates android/ and applies the patches
npx cap open android   # opens Android Studio
```

`npm run config` also reads `FIREBASE_WEB_CONFIG` if you set it in your shell. Without it, `src/firebase-config.js` stays empty and the app runs without accounts. After changing anything in `src/`, run `npm run build && npx cap sync android`.

Sleep Tracker is not a medical device and does not give medical advice.
