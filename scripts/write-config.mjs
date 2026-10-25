// Writes the Firebase settings for a build from two GitHub secrets:
//   FIREBASE_WEB_CONFIG   the "firebaseConfig" snippet from Firebase (Project settings > Your apps > Web app)
//   GOOGLE_SERVICES_JSON  the contents of google-services.json (Project settings > Your apps > Android app)
// Without them the app still builds: accounts are switched off and entries stay on the phone.
// On GitHub, the result and any problem with the secrets show on the build's summary page.
import { writeFileSync, readFileSync, existsSync } from "node:fs";

const CI = !!process.env.GITHUB_ACTIONS;
const say = (level, title, msg) => console.log(CI ? `::${level} title=${title}::${msg}` : `${title}: ${msg}`);
const stop = (title, msg) => { say("error", title, msg); process.exit(1); };

const appId = JSON.parse(readFileSync("capacitor.config.json", "utf8")).appId;
const web = (process.env.FIREBASE_WEB_CONFIG || "").trim();
const gs = (process.env.GOOGLE_SERVICES_JSON || "").trim();

// google-services.json as pasted, with stray text around it, or as base64
function parseServices(s) {
  const tries = [s];
  const a = s.indexOf("{"), b = s.lastIndexOf("}");
  if (a >= 0 && b > a) tries.push(s.slice(a, b + 1));
  if (/^[A-Za-z0-9+/=\s]+$/.test(s)) tries.push(Buffer.from(s.replace(/\s+/g, ""), "base64").toString("utf8"));
  for (const t of tries) { try { const j = JSON.parse(t); if (j && typeof j === "object") return { json: j, text: t }; } catch (e) { /* next */ } }
  return null;
}

// ---- Web config: used by the app's screens ----
let webProject = null;
if (web) {
  if (/project_info|client_info/.test(web)) stop("FIREBASE_WEB_CONFIG", "This looks like google-services.json. This secret needs the firebaseConfig snippet from the Web app (Project settings, Your apps, the Web app). google-services.json goes in GOOGLE_SERVICES_JSON.");
  const pick = k => { const m = new RegExp(k + "[\"']?\\s*:\\s*[\"']([^\"']+)[\"']").exec(web); return m ? m[1].trim() : undefined; };
  const cfg = {};
  for (const k of ["apiKey", "authDomain", "projectId", "storageBucket", "messagingSenderId", "appId"]) { const v = pick(k); if (v) cfg[k] = v; }
  if (!cfg.apiKey || !cfg.projectId) stop("FIREBASE_WEB_CONFIG", "Couldn't find apiKey and projectId in it. Paste the whole firebaseConfig snippet from Project settings, Your apps, the Web app.");
  if (cfg.appId && !/:web:/.test(cfg.appId)) say("warning", "FIREBASE_WEB_CONFIG", "Its appId isn't a Web app's. Make sure you copied the snippet from the Web app (</>), not the Android app.");
  writeFileSync("src/firebase-config.js", "export const firebaseConfig = " + JSON.stringify(cfg, null, 2) + ";\n");
  webProject = cfg.projectId;
}

// ---- google-services.json: used by Android for Google sign-in ----
let services = null, project = null, googleOk = false;
if (gs) {
  if (/apiKey\s*["']?\s*:/.test(gs) && !/project_info/.test(gs)) stop("GOOGLE_SERVICES_JSON", "This looks like the web firebaseConfig snippet. This secret needs everything in google-services.json, from Project settings, Your apps, the Android app.");
  const p = parseServices(gs);
  if (!p || !p.json.project_info) stop("GOOGLE_SERVICES_JSON", "This isn't the contents of google-services.json. Open the file in a text editor, copy all of it, and paste it as the secret.");
  project = p.json.project_info.project_id;
  const client = (p.json.client || []).find(c => c?.client_info?.android_client_info?.package_name === appId);
  if (!client) stop("GOOGLE_SERVICES_JSON", `google-services.json has no Android app with the package name ${appId}. In Firebase, add an Android app with exactly that package name, then download the file again.`);
  if (webProject && project !== webProject) stop("Firebase settings", `FIREBASE_WEB_CONFIG is for the project "${webProject}" but google-services.json is for "${project}". Both must come from the same Firebase project.`);
  const oauth = [...(client.oauth_client || []), ...(client.services?.appinvite_service?.other_platform_oauth_client || [])];
  googleOk = oauth.some(o => o.client_type === 3);
  if (!googleOk) say("warning", "Google sign-in", "google-services.json has no web client ID, so Google sign-in won't work. In Firebase, turn on Google under Authentication, Sign-in method, then download google-services.json again and update the GOOGLE_SERVICES_JSON secret.");
  services = JSON.stringify(p.json, null, 2);
  writeFileSync("google-services.json", services);
} else if (existsSync("google-services.json") && !readFileSync("google-services.json", "utf8").includes("sleep-tracker-placeholder")) {
  services = readFileSync("google-services.json", "utf8"); googleOk = true;
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
}

// ---- Summary ----
if (webProject && services && googleOk) say("notice", "Accounts", `On, with the Firebase project "${webProject}". Email and Google sign-in are both set up in this build.`);
else if (webProject && services) say("warning", "Accounts", `On, with the Firebase project "${webProject}". Email sign-in works; Google sign-in needs the fix above.`);
else if (webProject) say("warning", "Accounts", `On, with the Firebase project "${webProject}", but there's no GOOGLE_SERVICES_JSON secret, so only email sign-in works.`);
else if (gs) say("warning", "Accounts", "Off: there's no FIREBASE_WEB_CONFIG secret, so entries stay on the phone. Add it to turn accounts on.");
else say("notice", "Accounts", "Off: no Firebase secrets yet, so entries stay on the phone.");
