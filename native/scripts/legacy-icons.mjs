// Draws the PNG launcher icons that Android 7 (API 24-25) uses; newer Android uses the vector adaptive icon.
// Run from the repository root: node native/scripts/legacy-icons.mjs
import fs from "node:fs";
import { moonPng, COLORS, DENSITIES } from "../../scripts/png.mjs";
const RES = "native/app/src/main/res";
for (const [d, k] of Object.entries(DENSITIES)) {
  const s = Math.round(48 * k);
  fs.mkdirSync(`${RES}/mipmap-${d}`, { recursive: true });
  fs.writeFileSync(`${RES}/mipmap-${d}/ic_launcher.png`, moonPng(s, s, { bg: COLORS.night, shape: "rounded", moon: COLORS.moon, r: s * 0.29 }));
  fs.writeFileSync(`${RES}/mipmap-${d}/ic_launcher_round.png`, moonPng(s, s, { bg: COLORS.night, shape: "circle", moon: COLORS.moon, r: s * 0.27 }));
}
console.log("Legacy launcher icons written.");
