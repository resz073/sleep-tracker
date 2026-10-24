// Runs after `npx cap add android`. Adds what the generated Android project needs:
// Firebase settings, Google sign-in, the notification permission, version numbers, icons and splash screens.
// `node scripts/patch-android.mjs --preview` only draws the artwork into dist/icons-preview/.
import fs from "node:fs";
import path from "node:path";
import { moonPng, pngSize, COLORS, DENSITIES } from "./png.mjs";

const preview = process.argv.includes("--preview");
const RES = preview ? "dist/icons-preview/res" : "android/app/src/main/res";
const mk = p => fs.mkdirSync(path.dirname(p), { recursive: true });
const write = (p, data) => { mk(p); fs.writeFileSync(p, data); };

// ---- artwork ----
for (const [d, k] of Object.entries(DENSITIES)) {
  const s = Math.round(48 * k), f = Math.round(108 * k), n = Math.round(24 * k);
  write(`${RES}/mipmap-${d}/ic_launcher.png`, moonPng(s, s, { bg: COLORS.night, shape: "rounded", moon: COLORS.moon, r: s * 0.29 }));
  write(`${RES}/mipmap-${d}/ic_launcher_round.png`, moonPng(s, s, { bg: COLORS.night, shape: "circle", moon: COLORS.moon, r: s * 0.27 }));
  write(`${RES}/mipmap-${d}/ic_launcher_foreground.png`, moonPng(f, f, { bg: null, moon: COLORS.moon, r: f * 0.22 }));
  write(`${RES}/drawable-${d}/ic_stat_moon.png`, moonPng(n, n, { bg: null, moon: COLORS.white, r: n * 0.40 }));
}
write(`${RES}/values/ic_launcher_background.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">${COLORS.night}</color>\n</resources>\n`);
write(`${RES}/values/splash_colors.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="splash_background">${COLORS.night}</color>\n</resources>\n`);

if (preview) {
  write(`${RES}/splash-sample.png`, moonPng(480, 800, { bg: COLORS.night, shape: "full", moon: COLORS.moon, r: 480 * 0.13 }));
  write("dist/icons-preview/play-icon-512.png", moonPng(512, 512, { bg: COLORS.night, shape: "full", moon: COLORS.moon, r: 512 * 0.27 }));
  console.log("Preview written to dist/icons-preview/");
  process.exit(0);
}

// Replace every Capacitor splash image with a night-blue one of the same size
const walk = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
let splashCount = 0;
for (const file of walk(RES)) {
  if (path.basename(file) !== "splash.png") continue;
  const { w, h } = pngSize(fs.readFileSync(file));
  fs.writeFileSync(file, moonPng(w, h, { bg: COLORS.night, shape: "full", moon: COLORS.moon, r: Math.min(w, h) * 0.13 }));
  splashCount++;
}

// ---- project settings ----
const A = "android", APP = path.join(A, "app");
fs.copyFileSync("google-services.json", path.join(APP, "google-services.json"));

const varsFile = path.join(A, "variables.gradle");
let vars = fs.readFileSync(varsFile, "utf8");
if (!/rgcfaIncludeGoogle/.test(vars)) vars = vars.replace(/ext\s*\{/, "ext {\n    rgcfaIncludeGoogle = true");
fs.writeFileSync(varsFile, vars);

const manFile = path.join(APP, "src/main/AndroidManifest.xml");
let man = fs.readFileSync(manFile, "utf8");
if (!man.includes("android.permission.POST_NOTIFICATIONS")) man = man.replace(/<\/manifest>\s*$/, '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n</manifest>\n');
man = man.replace(/android:configChanges="([^"]*)"/, (s, v) => v.includes("density") ? s : `android:configChanges="${v}|density"`);
fs.writeFileSync(manFile, man);

const stylesFile = path.join(APP, "src/main/res/values/styles.xml");
if (fs.existsSync(stylesFile)) {
  let st = fs.readFileSync(stylesFile, "utf8");
  if (!st.includes("windowSplashScreenBackground")) st = st.replace(/(<style name="AppTheme\.NoActionBarLaunch"[^>]*>)/, '$1\n        <item name="windowSplashScreenBackground">@color/splash_background</item>');
  fs.writeFileSync(stylesFile, st);
}

const pkg = JSON.parse(fs.readFileSync("package.json", "utf8"));
const code = Number(process.env.VERSION_CODE || process.env.GITHUB_RUN_NUMBER || 1);
const gradleFile = path.join(APP, "build.gradle");
let gradle = fs.readFileSync(gradleFile, "utf8");
gradle = gradle.replace(/versionCode\s+\d+/, `versionCode ${code}`).replace(/versionName\s+"[^"]*"/, `versionName "${pkg.version}"`);
fs.writeFileSync(gradleFile, gradle);

console.log(`Android project patched: version ${pkg.version} (${code}), ${splashCount} splash images, icons, notification permission, Google sign-in.`);
