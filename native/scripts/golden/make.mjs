#!/usr/bin/env node
// Makes the reference results the Kotlin sleep engine is tested against, by running the current app's own
// code (src/app.js) on the accounts in scenarios.mjs, each in its own time zone.
//   node native/scripts/golden/make.mjs           writes native/app/src/test/resources/golden/
//   node native/scripts/golden/make.mjs --check   fails if those files don't match src/app.js any more
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");
const outDir = path.join(root, "native/app/src/test/resources/golden");
const appFile = path.join(root, "src/app.js");
const zoneArg = process.argv.indexOf("--zone");

if (zoneArg > 0) await child(process.argv[zoneArg + 1]);
else await main(process.argv.includes("--check"));

// One time zone: reads scenarios on stdin, writes them back with the app's results on stdout
async function child(zone) {
  const { sandbox } = await import("./sandbox.mjs");
  const probe = [Date.UTC(2026, 0, 15), Date.UTC(2026, 6, 15)].map(t => new Date(t));
  if (probe.some(d => d.toLocaleString("en-US") !== d.toLocaleString("en-US", { timeZone: zone }))) throw new Error("time zone not applied: " + zone);
  const input = JSON.parse(fs.readFileSync(0, "utf8"));
  const box = sandbox(appFile, path.join(here, "extract.js"));
  const ms = s => new Date(+s.slice(0, 4), +s.slice(5, 7) - 1, +s.slice(8, 10), +s.slice(11, 13), +s.slice(14, 16)).getTime();
  const out = input.map(scn => {
    scn.nowMs = ms(scn.now);
    (scn.goalOps || []).forEach(o => { o.nowMs = ms(o.at); });
    try { return { ...scn, expect: box.run(scn) }; } catch (e) { e.message = scn.name + ": " + e.message; throw e; }
  });
  process.stdout.write(JSON.stringify(out));
}

async function main(check) {
  const { scenarios } = await import("./scenarios.mjs");
  const all = scenarios(), zones = [...new Set(all.map(s => s.zone))];
  const names = new Set();
  all.forEach(s => { if (names.has(s.name)) throw new Error("duplicate scenario " + s.name); names.add(s.name); });
  const files = {};
  for (const zone of zones) {
    const r = spawnSync(process.execPath, [fileURLToPath(import.meta.url), "--zone", zone], {
      input: JSON.stringify(all.filter(s => s.zone === zone)), env: { ...process.env, TZ: zone }, maxBuffer: 1 << 29, encoding: "utf8"
    });
    if (r.status !== 0) { process.stderr.write(r.stderr); process.exit(1); }
    const cases = JSON.parse(r.stdout);
    const file = zone.toLowerCase().replace(/[^a-z0-9]+/g, "-") + ".json";
    files[file] = '{"zone":' + JSON.stringify(zone) + ',"cases":[\n' + cases.map(c => JSON.stringify(c)).join(",\n") + "\n]}\n";
  }
  files["index.txt"] = Object.keys(files).sort().join("\n") + "\n";
  if (check) {
    const stale = Object.keys(files).filter(f => { try { return fs.readFileSync(path.join(outDir, f), "utf8") !== files[f]; } catch (e) { return true; } });
    const extra = fs.existsSync(outDir) ? fs.readdirSync(outDir).filter(f => !(f in files)) : [];
    if (stale.length || extra.length) {
      console.log("::error title=Reference results out of date::src/app.js gives different results from native/app/src/test/resources/golden (" + stale.concat(extra).join(", ") + "). Run node native/scripts/golden/make.mjs, then update the Kotlin engine until its tests pass.");
      process.exit(1);
    }
    console.log("Reference results match src/app.js (" + all.length + " scenarios, " + zones.length + " time zones).");
    return;
  }
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });
  for (const [f, text] of Object.entries(files)) fs.writeFileSync(path.join(outDir, f), text);
  const size = Object.values(files).reduce((a, t) => a + t.length, 0);
  console.log("Wrote " + all.length + " scenarios in " + zones.length + " time zones to " + path.relative(root, outDir) + " (" + Math.round(size / 1024) + " KB).");
}
