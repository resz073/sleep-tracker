// Prints the SHA-1 fingerprints registered for one Android package in google-services.json, one per
// line, in lowercase hex. Google sign-in only works in a build signed with one of them.
// Usage: node scripts/gs-fingerprints.mjs google-services.json com.resz073.sleeptracker
import { readFileSync } from "node:fs";

const [file, pkg] = process.argv.slice(2);
const s = readFileSync(file, "utf8").trim();
// The secret may be the file, the file with text around it, or the file in base64
const tries = [s];
const a = s.indexOf("{"), b = s.lastIndexOf("}");
if (a >= 0 && b > a) tries.push(s.slice(a, b + 1));
if (/^[A-Za-z0-9+/=\s]+$/.test(s)) tries.push(Buffer.from(s.replace(/\s+/g, ""), "base64").toString("utf8"));
let json = null;
for (const t of tries) {
  try { const j = JSON.parse(t); if (j && j.project_info) { json = j; break; } } catch (e) { /* next */ }
}
if (!json) process.exit(1);

const found = new Set();
for (const c of json.client || [])
  for (const o of c.oauth_client || [])
    if (o.client_type === 1 && o.android_info?.package_name === pkg && o.android_info.certificate_hash)
      found.add(o.android_info.certificate_hash.toLowerCase());
console.log([...found].join("\n"));
