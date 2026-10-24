// Bundles the app (src/) into www/, which Capacitor packages into the Android app.
import { build } from "esbuild";
import { mkdirSync, copyFileSync, existsSync, rmSync } from "node:fs";
import { join } from "node:path";

const out = "www";
rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, "fonts"), { recursive: true });

await build({
  entryPoints: ["src/app.js"],
  bundle: true,
  format: "esm",
  target: ["chrome100"],
  minify: true,
  legalComments: "none",
  outfile: join(out, "app.js")
});
for (const f of ["index.html", "styles.css"]) copyFileSync(join("src", f), join(out, f));

// Nunito, bundled so the app looks right offline
const fonts = "node_modules/@fontsource/nunito/files";
for (const w of [400, 600, 700, 800]) {
  const n = `nunito-latin-${w}-normal.woff2`;
  if (existsSync(join(fonts, n))) copyFileSync(join(fonts, n), join(out, "fonts", n));
  else console.warn("Font file missing, the app will use the system font:", n);
}
console.log("Built www/");
