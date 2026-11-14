// Loads the current app (src/app.js) with a stand-in page and a fixed clock, so its own functions can be
// called and their results recorded. The time zone is the process's (TZ), so run one process per zone.
import fs from "node:fs";
import vm from "node:vm";

// Stands in for any page element or browser object: every property and call gives another stand-in,
// properties that are set keep their value, and it reads as "" (or 0) when used as text.
function stub() {
  const store = Object.create(null);
  return new Proxy(function () {}, {
    get(_, k) {
      if (k in store) return store[k];
      if (k === Symbol.toPrimitive) return hint => (hint === "number" ? 0 : "");
      if (k === Symbol.iterator) return function* () {};
      if (k === "then") return undefined; // not a promise
      if (k === "length") return 0;
      return stub();
    },
    set(_, k, v) { store[k] = v; return true; },
    apply() { return stub(); },
    construct() { return stub(); }
  });
}

// A fake clock: new Date() and Date.now() give the time set with __setNow, everything else is the real Date.
const PRELUDE = `
const __RealDate = Date;
let __now = 0;
class __FakeDate extends __RealDate {
  constructor(...a) { if (a.length === 0) super(__now); else super(...a); }
  static now() { return __now; }
}
globalThis.Date = __FakeDate;
globalThis.__setNow = v => { __now = v; };
`;

export function sandbox(appFile, extractFile) {
  const src = fs.readFileSync(appFile, "utf8");
  const imports = src.match(/^import .*$/gm) || [];
  if (imports.length !== 3) throw new Error("src/app.js imports changed, update the sandbox: " + imports.join(" | "));
  const body = '"use strict";' + src.replace(/^import .*$/gm, ""); // same line numbers as the file
  const els = new Map(), ls = new Map();
  const el = id => { if (!els.has(id)) els.set(id, stub()); return els.get(id); };
  const ctx = {
    document: {
      getElementById: el, querySelectorAll: () => stub(), querySelector: () => stub(), createElement: () => stub(),
      addEventListener() {}, body: stub(), activeElement: null, hidden: false
    },
    localStorage: { getItem: k => (ls.has(k) ? ls.get(k) : null), setItem: (k, v) => { ls.set(k, String(v)); }, removeItem: k => { ls.delete(k); } },
    window: stub(), history: stub(), location: { hash: "" }, matchMedia: () => ({ matches: false }),
    setInterval: () => 0, clearInterval() {}, setTimeout: () => 0, clearTimeout() {},
    URL: stub(), Blob: stub(), FileReader: stub(), console,
    backend: { configured: false, message: () => "" },
    platform: {
      native: false, onResume() {}, onBack() {}, setupNotifications() {}, exitApp() {},
      scheduleReminders: async () => {}, askNotifications: async () => "granted", notificationState: async () => "granted",
      exactAlarmsAllowed: async () => true, askExactAlarms: async () => true, shareFile: async () => {}
    },
    links: { privacy: "https://example.invalid/privacy", deletion: "https://example.invalid/delete" },
    appVersion: "0",
    __els: els, __ls: ls
  };
  vm.createContext(ctx);
  vm.runInContext(PRELUDE, ctx, { filename: "prelude.js" });
  vm.runInContext(body, ctx, { filename: "app.js" });
  vm.runInContext(fs.readFileSync(extractFile, "utf8"), ctx, { filename: "extract.js" });
  return { run: scn => JSON.parse(JSON.stringify(ctx.__run(JSON.parse(JSON.stringify(scn))))) };
}
