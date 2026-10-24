// Tiny PNG writer plus the app's crescent-moon artwork, so icons and splash screens are drawn
// at build time with no image tools. Shapes are anti-aliased with 4x4 supersampling.
import zlib from "node:zlib";

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc32(buf) { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC[(c ^ buf[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, c]);
}
export function encodePng(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}
export function pngSize(buf) { return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) }; }

const rgb = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];

// opt: { bg: "#rrggbb" | null, shape: "full" | "rounded" | "circle", moon: "#rrggbb", r: moon radius in px }
export function moonPng(w, h, opt) {
  const out = Buffer.alloc(w * h * 4), SS = 4, bg = opt.bg ? rgb(opt.bg) : null, mc = rgb(opt.moon);
  const r = opt.r, cx = w / 2 - 0.1 * r, cy = h / 2 + 0.08 * r;            // nudge so the crescent looks centred
  const bx = cx + 0.45 * r, by = cy - 0.32 * r, br = 0.86 * r;             // the "bite" circle that makes the crescent
  const cr = Math.min(w, h) * 0.22, half = Math.min(w, h) / 2;
  const inBg = (x, y) => {
    if (!bg) return false;
    if (opt.shape === "circle") { const dx = x - w / 2, dy = y - h / 2; return dx * dx + dy * dy <= half * half; }
    if (opt.shape === "rounded") { const ix = Math.max(cr - x, 0, x - (w - cr)), iy = Math.max(cr - y, 0, y - (h - cr)); return ix * ix + iy * iy <= cr * cr; }
    return true;
  };
  const inMoon = (x, y) => { const dx = x - cx, dy = y - cy; if (dx * dx + dy * dy > r * r) return false; const ex = x - bx, ey = y - by; return ex * ex + ey * ey > br * br; };
  const nearMoon = (px, py) => Math.abs(Math.hypot(px + .5 - cx, py + .5 - cy) - r) < 2 || Math.abs(Math.hypot(px + .5 - bx, py + .5 - by) - br) < 2;
  for (let py = 0; py < h; py++) for (let px = 0; px < w; px++) {
    let ab, am;
    const edgeBg = bg && opt.shape !== "full" && (px < cr + 2 || px > w - cr - 3 || py < cr + 2 || py > h - cr - 3);
    if (!edgeBg && !nearMoon(px, py)) { ab = inBg(px + .5, py + .5) ? 1 : 0; am = inMoon(px + .5, py + .5) ? 1 : 0; }
    else {
      let cb = 0, cm = 0;
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) { const x = px + (sx + .5) / SS, y = py + (sy + .5) / SS; if (inBg(x, y)) cb++; if (inMoon(x, y)) cm++; }
      ab = cb / (SS * SS); am = cm / (SS * SS);
    }
    let R = 0, G = 0, B = 0, A = 0;
    if (bg) { R = bg[0] * ab; G = bg[1] * ab; B = bg[2] * ab; A = ab; }
    R = mc[0] * am + R * (1 - am); G = mc[1] * am + G * (1 - am); B = mc[2] * am + B * (1 - am); A = am + A * (1 - am);
    const i = (py * w + px) * 4;
    if (A > 0) { out[i] = Math.round(R / A); out[i + 1] = Math.round(G / A); out[i + 2] = Math.round(B / A); }
    out[i + 3] = Math.round(A * 255);
  }
  return encodePng(w, h, out);
}

export const COLORS = { night: "#141C48", moon: "#A3ABFF", white: "#FFFFFF" };
export const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
