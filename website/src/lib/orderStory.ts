/**
 * The order film's timeline, shared by the page script (captions, HUD, flashes) and the 3D world. Kept apart from
 * the world so the page does not load three.js before the film is near. Everything is a function of scroll
 * progress p in 0..1.
 */
export const CHAPTERS = [
  { key: 'order', from: 0.0, to: 0.11 },
  { key: 'stock', from: 0.11, to: 0.22 },
  { key: 'approve', from: 0.22, to: 0.31 },
  { key: 'shipdate', from: 0.31, to: 0.43 },
  { key: 'wave', from: 0.43, to: 0.51 },
  { key: 'pick', from: 0.51, to: 0.63 },
  { key: 'pack', from: 0.63, to: 0.75 },
  { key: 'dispatch', from: 0.75, to: 0.89 },
  { key: 'everywhere', from: 0.89, to: 1.0 },
] as const;

export const CHANNELS = ['Shopify', 'BigCommerce', 'Amazon', 'Walmart', 'WooCommerce', 'Adobe Commerce', 'eBay', 'Etsy',
  'TikTok Shop', 'Wix', 'Squarespace', 'Salesforce Commerce', 'Shopify POS', 'B2B portal', 'EDI partners', 'Any API'];

const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const seg = (p: number, a: number, b: number) => clamp((p - a) / (b - a));
const bump = (p: number, a: number, peak: number, b: number) => (p < peak ? seg(p, a, peak) : 1 - seg(p, peak, b));

export const chapterAt = (p: number) => {
  const i = CHAPTERS.findIndex(c => p < c.to);
  return i < 0 ? CHAPTERS.length - 1 : i;
};
export const local = (p: number, i: number) => seg(p, CHAPTERS[i].from, CHAPTERS[i].to);

/** The cuts: diving through the phone's screen into Nexus (light), and the fade out to the globe (dark). */
export function flash(p: number): { white: number; black: number } {
  return { white: bump(p, 0.096, 0.108, 0.122), black: bump(p, 0.878, 0.892, 0.906) };
}

/** The order's clock (simulated wall time) and the one number each chapter is about, like a speedometer. */
const CLOCK: [number, number][] = [   // [progress, seconds since midnight]
  [0.0, 10 * 3600 + 42 * 60 + 1], [0.06, 10 * 3600 + 42 * 60 + 3], [0.22, 10 * 3600 + 42 * 60 + 4], [0.31, 10 * 3600 + 42 * 60 + 5],
  [0.43, 11 * 3600 + 5 * 60], [0.51, 11 * 3600 + 8 * 60], [0.63, 11 * 3600 + 21 * 60], [0.75, 11 * 3600 + 27 * 60],
  [0.89, 17 * 3600 + 30 * 60], [1.0, 17 * 3600 + 42 * 60],
];
const hms = (s: number) => [Math.floor(s / 3600), Math.floor(s / 60) % 60, Math.floor(s) % 60].map(n => String(n).padStart(2, '0')).join(':');

export function hud(p: number): { clock: string; label: string; value: string } {
  let s = CLOCK[CLOCK.length - 1][1];
  for (let i = 0; i < CLOCK.length - 1; i++) {
    const [a, sa] = CLOCK[i], [b, sb] = CLOCK[i + 1];
    if (p <= b) { s = sa + (sb - sa) * seg(p, a, b); break; }
  }
  const c = chapterAt(p), t = local(p, c);
  const n = (to: number, from = 0, k = t) => Math.round(from + (to - from) * clamp(k * 1.25));
  const m: [string, string][] = [
    ['Channels', String(n(CHANNELS.length))],
    ['Sellable at East', `${n(36)} of 41`],
    ['Checks passed', `${Math.min(4, Math.floor(clamp(t * 1.4 - 0.25) * 5))} / 4`],
    ['B2B: days to ship', String(n(3, 64, seg(t, 0.15, 0.7)))],
    ['Locations considered', String(n(12))],
    ['Picker walked', `${n(42, 0, seg(t, 0.1, 0.95))} m`],
    ['Parcels labelled', `${n(1, 0, seg(t, 0.25, 0.75))} / 1`],
    ['Truck', `${Math.round(48 * Math.pow(seg(t, 0.72, 1), 1.4))} km/h`],
    ['Order', 'Completed'],
  ];
  return { clock: hms(s), label: m[c][0], value: m[c][1] };
}
