// Writes native/app/firebase.properties for the native app's build, from google-services.json:
// the GOOGLE_SERVICES_JSON secret on GitHub, or native/app/google-services.json on your own computer.
// Run from the repository root: node native/scripts/firebase-config.mjs
//
// The preview installs as com.resz073.sleeptracker.next. If google-services.json has no Android app for
// that package yet, the settings of the main app are used: the build still works and email sign-in may,
// but Google sign-in needs the preview package registered in Firebase.
import { readFileSync, writeFileSync, existsSync } from "node:fs";

const APP_ID = "com.resz073.sleeptracker.next", MAIN_ID = "com.resz073.sleeptracker";
const OUT = "native/app/firebase.properties", LOCAL = "native/app/google-services.json";
const CI = !!process.env.GITHUB_ACTIONS;
const say = (level, title, msg) => console.log(CI ? `::${level} title=${title}::${msg}` : `${title}: ${msg}`);

function parse(s) {
  const tries = [s];
  const a = s.indexOf("{"), b = s.lastIndexOf("}");
  if (a >= 0 && b > a) tries.push(s.slice(a, b + 1));
  if (/^[A-Za-z0-9+/=\s]+$/.test(s)) tries.push(Buffer.from(s.replace(/\s+/g, ""), "base64").toString("utf8"));
  for (const t of tries) { try { const j = JSON.parse(t); if (j && j.project_info) return j; } catch (e) { /* next */ } }
  return null;
}

let text = (process.env.GOOGLE_SERVICES_JSON || "").trim();
if (!text && existsSync(LOCAL)) text = readFileSync(LOCAL, "utf8").trim();
if (!text) {
  writeFileSync(OUT, "");
  say("notice", "Accounts", "Off: no GOOGLE_SERVICES_JSON secret, so this build can't sign in.");
  process.exit(0);
}
const json = parse(text);
if (!json) { say("error", "GOOGLE_SERVICES_JSON", "This isn't the contents of google-services.json."); process.exit(1); }

const pkg = c => c?.client_info?.android_client_info?.package_name;
const clients = json.client || [];
const own = clients.find(c => pkg(c) === APP_ID);
const client = own || clients.find(c => pkg(c) === MAIN_ID) || clients[0];
if (!client) { say("error", "GOOGLE_SERVICES_JSON", "google-services.json has no Android app in it."); process.exit(1); }

// The web client ID is what Google sign-in asks for; any Android app in the file lists it
const oauth = clients.flatMap(c => [...(c.oauth_client || []), ...(c.services?.appinvite_service?.other_platform_oauth_client || [])]);
const web = oauth.find(o => o.client_type === 3)?.client_id || "";

const props = {
  api_key: client.api_key?.[0]?.current_key || "",
  app_id: client.client_info?.mobilesdk_app_id || "",
  project_id: json.project_info.project_id || "",
  storage_bucket: json.project_info.storage_bucket || "",
  sender_id: json.project_info.project_number || "",
  web_client_id: web,
  registered: own ? "true" : "false",
};
writeFileSync(OUT, Object.entries(props).map(([k, v]) => `${k}=${String(v).replace(/[\r\n]/g, "")}`).join("\n") + "\n");

if (own) say("notice", "Accounts", `On, with the Firebase project "${props.project_id}" and its Android app for ${APP_ID}.`);
else say("warning", "Google sign-in", `google-services.json has no Android app for ${APP_ID}, so Google sign-in won't work in this preview yet. In Firebase, add an Android app with that package name and your SHA-1 fingerprints, then download google-services.json again and update the GOOGLE_SERVICES_JSON secret.`);
if (!web) say("warning", "Google sign-in", "google-services.json has no web client ID. Turn on Google sign-in in Firebase, then download the file again.");
