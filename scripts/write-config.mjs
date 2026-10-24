// Writes the Firebase settings for a build from two GitHub secrets:
//   FIREBASE_WEB_CONFIG   the "firebaseConfig" snippet from Firebase (Project settings > Your apps > Web app)
//   GOOGLE_SERVICES_JSON  the contents of google-services.json (Project settings > Your apps > Android app)
// Without them the app still builds: accounts are switched off and entries stay on the phone.
import { writeFileSync, readFileSync, existsSync } from "node:fs";

const appId = JSON.parse(readFileSync("capacitor.config.json", "utf8")).appId;

const web = (process.env.FIREBASE_WEB_CONFIG || "").trim();
if (web) {
  const pick = k => { const m = new RegExp(k + "[\"']?\\s*:\\s*[\"']([^\"']+)[\"']").exec(web); return m ? m[1] : undefined; };
  const cfg = {};
  for (const k of ["apiKey", "authDomain", "projectId", "storageBucket", "messagingSenderId", "appId"]) { const v = pick(k); if (v) cfg[k] = v; }
  if (!cfg.apiKey || !cfg.projectId) { console.error("FIREBASE_WEB_CONFIG is set but has no apiKey or projectId. Paste the whole firebaseConfig snippet."); process.exit(1); }
  writeFileSync("src/firebase-config.js", "export const firebaseConfig = " + JSON.stringify(cfg, null, 2) + ";\n");
  console.log("Accounts on: Firebase project " + cfg.projectId);
} else {
  console.log("No FIREBASE_WEB_CONFIG secret: building without accounts (entries stay on the phone).");
}

const gs = (process.env.GOOGLE_SERVICES_JSON || "").trim();
if (gs) {
  const text = gs.startsWith("{") ? gs : Buffer.from(gs, "base64").toString("utf8");
  const json = JSON.parse(text);
  const pkgs = (json.client || []).map(c => c.client_info && c.client_info.android_client_info && c.client_info.android_client_info.package_name);
  if (!pkgs.includes(appId)) { console.error("google-services.json has no Android app for " + appId + ". Add an Android app with that package name in Firebase."); process.exit(1); }
  writeFileSync("google-services.json", text);
  console.log("google-services.json added");
} else if (existsSync("google-services.json") && !readFileSync("google-services.json", "utf8").includes("sleep-tracker-placeholder")) {
  console.log("Using the google-services.json already in the project folder.");
} else {
  // The native Google sign-in plugin needs Firebase initialised to start, so a placeholder keeps the app
  // from crashing. Google sign-in won't work until the real file is added as a secret.
  writeFileSync("google-services.json", JSON.stringify({
    project_info: { project_number: "000000000000", project_id: "sleep-tracker-placeholder", storage_bucket: "sleep-tracker-placeholder.appspot.com" },
    client: [{
      client_info: { mobilesdk_app_id: "1:000000000000:android:0000000000000000", android_client_info: { package_name: appId } },
      oauth_client: [{ client_id: "000000000000-placeholder.apps.googleusercontent.com", client_type: 3 }],
      api_key: [{ current_key: "placeholder-api-key" }],
      services: { appinvite_service: { other_platform_oauth_client: [{ client_id: "000000000000-placeholder.apps.googleusercontent.com", client_type: 3 }] } }
    }],
    configuration_version: "1"
  }, null, 2));
  console.log("No GOOGLE_SERVICES_JSON secret: using a placeholder, so Google sign-in is off in this build.");
}
