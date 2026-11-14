#!/usr/bin/env node
// Shows the unit test results on the GitHub Actions run's summary page: a note when they pass, and each
// failed test's message as an error (the engine test lists every value that differs from the current app).
// Usage: node native/scripts/test-report.mjs <folder with JUnit XML results>
import fs from "node:fs";
import path from "node:path";

const dir = process.argv[2];
let files = [];
try { files = fs.readdirSync(dir).filter(f => f.endsWith(".xml")); } catch (e) { /* no results: the tests didn't compile */ }
if (!files.length) process.exit(0);

const unxml = s => s.replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16))).replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n))
  .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, "&");
const data = s => s.replace(/%/g, "%25").replace(/\r/g, "").replace(/\n/g, "%0A");
const prop = s => data(s).replace(/:/g, "%3A").replace(/,/g, "%2C");

let tests = 0;
const failed = [];
for (const f of files) {
  const xml = fs.readFileSync(path.join(dir, f), "utf8");
  for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
    tests++;
    const attr = k => (m[1].match(new RegExp("\\b" + k + '="([^"]*)"')) || [])[1] || "";
    const fail = (m[3] || "").match(/<(failure|error) message="([^"]*)"/);
    if (fail) failed.push({ name: attr("classname").split(".").pop() + "." + attr("name"), message: unxml(fail[2]) });
  }
}
if (!failed.length) { console.log("::notice title=Engine tests::" + tests + (tests === 1 ? " test" : " tests") + " passed: the sleep engine gives the same results as the current app."); process.exit(0); }
for (const t of failed.slice(0, 8)) {
  const msg = t.message.length > 6000 ? t.message.slice(0, 6000) + "\n…" : t.message;
  console.log("::error title=" + prop("Test failed: " + t.name) + "::" + data(msg));
}
