import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { SMAAPass } from 'three/addons/postprocessing/SMAAPass.js';
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js';
import { CHANNELS, CHAPTERS } from './orderStory';
import LAND from '../data/land.json';

/**
 * The life of one order as a real-time 3D film, driven entirely by scroll progress (0..1). Every position, colour,
 * light and the camera are pure functions of progress, so scrolling back plays the film backwards exactly; idle
 * motion (glows, rings, particles) runs on the clock on top.
 *
 * Each chapter has its own light, the way a film changes location: a bright studio for the customer, deep space for
 * Nexus, dusk for the ship-date fork, a bright warehouse, an evening loading dock, and the planet at the end.
 * Rendering: ambient occlusion and bloom on capable screens, SMAA, a soft vignette and fine grain.
 *
 * Illustration with an invented order; the steps are Nexus's steps.
 */

export { CHAPTERS, CHANNELS } from './orderStory';
export interface OrderWorld { setProgress(p: number): void; destroy(): void; }

// ------------------------------------------------------------------ maths

const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const seg = (p: number, a: number, b: number) => clamp((p - a) / (b - a));
const ease = (t: number) => t * t * t * (t * (t * 6 - 15) + 10);
const easeOut = (t: number) => 1 - Math.pow(1 - t, 3);
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const v = (x: number, y: number, z: number) => new THREE.Vector3(x, y, z);
const L = (p: number, i: number) => seg(p, CHAPTERS[i].from, CHAPTERS[i].to);   // progress within chapter i
const at = (i: number, t: number) => lerp(CHAPTERS[i].from, CHAPTERS[i].to, t);   // a moment within chapter i
const chapterIdx = (p: number) => { const i = CHAPTERS.findIndex(c => p < c.to); return i < 0 ? CHAPTERS.length - 1 : i; };

const COL = {
  ink: 0x1b2334, steel: 0x8f9ab0, signal: 0x5b8cff, ember: 0xff8a4c, go: 0x3ddc97, warn: 0xffc857, stop: 0xff5d6c,
  card: 0xd2a86f, cardDark: 0xb48650, wood: 0xa7784a, skin: 0xe2b896, fork: 0xffb627,
};

// ------------------------------------------------------------------ the look of each chapter

interface Env { bg: THREE.Color; fogNear: number; fogFar: number; sky: THREE.Color; ground: THREE.Color; hemi: number; sun: THREE.Color; sunI: number;
  floor: THREE.Color; grid: THREE.Color; exposure: number; bloom: number; bloomT: number; words: THREE.Color; wordsA: number }
type EnvIn = { bg: number; near: number; far: number; sky: number; ground: number; hemi: number; sun: number; sunI: number; floor: number; grid: number; exp: number; bloom: number; bloomT: number; words: number; wordsA: number };
const env = (o: EnvIn): Env => ({
  bg: new THREE.Color(o.bg), fogNear: o.near, fogFar: o.far, sky: new THREE.Color(o.sky), ground: new THREE.Color(o.ground), hemi: o.hemi,
  sun: new THREE.Color(o.sun), sunI: o.sunI, floor: new THREE.Color(o.floor), grid: new THREE.Color(o.grid), exposure: o.exp, bloom: o.bloom, bloomT: o.bloomT,
  words: new THREE.Color(o.words), wordsA: o.wordsA,
});
const STUDIO = env({ bg: 0xe8ecf3, near: 26, far: 80, sky: 0xffffff, ground: 0xc6cfdf, hemi: 0.95, sun: 0xfff3e6, sunI: 2.1, floor: 0xe3e8f0, grid: 0xcfd6e2, exp: 0.95, bloom: 0.35, bloomT: 3.5, words: 0x0b1222, wordsA: 0.05 });
const SPACE = env({ bg: 0x04060d, near: 26, far: 95, sky: 0x5f78c4, ground: 0x04060d, hemi: 0.55, sun: 0xb9c8ff, sunI: 0.9, floor: 0x0a0f1c, grid: 0x18213a, exp: 1.05, bloom: 0.75, bloomT: 0.9, words: 0xffffff, wordsA: 0.035 });
const DUSK = env({ bg: 0x1d2647, near: 24, far: 85, sky: 0xffb28a, ground: 0x1b2238, hemi: 0.9, sun: 0xffa36c, sunI: 2.0, floor: 0x252d48, grid: 0x343e60, exp: 1.05, bloom: 0.5, bloomT: 1.6, words: 0xffffff, wordsA: 0.035 });
const WAREHOUSE = env({ bg: 0xeef1f6, near: 26, far: 82, sky: 0xffffff, ground: 0xbac4d4, hemi: 0.9, sun: 0xffffff, sunI: 2.0, floor: 0xd6dce6, grid: 0xc4ccd9, exp: 0.95, bloom: 0.35, bloomT: 3.5, words: 0x0b1222, wordsA: 0.045 });
const EVENING = env({ bg: 0x0b1330, near: 24, far: 90, sky: 0x6b80c0, ground: 0x0b1330, hemi: 0.6, sun: 0x9fb4ff, sunI: 0.75, floor: 0x121a33, grid: 0x1f2a4a, exp: 1.0, bloom: 0.6, bloomT: 1.4, words: 0xffffff, wordsA: 0.035 });
const ORBIT = env({ bg: 0x02030a, near: 60, far: 220, sky: 0x5f78c4, ground: 0x02030a, hemi: 0.4, sun: 0xffffff, sunI: 0.6, floor: 0x02030a, grid: 0x02030a, exp: 1.05, bloom: 0.9, bloomT: 0.5, words: 0xffffff, wordsA: 0.0 });
// [progress, look]; between two keys the look blends
const ENV_KEYS: [number, Env][] = [[0, STUDIO], [0.104, STUDIO], [0.112, SPACE], [0.305, SPACE], [0.33, DUSK], [0.425, DUSK], [0.445, WAREHOUSE],
  [0.75, WAREHOUSE], [0.775, EVENING], [0.889, EVENING], [0.893, ORBIT], [1, ORBIT]];
const envNow = env({ bg: 0, near: 0, far: 0, sky: 0, ground: 0, hemi: 0, sun: 0, sunI: 0, floor: 0, grid: 0, exp: 1, bloom: 0, bloomT: 1, words: 0, wordsA: 0 });
function envAt(p: number): Env {
  let i = 0;
  while (i < ENV_KEYS.length - 2 && p > ENV_KEYS[i + 1][0]) i++;
  const [pa, a] = ENV_KEYS[i], [pb, b] = ENV_KEYS[i + 1];
  const t = ease(seg(p, pa, pb));
  for (const k of ['bg', 'sky', 'ground', 'sun', 'floor', 'grid', 'words'] as const) envNow[k].copy(a[k]).lerp(b[k], t);
  envNow.fogNear = lerp(a.fogNear, b.fogNear, t); envNow.fogFar = lerp(a.fogFar, b.fogFar, t);
  envNow.hemi = lerp(a.hemi, b.hemi, t); envNow.sunI = lerp(a.sunI, b.sunI, t); envNow.exposure = lerp(a.exposure, b.exposure, t);
  envNow.bloom = lerp(a.bloom, b.bloom, t); envNow.bloomT = lerp(a.bloomT, b.bloomT, t); envNow.wordsA = lerp(a.wordsA, b.wordsA, t);
  return envNow;
}

// ------------------------------------------------------------------ canvas text

type Painter = (g: CanvasRenderingContext2D, w: number, h: number) => void;
const FONT = '"Inter Variable", Inter, system-ui, sans-serif';
const MONO = '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, monospace';
const DISPLAY = '"Unbounded Variable", Unbounded, system-ui, sans-serif';

function panel(wu: number, hu: number, px: number, paint: Painter, opts: { fog?: boolean } = {}) {
  const c = document.createElement('canvas');
  c.width = px; c.height = Math.round(px * (hu / wu));
  const g = c.getContext('2d')!;
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 8;
  const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, toneMapped: false, side: THREE.DoubleSide, fog: opts.fog ?? true, depthWrite: false });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(wu, hu), mat);
  mesh.renderOrder = 2;
  const repaint = (p: Painter = paint) => { g.clearRect(0, 0, c.width, c.height); p(g, c.width, c.height); tex.needsUpdate = true; };
  repaint();
  return { mesh, repaint, mat };
}
function rr(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}
function card(g: CanvasRenderingContext2D, w: number, h: number, border = 'rgba(255,255,255,.2)', fill = 'rgba(9,14,27,.92)') {
  rr(g, 4, 4, w - 8, h - 8, Math.min(w, h) * 0.12); g.fillStyle = fill; g.fill(); g.lineWidth = 4; g.strokeStyle = border; g.stroke();
}
function text(g: CanvasRenderingContext2D, s: string, x: number, y: number, size: number, color = '#e8edf7', font = FONT, weight = 600, align: CanvasTextAlign = 'left') {
  g.font = `${weight} ${size}px ${font}`; g.fillStyle = color; g.textAlign = align; g.textBaseline = 'middle'; g.fillText(s, x, y);
}

// ------------------------------------------------------------------ materials and primitives

const std = (color: number, extra: Partial<THREE.MeshStandardMaterialParameters> = {}) => new THREE.MeshStandardMaterial({ color, roughness: 0.55, metalness: 0.04, ...extra });
const glow = (color: number, intensity = 2) => new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: intensity, roughness: 0.35 });
function rbox(w: number, h: number, d: number, r: number, mat: THREE.Material, shadow = true) {
  const m = new THREE.Mesh(new RoundedBoxGeometry(w, h, d, 3, r), mat); m.castShadow = shadow; m.receiveShadow = true; return m;
}
function box(w: number, h: number, d: number, mat: THREE.Material, shadow = true) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat); m.castShadow = shadow; m.receiveShadow = true; return m;
}
function cyl(r: number, h: number, mat: THREE.Material, segs = 24) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, segs), mat); m.castShadow = true; m.receiveShadow = true; return m;
}
function mulberry(seed: number) {
  return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
function glowTexture() {
  const c = document.createElement('canvas'); c.width = c.height = 64;
  const g = c.getContext('2d')!, gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.3, 'rgba(255,255,255,.5)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
  return new THREE.CanvasTexture(c);
}
const points = (n: number, color: number, size: number, tex: THREE.Texture, additive = true) => {
  const pos = new Float32Array(n * 3), geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const mat = new THREE.PointsMaterial({ color, size, map: tex, transparent: true, depthWrite: false, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending });
  return { pos, geo, mat, obj: new THREE.Points(geo, mat), dirty: () => { geo.attributes.position.needsUpdate = true; } };
};

// ------------------------------------------------------------------ models

/** A person: legs with feet, body (vest colour) with a reflective band, arms, head and cap. */
function person(vest: number, cap: number) {
  const g = new THREE.Group();
  const legMat = std(0x222b3d), skin = std(COL.skin, { roughness: 0.7 }), vestM = std(vest, { roughness: 0.6 }), shoe = std(0x0f141f);
  const limb = (r: number, len: number, mat: THREE.Material, end: THREE.Material, pivot: THREE.Vector3) => {
    const pv = new THREE.Group(); pv.position.copy(pivot);
    const m = new THREE.Mesh(new THREE.CapsuleGeometry(r, len, 4, 10), mat); m.position.y = -len / 2 - r * 0.5; m.castShadow = true;
    const tip = new THREE.Mesh(new THREE.SphereGeometry(r * 1.05, 10, 8), end); tip.position.set(0, -len - r, r * 0.3); tip.scale.set(1, 0.7, 1.4); tip.castShadow = true;
    pv.add(m, tip); g.add(pv); return pv;
  };
  const hipL = limb(0.13, 0.5, legMat, shoe, v(-0.15, 0.78, 0)), hipR = limb(0.13, 0.5, legMat, shoe, v(0.15, 0.78, 0));
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.3, 0.5, 6, 16), vestM); body.position.y = 1.22; body.castShadow = true;
  const band = new THREE.Mesh(new THREE.CylinderGeometry(0.305, 0.305, 0.06, 20), glow(0xe8f0ff, 0.25)); band.position.y = 1.1;
  const shL = limb(0.085, 0.42, vestM, skin, v(-0.39, 1.52, 0)), shR = limb(0.085, 0.42, vestM, skin, v(0.39, 1.52, 0));
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.23, 24, 18), skin); head.position.y = 1.88; head.castShadow = true;
  const capM = new THREE.Mesh(new THREE.SphereGeometry(0.245, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), std(cap)); capM.position.y = 1.92; capM.castShadow = true;
  const brim = box(0.32, 0.03, 0.22, std(cap)); brim.position.set(0, 1.93, 0.21);
  g.add(body, band, head, capM, brim);
  const walk = (phase: number, amount: number) => {
    const s = Math.sin(phase) * 0.6 * amount;
    hipL.rotation.x = s; hipR.rotation.x = -s; shL.rotation.x = -s * 0.8; shR.rotation.x = s * 0.8;
    body.position.y = 1.22 + Math.abs(Math.cos(phase)) * 0.04 * amount; band.position.y = body.position.y - 0.12;
    head.position.y = body.position.y + 0.66; capM.position.y = head.position.y + 0.04; brim.position.y = head.position.y + 0.05;
  };
  const reach = (t: number) => { shR.rotation.x = -t * 2.3; shL.rotation.x = -t * 0.35; };
  return { g, walk, reach };
}

/** An open cardboard box; close(0..1) folds the top flaps shut and tapes it. */
function parcel(w: number, h: number, d: number) {
  const g = new THREE.Group();
  const m = std(COL.card, { roughness: 0.8 }), md = std(COL.cardDark, { roughness: 0.8 }), t = 0.04;
  const add = (o: THREE.Mesh, x: number, y: number, z: number) => { o.position.set(x, y, z); g.add(o); };
  add(box(w, t, d, m), 0, t / 2, 0); add(box(w, h, t, m), 0, h / 2, d / 2); add(box(w, h, t, m), 0, h / 2, -d / 2);
  add(box(t, h, d, md), -w / 2, h / 2, 0); add(box(t, h, d, md), w / 2, h / 2, 0);
  const flapF = new THREE.Group(), flapB = new THREE.Group(); flapF.position.set(0, h, d / 2); flapB.position.set(0, h, -d / 2);
  const ff = box(w, t, d / 2, m); ff.position.z = -d / 4; flapF.add(ff);
  const fb = box(w, t, d / 2, m); fb.position.z = d / 4; flapB.add(fb);
  const tape = box(0.16, 0.012, d + 0.02, std(0xe8d3a6, { roughness: 0.3 })); tape.position.y = h + 0.03;
  g.add(flapF, flapB, tape);
  const close = (c: number) => { flapF.rotation.x = lerp(-2.0, 0, c); flapB.rotation.x = lerp(2.0, 0, c); tape.visible = c > 0.98; };
  close(0);
  return { g, close, h };
}

/** A box truck facing +x: roller door at the back, wheels with hubs, headlights with light cones, tail lights. */
function truck() {
  const g = new THREE.Group();
  const white = std(0xdfe4ec, { roughness: 0.45 }), dark = std(0x141a26), glass = std(0x1a2438, { roughness: 0.08, metalness: 0.6 });
  const body = rbox(6.2, 3, 2.6, 0.14, white); body.position.set(-1.2, 2.0, 0);
  const stripe = box(6.22, 0.32, 2.62, std(COL.signal)); stripe.position.set(-1.2, 1.25, 0);
  const brand = panel(3.6, 0.9, 512, (gg, _w, h) => { text(gg, 'NEXUS FREIGHT', 0, h / 2, 64, '#1b2334', DISPLAY, 700); });
  brand.mesh.position.set(-1.2, 2.4, 1.32); g.add(brand.mesh);
  const brand2 = brand.mesh.clone(); brand2.position.z = -1.32; brand2.rotation.y = Math.PI; g.add(brand2);
  const cab = rbox(2.1, 2.3, 2.5, 0.3, std(COL.signal, { roughness: 0.35 })); cab.position.set(3.0, 1.65, 0);
  const wind = box(0.05, 1.0, 2.1, glass); wind.position.set(4.06, 2.15, 0);
  const grille = box(0.06, 0.5, 1.6, dark); grille.position.set(4.06, 1.1, 0);
  const bumper = box(0.25, 0.25, 2.5, dark); bumper.position.set(4.1, 0.65, 0);
  const door = new THREE.Group(); door.position.set(-4.32, 3.45, 0);
  const doorPanel = box(0.08, 2.85, 2.4, std(0xe2e6ee)); doorPanel.position.y = -1.43; door.add(doorPanel);
  g.add(body, stripe, cab, wind, grille, bumper, door);
  const wheels: THREE.Mesh[] = [];
  for (const [x, z] of [[2.9, 1.2], [2.9, -1.2], [-0.4, 1.2], [-0.4, -1.2], [-3.2, 1.2], [-3.2, -1.2]]) {
    const w = cyl(0.5, 0.4, dark, 28); w.rotation.x = Math.PI / 2; w.position.set(x, 0.5, z);
    const hub = cyl(0.22, 0.42, std(0x9aa3b5, { metalness: 0.6, roughness: 0.3 })); hub.rotation.x = Math.PI / 2; hub.position.copy(w.position);
    wheels.push(w, hub); g.add(w, hub);
  }
  const lampMat = glow(0xfff2d6, 0);
  for (const z of [0.85, -0.85]) { const l = box(0.06, 0.24, 0.5, lampMat, false); l.position.set(4.08, 1.25, z); g.add(l); }
  const coneMat = new THREE.MeshBasicMaterial({ color: 0xfff1d0, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide });
  for (const z of [0.85, -0.85]) {
    const cone = new THREE.Mesh(new THREE.ConeGeometry(2.2, 9, 24, 1, true), coneMat);
    cone.rotation.z = Math.PI / 2; cone.position.set(8.6, 0.9, z); g.add(cone);
  }
  for (const z of [1.1, -1.1]) { const t = box(0.06, 0.3, 0.2, glow(COL.stop, 2), false); t.position.set(-4.33, 0.9, z); g.add(t); }
  const open = (t: number) => { door.scale.y = Math.max(0.04, 1 - t); };
  const roll = (d: number) => wheels.forEach(w => (w.rotation.y = d / 0.5));
  const lights = (k: number) => { lampMat.emissiveIntensity = 3 * k; coneMat.opacity = 0.05 * k; };
  return { g, open, roll, lights };
}

/** A counterbalance forklift facing -x (forks at -x) with a driver, overhead guard and a flashing beacon. */
function forklift() {
  const g = new THREE.Group();
  const yellow = std(COL.fork, { roughness: 0.4 }), dark = std(0x1a2030), steel = std(0x6b7487, { metalness: 0.5, roughness: 0.35 });
  const body = rbox(2.2, 1.1, 1.4, 0.18, yellow); body.position.set(0.2, 0.95, 0);
  const counter = rbox(0.6, 1.2, 1.38, 0.2, std(0x2a3142)); counter.position.set(1.2, 1.05, 0);
  const seat = rbox(0.55, 0.25, 0.6, 0.08, dark); seat.position.set(0.5, 1.62, 0);
  g.add(body, counter, seat);
  for (const [x, z] of [[-0.4, 0.62], [-0.4, -0.62], [0.95, 0.62], [0.95, -0.62]]) { const post = box(0.07, 1.6, 0.07, dark); post.position.set(x, 2.25, z); g.add(post); }
  const roof = box(1.55, 0.06, 1.35, dark); roof.position.set(0.28, 3.05, 0); g.add(roof);
  for (const [x, z, r] of [[-0.5, 0.72, 0.42], [-0.5, -0.72, 0.42], [1.0, 0.72, 0.34], [1.0, -0.72, 0.34]] as const) {
    const w = cyl(r, 0.32, dark, 22); w.rotation.x = Math.PI / 2; w.position.set(x, r, z); g.add(w);
  }
  const mast = new THREE.Group(); mast.position.set(-1.0, 0, 0); g.add(mast);
  for (const z of [0.42, -0.42]) { const rail = box(0.1, 3.2, 0.1, steel); rail.position.set(0, 1.7, z); mast.add(rail); }
  const carriage = new THREE.Group(); mast.add(carriage);
  const plate = box(0.1, 0.7, 1.0, steel); plate.position.y = 0.5; carriage.add(plate);
  for (const z of [0.32, -0.32]) { const f = box(1.3, 0.06, 0.14, steel); f.position.set(-0.65, 0.18, z); carriage.add(f); }
  const driver = person(COL.signal, COL.ink); driver.g.scale.setScalar(0.85); driver.g.position.set(0.5, 0.9, 0); driver.g.rotation.y = -Math.PI / 2; g.add(driver.g);
  const beacon = new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 10), glow(COL.ember, 3)); beacon.position.set(0.9, 3.15, 0); g.add(beacon);
  const lift = (h: number) => { carriage.position.y = h; };
  return { g, lift, beacon };
}

/** Rack rows: uprights, beams, decks and cartons of mixed sizes and shades (one instanced mesh). */
function racks(scene: THREE.Scene, rows: { x0: number; z: number; bays: number }[]) {
  const upright = std(COL.signal, { roughness: 0.45 }), beam = std(COL.ember, { roughness: 0.45 }), deck = std(0x9aa3b4, { roughness: 0.6 });
  const list: { m: THREE.Matrix4; c: THREE.Color }[] = [];
  const rnd = mulberry(11);
  const shades = [0xd2a86f, 0xc29558, 0xe0bb84, 0xb98a52, 0xdcd4c4].map(c => new THREE.Color(c));
  for (const r of rows) {
    for (let b = 0; b <= r.bays; b++) for (const dz of [-0.6, 0.6]) { const u = box(0.12, 4.4, 0.12, upright); u.position.set(r.x0 + b * 2.4, 2.2, r.z + dz); scene.add(u); }
    for (let b = 0; b < r.bays; b++) {
      for (let s = 0; s < 3; s++) {
        const y = 0.3 + s * 1.4;
        const d = box(2.3, 0.06, 1.25, deck, false); d.position.set(r.x0 + 1.2 + b * 2.4, y, r.z); d.receiveShadow = true; scene.add(d);
        for (const dz of [-0.62, 0.62]) { const be = box(2.3, 0.14, 0.08, beam, false); be.position.set(r.x0 + 1.2 + b * 2.4, y, r.z + dz); scene.add(be); }
        let x = r.x0 + 0.15 + b * 2.4;
        while (true) {
          const w = 0.5 + rnd() * 0.55, h = 0.4 + rnd() * 0.55, dd = 0.7 + rnd() * 0.3;
          if (x + w > r.x0 + 2.3 + b * 2.4) break;
          if (rnd() > 0.12) list.push({ m: new THREE.Matrix4().compose(v(x + w / 2, y + 0.03 + h / 2, r.z + (rnd() - 0.5) * 0.15), new THREE.Quaternion().setFromAxisAngle(v(0, 1, 0), (rnd() - 0.5) * 0.08), v(w, h, dd)), c: shades[Math.floor(rnd() * shades.length)] });
          x += w + 0.06;
        }
      }
    }
  }
  const inst = new THREE.InstancedMesh(new RoundedBoxGeometry(1, 1, 1, 2, 0.04), std(0xffffff, { roughness: 0.85 }), list.length);
  list.forEach((it, i) => { inst.setMatrixAt(i, it.m); inst.setColorAt(i, it.c); });
  inst.castShadow = true; inst.receiveShadow = true;
  scene.add(inst);
}

/** A walk along waypoints: position, heading and distance walked at fraction t. */
function pathAt(pts: THREE.Vector3[], t: number) {
  const lens = pts.slice(1).map((p, i) => p.distanceTo(pts[i]));
  const total = lens.reduce((a, b) => a + b, 0);
  let d = clamp(t) * total;
  for (let i = 0; i < lens.length; i++) {
    if (d <= lens[i] || i === lens.length - 1) {
      const k = lens[i] === 0 ? 0 : clamp(d / lens[i]);
      const dir = pts[i + 1].clone().sub(pts[i]);
      return { pos: pts[i].clone().lerp(pts[i + 1], k), heading: Math.atan2(dir.x, dir.z), dist: clamp(t) * total };
    }
    d -= lens[i];
  }
  return { pos: pts[pts.length - 1].clone(), heading: 0, dist: total };
}

const FINISH = /* glsl */ `
  uniform sampler2D tDiffuse; uniform float uTime; uniform float uVignette; uniform float uGrain; varying vec2 vUv;
  float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
  void main(){
    vec4 c = texture2D(tDiffuse, vUv);
    vec2 q = vUv - 0.5;
    c.rgb *= 1.0 - uVignette * smoothstep(0.25, 0.85, length(q * vec2(1.15, 1.0)));
    c.rgb += (hash(vUv * 1024.0 + fract(uTime)) - 0.5) * uGrain;
    gl_FragColor = c;
  }`;

// ------------------------------------------------------------------ the film

/** ?filmsnap jumps straight to the scrolled frame (used for screenshots, where frames render slowly). */
const SNAP = typeof location !== 'undefined' && new URLSearchParams(location.search).has('filmsnap');

export function mountOrderWorld(host: HTMLElement, opts: { reduced: boolean }): OrderWorld | null {
  let renderer: THREE.WebGLRenderer;
  try { renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' }); } catch { return null; }
  const small = window.innerWidth < 760;
  const hi = !small && (navigator.hardwareConcurrency || 4) >= 6;     // ambient occlusion only where it is affordable
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-hidden', 'true');

  const NX = 28, GX = 44, SX = 62, WX = 84, PX = 124, DX = 146;
  const GLOBE = v(0, -420, 0);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color();
  const fog = new THREE.Fog(0xffffff, 30, 90);
  scene.fog = fog;
  const pmrem = new THREE.PMREMGenerator(renderer);
  const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environment = envTex;
  scene.environmentIntensity = 0.5;
  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 600);
  const hemi = new THREE.HemisphereLight(0xffffff, 0x222222, 1);
  const sun = new THREE.DirectionalLight(0xffffff, 2);
  sun.castShadow = true;
  sun.shadow.mapSize.set(small ? 1024 : 2048, small ? 1024 : 2048);
  Object.assign(sun.shadow.camera, { left: -24, right: 24, top: 24, bottom: -24, near: 1, far: 100 });
  sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.035;
  scene.add(hemi, sun, sun.target);
  const glowTex = glowTexture();
  const hc = new THREE.Color(), hcOff = new THREE.Color(0x0c1324), hcOn = new THREE.Color(0x7aa2ff).multiplyScalar(1.6);

  // ---------------- ground, grid, the route that lights up as the order travels
  const floorMat = std(0xffffff, { roughness: 0.82 });
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(700, 220), floorMat);
  ground.rotation.x = -Math.PI / 2; ground.position.x = 120; ground.receiveShadow = true; scene.add(ground);
  const grid = new THREE.GridHelper(700, 350, 0xffffff, 0xffffff);
  const gridMat = grid.material as THREE.LineBasicMaterial; gridMat.transparent = true; gridMat.opacity = 0.55; gridMat.vertexColors = false;
  grid.position.set(120, 0.012, 0); scene.add(grid);
  const routeLit = box(1, 0.03, 0.42, glow(COL.signal, 1.6), false); routeLit.position.y = 0.03; scene.add(routeLit);

  // giant words behind each chapter, the way the reference sets type behind its subjects
  const WORD_X = [0, NX, GX, SX + 4, WX + 8, WX + 14, PX, DX + 10];
  const words = ['ANY CHANNEL', 'ONE BRAIN', 'APPROVED', 'SHIP DATE', 'WAVE', 'PICK', 'PACK', 'SHIPPED'].map((w, i) => {
    const t = panel(46, 9, 2048, (g, cw, ch) => text(g, w, cw / 2, ch / 2, 330, '#ffffff', DISPLAY, 800, 'center'), { fog: false });
    t.mesh.position.set(WORD_X[i], 8, -30); t.mesh.scale.setScalar(1.3); t.mesh.renderOrder = 0;
    scene.add(t.mesh);
    return t;
  });

  // ================= 0. the customer, the phone, every channel
  const phone = new THREE.Group();
  phone.add(rbox(2.3, 4.6, 0.26, 0.3, std(0x0e131f, { roughness: 0.25, metalness: 0.5 })));
  const camBump = rbox(0.7, 0.7, 0.08, 0.12, std(0x0e131f, { roughness: 0.2, metalness: 0.6 })); camBump.position.set(-0.6, 1.6, -0.16); phone.add(camBump);
  let screenState = -1;
  const screen = panel(2.08, 4.3, 1120, () => { }, { fog: false });
  screen.mesh.position.z = 0.135; phone.add(screen.mesh);
  const screenLight = new THREE.PointLight(0x9fc0ff, 0, 6, 2); screenLight.position.z = 1; phone.add(screenLight);
  phone.position.set(0, 3.2, 0); phone.rotation.set(-0.08, 0.35, 0);
  scene.add(phone);
  const pedestal = new THREE.Mesh(new THREE.CylinderGeometry(2.6, 2.9, 0.35, 64), std(0xffffff, { roughness: 0.3 }));
  pedestal.position.set(0, 0.18, 0); pedestal.receiveShadow = true; pedestal.castShadow = true; scene.add(pedestal);
  const paintScreen = (state: number) => screen.repaint((g, cw, ch) => {
    // drawn on a 560-wide layout, scaled up so the dive through the screen stays sharp
    const k = cw / 560, w = 560, h = ch / k;
    g.setTransform(k, 0, 0, k, 0, 0);
    g.fillStyle = '#f7f8fb'; rr(g, 0, 0, w, h, 44); g.fill();
    text(g, '9:41', 40, 40, 22, '#0b1222', FONT, 700);
    text(g, 'Northwind Outfitters', 40, 100, 32, '#0b1222', FONT, 800);
    text(g, 'Checkout', 40, 142, 24, '#566074', FONT, 500);
    const item = (y: number, name: string, price: string, color: string) => {
      g.fillStyle = color; rr(g, 40, y, 100, 100, 18); g.fill();
      text(g, name, 162, y + 34, 27, '#0b1222', FONT, 650); text(g, price, 162, y + 72, 24, '#566074', FONT, 500);
    };
    item(190, 'Navy jacket · M', '$120.00', '#5b8cff'); item(320, 'Wool scarf', '$35.00', '#ff8a4c');
    g.fillStyle = '#e3e7ef'; g.fillRect(40, 450, w - 80, 2);
    text(g, 'Total', 40, 498, 28, '#0b1222', FONT, 650); text(g, '$155.00', w - 40, 498, 28, '#0b1222', FONT, 800, 'right');
    if (state === 0) { g.fillStyle = '#0b1222'; rr(g, 40, h - 180, w - 80, 100, 50); g.fill(); text(g, 'Place order', w / 2, h - 130, 32, '#fff', FONT, 750, 'center'); }
    else {
      g.fillStyle = '#3ddc97'; rr(g, 40, h - 250, w - 80, 170, 30); g.fill();
      text(g, '✓ Order placed', w / 2, h - 196, 34, '#05261a', FONT, 850, 'center');
      text(g, '#4821 · on its way to Nexus', w / 2, h - 140, 22, '#05261a', MONO, 650, 'center');
    }
    g.setTransform(1, 0, 0, 1, 0, 0);
  });
  paintScreen(0); screenState = 0;
  const tiles = CHANNELS.map((name, i) => {
    const grp = new THREE.Group();
    grp.add(rbox(2.5, 0.86, 0.14, 0.12, std(0xffffff, { roughness: 0.25 })));
    const face = panel(2.4, 0.78, 420, (g, w, h) => { text(g, name, w / 2, h / 2 + 2, name.length > 14 ? 40 : 48, '#0b1222', FONT, 750, 'center'); });
    face.mesh.position.z = 0.075; grp.add(face.mesh);
    const back = face.mesh.clone(); back.position.z = -0.075; back.rotation.y = Math.PI; grp.add(back);
    const dot = new THREE.Mesh(new THREE.SphereGeometry(0.07, 12, 10), glow(i === 0 ? COL.go : COL.signal, 2)); dot.position.set(-1.08, 0.26, 0.08); grp.add(dot);
    scene.add(grp);
    return grp;
  });
  const burstN = 160;
  const burstDirs = Array.from({ length: burstN }, (_, i) => { const r = mulberry(i + 3); return v(r() - 0.5, r() * 0.9 + 0.1, r() - 0.5).normalize().multiplyScalar(2 + r() * 3); });
  const burst = points(burstN, COL.go, 0.22, glowTex); scene.add(burst.obj);

  // ================= the order itself, with a glowing trail
  const token = new THREE.Group();
  const tokenMat = glow(COL.ember, 1.6);
  const tokenCube = rbox(0.9, 0.9, 0.9, 0.2, tokenMat); token.add(tokenCube);
  const tokenTag = panel(2.2, 0.62, 360, (g, w, h) => { card(g, w, h, 'rgba(255,138,76,.9)'); text(g, 'Order #4821', w / 2, h / 2, 44, '#fff', MONO, 700, 'center'); });
  tokenTag.mesh.position.y = 1.05; token.add(tokenTag.mesh);
  const tokenLight = new THREE.PointLight(COL.ember, 8, 8, 2); token.add(tokenLight);
  scene.add(token);
  const trailN = 40, trailPts: THREE.Vector3[] = Array.from({ length: trailN }, () => v(0, -60, 0));
  const trail = points(trailN, COL.ember, 0.3, glowTex); scene.add(trail.obj);

  // ================= 1. Nexus: the core, stock flowing in from ERP and POS
  const core = new THREE.Group(); core.position.set(NX, 0, 0); scene.add(core);
  const plinth = new THREE.Mesh(new THREE.CylinderGeometry(4.4, 4.9, 0.5, 6), std(0x161e33, { roughness: 0.3, metalness: 0.3 }));
  plinth.position.y = 0.25; plinth.castShadow = plinth.receiveShadow = true; core.add(plinth);
  const hexN = 54;
  const hexes = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.9, 0.9, 0.1, 6), new THREE.MeshBasicMaterial({ color: 0xffffff }), hexN);   // unlit: the instance colour is the glow
  const hexR: number[] = [];
  for (let i = 0; i < hexN; i++) {
    const ring = 6 + Math.floor(i / 18) * 1.6, a = (i % 18) / 18 * Math.PI * 2 + Math.floor(i / 18) * 0.17;
    hexR.push(ring);
    hexes.setMatrixAt(i, new THREE.Matrix4().makeTranslation(Math.cos(a) * ring, 0.06, Math.sin(a) * ring));
    hexes.setColorAt(i, hcOff);
  }
  core.add(hexes);
  const brainMat = new THREE.MeshStandardMaterial({ color: 0x9cb8ff, emissive: COL.signal, emissiveIntensity: 1.4, roughness: 0.15, metalness: 0.2 });
  const brain = new THREE.Mesh(new THREE.IcosahedronGeometry(1.3, 4), brainMat); brain.position.y = 3.1; core.add(brain);
  const shell = new THREE.Mesh(new THREE.IcosahedronGeometry(1.75, 1), new THREE.MeshBasicMaterial({ color: COL.signal, wireframe: true, transparent: true, opacity: 0.35 }));
  shell.position.y = 3.1; core.add(shell);
  const rings = [2.4, 2.9, 3.4].map((r, i) => {
    const m = new THREE.Mesh(new THREE.TorusGeometry(r, 0.035 + i * 0.008, 12, 160), glow(i === 1 ? COL.ember : COL.signal, 2.4));
    m.position.y = 3.1; m.rotation.x = Math.PI / (2.2 + i * 0.5); core.add(m); return m;
  });
  const coreLight = new THREE.PointLight(COL.signal, 26, 16, 2); coreLight.position.y = 3.2; core.add(coreLight);
  const coreTag = panel(3.4, 0.82, 460, (g, w, h) => { card(g, w, h, 'rgba(91,140,255,.85)'); text(g, 'NEXUS', w / 2, h / 2 + 2, 56, '#fff', DISPLAY, 700, 'center'); });
  coreTag.mesh.position.set(0, 5.5, 0); core.add(coreTag.mesh);
  const vortN = small ? 600 : 1400;
  const vortSeed = Array.from({ length: vortN }, (_, i) => { const r = mulberry(i * 7 + 1); return [r() * Math.PI * 2, 1.9 + r() * 2.8, r() * 5.5, 0.15 + r() * 0.5]; });
  const vort = points(vortN, 0x8fb0ff, 0.09, glowTex); vort.obj.position.set(NX, 0, 0); scene.add(vort.obj);
  const erp = new THREE.Group(); erp.position.set(NX - 9, 0, -6.5); scene.add(erp);
  const rack = rbox(2.4, 4.6, 1.7, 0.1, std(0x1a2236, { roughness: 0.3, metalness: 0.4 })); rack.position.y = 2.3; erp.add(rack);
  for (let i = 0; i < 10; i++) { const led = box(1.8, 0.05, 0.02, glow(i % 3 === 0 ? COL.go : COL.signal, 2.2), false); led.position.set(0, 0.8 + i * 0.4, 0.86); erp.add(led); }
  const erpTag = panel(3.8, 1.0, 500, (g, w, h) => { card(g, w, h); text(g, 'ERP', 32, h * 0.36, 44, '#fff', DISPLAY, 700); text(g, 'NetSuite · SAP · any', 32, h * 0.72, 28, '#8b97ad', MONO, 500); });
  erpTag.mesh.position.set(0, 5.4, 0); erp.add(erpTag.mesh);
  const pos = new THREE.Group(); pos.position.set(NX + 9, 0, -6.5); scene.add(pos);
  const counter = rbox(3.4, 1.2, 1.5, 0.12, std(0xe9edf4, { roughness: 0.35 })); counter.position.y = 0.6; pos.add(counter);
  const posScr = rbox(1.0, 0.75, 0.08, 0.05, std(0x0e131f, { roughness: 0.25 })); posScr.position.set(0.5, 1.75, 0); posScr.rotation.x = -0.4; pos.add(posScr);
  const posGlow = box(0.86, 0.6, 0.01, glow(COL.go, 1.6), false); posGlow.position.set(0.5, 1.76, 0.05); posGlow.rotation.x = -0.4; pos.add(posGlow);
  const posTag = panel(3.8, 1.0, 500, (g, w, h) => { card(g, w, h); text(g, 'Stores · POS', 32, h * 0.36, 42, '#fff', DISPLAY, 700); text(g, 'Shopify POS · any POS', 32, h * 0.72, 28, '#8b97ad', MONO, 500); });
  posTag.mesh.position.set(0, 3.3, 0); pos.add(posTag.mesh);
  const beamMat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    uniforms: { uTime: { value: 0 }, uOn: { value: 0 }, uColor: { value: new THREE.Color(COL.go) } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'uniform float uTime; uniform float uOn; uniform vec3 uColor; varying vec2 vUv; void main(){ float d = fract(vUv.x * 6.0 - uTime * 1.4); float a = smoothstep(0.0, 0.25, d) * (1.0 - smoothstep(0.35, 0.6, d)); gl_FragColor = vec4(uColor * 2.0, (0.25 + a) * uOn); }',
  });
  [erp.position.clone().setY(4.2), pos.position.clone().setY(1.9)].forEach(from => {
    const curve = new THREE.QuadraticBezierCurve3(from, from.clone().lerp(v(NX, 7, 0), 0.5).setY(7.2), v(NX, 3.2, 0));
    scene.add(new THREE.Mesh(new THREE.TubeGeometry(curve, 64, 0.07, 8, false), beamMat));
  });
  const LOCS = [{ n: 'Warehouse · East', on: 41, safe: 5 }, { n: 'Store · New York', on: 3, safe: 2 }, { n: 'Store · Boston', on: 4, safe: 2 }];
  let stockStep = -1;
  const stock = panel(6.4, 3.4, 960, () => { });
  stock.mesh.position.set(NX - 1.5, 6.9, -4.6); scene.add(stock.mesh);
  const paintStock = (k: number) => stock.repaint((g, w, h) => {
    card(g, w, h, 'rgba(61,220,151,.65)');
    text(g, 'Navy jacket · M', 44, 64, 40, '#fff', FONT, 750);
    text(g, 'on hand − safety stock = sellable', w - 44, 64, 24, '#8b97ad', MONO, 500, 'right');
    LOCS.forEach((l, i) => {
      const y = 148 + i * 132, max = 45, bw = w - 360, shown = Math.round(l.on * clamp(k * 1.4 - i * 0.15));
      text(g, l.n, 44, y, 28, '#c7d0e0', FONT, 600);
      g.fillStyle = 'rgba(255,255,255,.08)'; rr(g, 44, y + 28, bw, 32, 16); g.fill();
      g.fillStyle = '#3ddc97'; rr(g, 44, y + 28, Math.max(32, bw * (shown / max)), 32, 16); g.fill();
      if (k > 0.55) { g.fillStyle = 'rgba(255,93,108,.9)'; rr(g, 44, y + 28, Math.max(20, bw * (Math.min(l.safe, shown) / max)), 32, 16); g.fill(); }
      text(g, k > 0.55 ? `${l.on} − ${l.safe} = ${Math.max(0, l.on - l.safe)}` : String(shown), w - 44, y + 44, 32, '#fff', MONO, 700, 'right');
    });
  });

  // ================= 2. approval gate
  const gate = new THREE.Group(); gate.position.set(GX, 0, 0); scene.add(gate);
  const pillarMat = std(0x18213a, { roughness: 0.3, metalness: 0.4 });
  for (const z of [-2.3, 2.3]) { const pl = rbox(0.65, 4.4, 0.65, 0.12, pillarMat); pl.position.set(0, 2.2, z); gate.add(pl); }
  const beamTop = rbox(0.75, 0.55, 5.3, 0.12, pillarMat); beamTop.position.y = 4.5; gate.add(beamTop);
  const gateLightMat = glow(COL.signal, 2.6);
  for (const z of [-1.95, 1.95]) { const s = box(0.06, 3.8, 0.06, gateLightMat, false); s.position.set(0.36, 2.2, z); gate.add(s); }
  const gateTop = box(0.06, 0.06, 4.0, gateLightMat, false); gateTop.position.set(0.4, 4.2, 0); gate.add(gateTop);
  const scanMat = new THREE.MeshBasicMaterial({ color: COL.signal, transparent: true, opacity: 0.6, side: THREE.DoubleSide, toneMapped: false, blending: THREE.AdditiveBlending, depthWrite: false });
  const scanPlane = new THREE.Mesh(new THREE.PlaneGeometry(3.9, 0.5), scanMat); scanPlane.rotation.y = Math.PI / 2; gate.add(scanPlane);
  const CHECKS = ['Payment captured', 'Address valid', 'Order value under limit', 'Not on a block list'];
  let checksStep = -1;
  const checks = panel(4.6, 3.2, 680, () => { });
  checks.mesh.position.set(GX + 1.2, 4.5, -4.2); scene.add(checks.mesh);
  const paintChecks = (n: number, ok: boolean) => checks.repaint((g, w, h) => {
    card(g, w, h, ok ? 'rgba(61,220,151,.95)' : 'rgba(91,140,255,.75)');
    text(g, 'Approval rules', 40, 58, 38, '#fff', FONT, 750);
    CHECKS.forEach((c, i) => {
      const y = 128 + i * 64, done = i < n;
      g.fillStyle = done ? '#3ddc97' : 'rgba(255,255,255,.12)'; g.beginPath(); g.arc(58, y, 17, 0, Math.PI * 2); g.fill();
      if (done) text(g, '✓', 58, y + 1, 24, '#05261a', FONT, 900, 'center');
      text(g, c, 92, y, 28, done ? '#e8edf7' : '#6b768c', FONT, 500);
    });
    if (ok) { g.fillStyle = '#3ddc97'; rr(g, 40, h - 80, w - 80, 54, 27); g.fill(); text(g, 'APPROVED', w / 2, h - 52, 28, '#05261a', MONO, 800, 'center'); }
  });

  // ================= 3. ship date: B2C now, B2B later; a forklift moves the released pallet
  const laneB2C = box(24, 0.03, 0.42, glow(COL.go, 1.1), false); laneB2C.position.set(SX + 10, 0.034, 3.4); scene.add(laneB2C);
  const laneB2B = box(24, 0.03, 0.42, glow(COL.warn, 0.9), false); laneB2B.position.set(SX + 10, 0.034, -4.4); scene.add(laneB2B);
  const sign = (title: string, sub: string, border: string, subCol: string) => panel(4.8, 1.3, 680, (g, w, h) => { card(g, w, h, border); text(g, title, 34, h * 0.36, 44, '#fff', FONT, 750); text(g, sub, 34, h * 0.72, 28, subCol, MONO, 500); });
  const signC = sign('B2C · ship today', 'Reserve now, fulfil now', 'rgba(61,220,151,.95)', '#9fe9c8');
  signC.mesh.position.set(SX + 2.5, 2.6, 5.4); scene.add(signC.mesh);
  const signB = sign('B2B · ship by 15 Jan', 'Hold; release near ship date', 'rgba(255,200,87,.95)', '#ffe2a3');
  signB.mesh.position.set(SX + 0.5, 2.6, -7.6); scene.add(signB.mesh);
  const pallet = new THREE.Group();
  const deckP = box(2.2, 0.22, 1.8, std(COL.wood, { roughness: 0.8 })); deckP.position.y = 0.11; pallet.add(deckP);
  for (let x = 0; x < 2; x++) for (let z = 0; z < 2; z++) for (let y = 0; y < 2; y++) {
    const c = rbox(1.0, 0.75, 0.85, 0.05, std(y ? COL.card : COL.cardDark, { roughness: 0.8 })); c.position.set(-0.52 + x * 1.04, 0.62 + y * 0.77, -0.44 + z * 0.88); pallet.add(c);
  }
  const shrink = new THREE.Mesh(new THREE.BoxGeometry(2.15, 1.6, 1.82), new THREE.MeshPhysicalMaterial({ color: 0xffffff, transparent: true, opacity: 0.18, roughness: 0.1, clearcoat: 1 }));
  shrink.position.y = 1.0; pallet.add(shrink);
  const palletTag = panel(2.9, 0.66, 440, (g, w, h) => { card(g, w, h, 'rgba(255,200,87,.95)'); text(g, 'PO-881 · 240 units', w / 2, h / 2, 38, '#fff', MONO, 700, 'center'); });
  palletTag.mesh.position.y = 2.6; pallet.add(palletTag.mesh);
  scene.add(pallet);
  const lift = forklift(); scene.add(lift.g);
  let calStep = -1;
  const cal = panel(4.6, 3.4, 680, () => { });
  cal.mesh.position.set(SX + 9, 3.3, -8.2); scene.add(cal.mesh);
  const DAYS = ['12 Nov', '1 Dec', '20 Dec', '2 Jan', '12 Jan'];
  const paintCal = (k: number) => cal.repaint((g, w, h) => {
    const i = Math.min(DAYS.length - 1, Math.floor(k * DAYS.length)), rel = i === DAYS.length - 1;
    card(g, w, h, rel ? 'rgba(61,220,151,.95)' : 'rgba(255,200,87,.85)');
    text(g, 'Today', 40, 56, 28, '#8b97ad', MONO, 600);
    text(g, DAYS[i], 40, 120, 70, '#fff', DISPLAY, 700);
    text(g, 'Ship by 15 Jan', 40, 196, 30, '#c7d0e0', FONT, 600);
    g.fillStyle = 'rgba(255,255,255,.1)'; rr(g, 40, 240, w - 80, 24, 12); g.fill();
    g.fillStyle = rel ? '#3ddc97' : '#ffc857'; rr(g, 40, 240, (w - 80) * ((i + 1) / DAYS.length), 24, 12); g.fill();
    text(g, rel ? 'Released: reserve and pick now' : 'Not reserved yet: stock serves other orders', 40, 306, 25, rel ? '#9fe9c8' : '#ffe2a3', FONT, 600);
  });

  // ================= 4–5. the warehouse: floor, lights, racks, a wave and a picker
  const slab = new THREE.Mesh(new THREE.BoxGeometry(46, 0.08, 26), std(0xe9edf3, { roughness: 0.7 }));
  slab.position.set(WX + 21, 0.04, -1); slab.receiveShadow = true; scene.add(slab);
  const yellow = std(COL.warn, { roughness: 0.6 });
  for (const z of [-3.2, 3.2]) { const line = box(26, 0.012, 0.12, yellow, false); line.position.set(WX + 13, 0.086, z); scene.add(line); }
  for (let i = 0; i < 9; i++) { const c = box(0.5, 0.012, 0.5, std(COL.signal), false); c.position.set(WX + 3 + i * 3, 0.087, 0); scene.add(c); }
  racks(scene, [{ x0: WX + 2, z: -4.8, bays: 8 }, { x0: WX + 2, z: -9.6, bays: 8 }, { x0: WX + 2, z: 4.8, bays: 8 }]);
  for (let i = 0; i < 5; i++) for (const z of [-2, 2]) { const lamp = box(3.2, 0.08, 0.5, glow(0xf4f8ff, 1.6), false); lamp.position.set(WX + 4 + i * 4.4, 7.2, z); scene.add(lamp); }
  for (const x of [WX - 2, WX + 22, WX + 44]) { const col = box(0.6, 8, 0.6, std(0xd7dde7)); col.position.set(x, 4, -12); scene.add(col); }
  const BIN_A = v(WX + 7.0, 1.72, -4.1), BIN_B = v(WX + 14.2, 3.12, 4.1);
  const binMarker = (p: THREE.Vector3, label: string) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.05, 10, 50), glow(COL.signal, 3)); ring.position.copy(p); ring.rotation.y = Math.PI / 2; scene.add(ring);
    const tag = panel(1.9, 0.52, 320, (g, w, h) => { card(g, w, h, 'rgba(91,140,255,.95)'); text(g, label, w / 2, h / 2, 38, '#fff', MONO, 700, 'center'); });
    tag.mesh.position.copy(p).add(v(0, 0.95, 0)); scene.add(tag.mesh);
    return { ring, tag };
  };
  const markA = binMarker(BIN_A, 'A-03-2'), markB = binMarker(BIN_B, 'B-07-3');
  const itemA = rbox(0.62, 0.42, 0.52, 0.07, std(COL.signal, { roughness: 0.4 })); scene.add(itemA);
  const itemB = rbox(0.52, 0.3, 0.42, 0.07, std(COL.ember, { roughness: 0.4 })); scene.add(itemB);
  let waveStep = -1;
  const wave = panel(5.8, 3.4, 860, () => { });
  wave.mesh.position.set(WX + 5, 5.2, 0.5); scene.add(wave.mesh);
  const paintWave = (k: number) => wave.repaint((g, w, h) => {
    card(g, w, h, 'rgba(91,140,255,.85)');
    text(g, 'Pick wave W-12', 40, 60, 42, '#fff', FONT, 750);
    text(g, k > 0.5 ? '18 orders · 31 lines · 1 route' : 'Grouping orders…', 40, 112, 28, '#8b97ad', MONO, 600);
    const n = Math.round(18 * clamp(k * 1.3));
    for (let i = 0; i < 18; i++) { g.fillStyle = i < n ? (i === 0 ? '#ff8a4c' : '#5b8cff') : 'rgba(255,255,255,.08)'; rr(g, 40 + (i % 9) * 86, 150 + Math.floor(i / 9) * 72, 74, 56, 12); g.fill(); }
    if (k > 0.75) { g.fillStyle = '#3ddc97'; rr(g, 40, h - 80, w - 80, 54, 27); g.fill(); text(g, 'Assigned to picker: Sam', w / 2, h - 52, 28, '#05261a', MONO, 800, 'center'); }
  });
  const picker = person(COL.ember, COL.signal);
  const cart = new THREE.Group();
  const cartTop = box(1.0, 0.05, 0.7, std(COL.steel, { metalness: 0.5, roughness: 0.35 })); cartTop.position.y = 0.6; cart.add(cartTop);
  const tote = new THREE.Group(), toteMat = std(COL.signal, { roughness: 0.45 });
  for (const [w, h, d, x, y, z] of [[0.9, 0.05, 0.6, 0, 0, 0], [0.9, 0.4, 0.05, 0, 0.2, 0.3], [0.9, 0.4, 0.05, 0, 0.2, -0.3], [0.05, 0.4, 0.6, 0.45, 0.2, 0], [0.05, 0.4, 0.6, -0.45, 0.2, 0]] as const) { const m = box(w, h, d, toteMat); m.position.set(x, y, z); tote.add(m); }
  tote.position.y = 0.66; cart.add(tote);
  for (const [x, z] of [[0.4, 0.3], [-0.4, 0.3], [0.4, -0.3], [-0.4, -0.3]]) {
    const w = cyl(0.1, 0.06, std(COL.ink), 14); w.rotation.x = Math.PI / 2; w.position.set(x, 0.1, z); cart.add(w);
    const leg = box(0.04, 0.55, 0.04, std(COL.steel)); leg.position.set(x, 0.35, z); cart.add(leg);
  }
  const handle = box(0.05, 0.5, 0.7, std(COL.steel)); handle.position.set(-0.52, 0.95, 0); cart.add(handle);
  const pickerRig = new THREE.Group();
  picker.g.position.x = -0.95; picker.g.rotation.y = Math.PI / 2;
  pickerRig.add(picker.g, cart); scene.add(pickerRig);
  const scanMatR = new THREE.MeshBasicMaterial({ color: COL.stop, transparent: true, opacity: 0, toneMapped: false });
  const scanBeam = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 1, 6), scanMatR); scene.add(scanBeam);
  const pickerTag = panel(2.0, 0.52, 320, (g, w, h) => { card(g, w, h, 'rgba(255,138,76,.95)'); text(g, 'Sam · W-12', w / 2, h / 2, 36, '#fff', MONO, 700, 'center'); });
  pickerTag.mesh.position.set(-0.95, 2.7, 0); pickerRig.add(pickerTag.mesh);
  const AISLE_A = -2.4, AISLE_B = 2.4;
  const PICK_PATH = [v(WX - 1, 0, 0), v(WX + 1.5, 0, AISLE_A), v(BIN_A.x - 0.2, 0, AISLE_A), v(BIN_A.x + 0.2, 0, AISLE_A),
    v(BIN_B.x - 1.5, 0, AISLE_A), v(BIN_B.x - 1.0, 0, AISLE_B), v(BIN_B.x - 0.2, 0, AISLE_B), v(BIN_B.x + 0.2, 0, AISLE_B),
    v(WX + 23, 0, 0.6), v(PX - 3.6, 0, 1.2)];
  const HOLD = 0.08, WALK = 1 - 2 * HOLD;
  const pathLen = (n: number) => PICK_PATH.slice(1, n + 1).reduce((a, q, i) => a + q.distanceTo(PICK_PATH[i]), 0);
  const STOP_A = pathLen(2) / pathLen(PICK_PATH.length - 1), STOP_B = pathLen(6) / pathLen(PICK_PATH.length - 1);
  const A1 = STOP_A * WALK, B1 = STOP_B * WALK + HOLD;
  const pause = (t: number) => {
    if (t < A1) return { t: t / WALK, picking: -1 };
    if (t < A1 + HOLD) return { t: STOP_A, picking: (t - A1) / HOLD };
    if (t < B1) return { t: (t - HOLD) / WALK, picking: -1 };
    if (t < B1 + HOLD) return { t: STOP_B, picking: (t - B1) / HOLD };
    return { t: (t - 2 * HOLD) / WALK, picking: -1 };
  };

  // ================= 6. packing
  const table = new THREE.Group(); table.position.set(PX, 0, 0); scene.add(table);
  const top = rbox(4.4, 0.16, 2.1, 0.05, std(0xf6f7fa, { roughness: 0.35 })); top.position.y = 1.0; table.add(top);
  for (const [x, z] of [[2.0, 0.9], [-2.0, 0.9], [2.0, -0.9], [-2.0, -0.9]]) { const l = box(0.1, 1.0, 0.1, std(COL.steel, { metalness: 0.5 })); l.position.set(x, 0.5, z); table.add(l); }
  const printer = rbox(0.75, 0.5, 0.62, 0.07, std(0x2a3348)); printer.position.set(1.65, 1.33, -0.55); table.add(printer);
  const printLed = box(0.08, 0.04, 0.02, glow(COL.go, 3), false); printLed.position.set(1.4, 1.48, -0.24); table.add(printLed);
  const labelPaper = box(0.5, 0.01, 0.44, std(0xffffff, { roughness: 0.8 })); labelPaper.position.set(1.65, 1.59, -0.28); table.add(labelPaper);
  const monitor = panel(2.6, 1.65, 580, () => { });
  monitor.mesh.position.set(PX - 1.1, 2.45, -1.1); monitor.mesh.rotation.y = 0.2; scene.add(monitor.mesh);
  const monArm = box(0.08, 1.3, 0.08, std(COL.steel)); monArm.position.set(PX - 1.1, 1.6, -1.15); scene.add(monArm);
  let monStep = -1;
  const paintMonitor = (k: number) => monitor.repaint((g, w, h) => {
    card(g, w, h, k > 0.3 ? 'rgba(61,220,151,.95)' : 'rgba(91,140,255,.85)', 'rgba(9,14,27,.97)');
    text(g, 'Packing · #4821', 32, 50, 32, '#fff', FONT, 750); text(g, '2 items · 0.9 kg', 32, 94, 24, '#8b97ad', MONO, 600);
    if (k > 0.3) { text(g, 'Recommended box', 32, 150, 24, '#8b97ad', FONT, 600); text(g, 'M · 30 × 20 × 15 cm', 32, 198, 38, '#3ddc97', MONO, 800); text(g, '1 box · 78% full', 32, 246, 24, '#c7d0e0', MONO, 600); }
    if (k > 0.8) { g.fillStyle = '#ffc857'; rr(g, 32, h - 76, w - 64, 50, 25); g.fill(); text(g, 'Label printed · 1Z 999 AA1 01 2345 6784', w / 2, h - 51, 20, '#3a2a00', MONO, 800, 'center'); }
  });
  const ghostMat = new THREE.LineBasicMaterial({ color: COL.go, transparent: true, opacity: 0, toneMapped: false });
  const ghost = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.5, 1.0, 1.1)), ghostMat);
  ghost.position.set(PX, 1.6, 0.1); scene.add(ghost);
  const pkg = parcel(1.5, 1.0, 1.1); scene.add(pkg.g);
  const label = panel(0.64, 0.52, 240, (g, w, h) => {
    g.fillStyle = '#fff'; g.fillRect(0, 0, w, h); text(g, 'UPS GROUND', 14, 26, 22, '#000', MONO, 800);
    for (let i = 0; i < 50; i++) { g.fillStyle = '#000'; g.fillRect(14 + i * 4, 54, (i * 7) % 3 + 1, 74); }
    text(g, '#4821', 14, 160, 24, '#000', MONO, 700);
  });
  label.mat.transparent = false; label.mesh.rotation.x = -Math.PI / 2; scene.add(label.mesh);
  const packer = person(COL.go, COL.ink); packer.g.position.set(PX, 0, -1.7); scene.add(packer.g);

  // ================= 7. dispatch in the evening
  const convLen = DX - 3 - (PX + 2.4);
  const belt = box(convLen, 0.12, 1.3, std(0x262f45, { roughness: 0.85 })); belt.position.set(PX + 2.4 + convLen / 2, 0.95, 0); scene.add(belt);
  for (let i = 0; i <= convLen; i += 2) { const l = box(0.1, 0.9, 1.3, std(COL.steel)); l.position.set(PX + 2.4 + i, 0.45, 0); scene.add(l); }
  for (const z of [-0.7, 0.7]) { const rail = box(convLen, 0.12, 0.06, std(COL.warn), false); rail.position.set(PX + 2.4 + convLen / 2, 1.08, z); scene.add(rail); }
  const dock = new THREE.Group(); dock.position.set(DX, 0, 0); scene.add(dock);
  const wallMat = std(0x2a3450, { roughness: 0.8 });
  for (const z of [-6.2, 6.2]) { const w = box(0.6, 4.6, 6, wallMat); w.position.set(0, 2.3, z); dock.add(w); }
  const header = box(0.6, 0.5, 6.4, wallMat); header.position.set(0, 4.35, 0); dock.add(header);
  const dockDoor = box(0.15, 4.0, 6.4, std(0x3a4566)); dockDoor.position.set(0.1, 2.1, 0); dock.add(dockDoor);
  for (const z of [-1.6, 1.6]) { const b = rbox(0.4, 0.6, 0.5, 0.06, std(0x0e1220)); b.position.set(0.5, 1.0, z); dock.add(b); }
  const dockLamp = box(0.12, 0.18, 1.6, glow(COL.go, 3), false); dockLamp.position.set(0.45, 4.7, 0); dock.add(dockLamp);
  const stripes = box(0.02, 0.4, 6.4, std(COL.warn), false); stripes.position.set(0.32, 0.25, 0); dock.add(stripes);
  const dockTag = panel(3.6, 0.82, 500, (g, w, h) => { card(g, w, h, 'rgba(61,220,151,.85)'); text(g, 'DOCK 3 · UPS 17:30', w / 2, h / 2, 38, '#fff', MONO, 700, 'center'); });
  dockTag.mesh.position.set(0.45, 5.4, 0); dockTag.mesh.rotation.y = Math.PI / 2; dock.add(dockTag.mesh);
  const extra = [0, 1, 2, 3].map(i => { const p = rbox(1.0 - i * 0.1, 0.7, 0.9, 0.05, std(i % 2 ? COL.card : COL.cardDark, { roughness: 0.8 })); scene.add(p); return p; });
  const van = truck(); scene.add(van.g);
  const road = box(300, 0.02, 5, std(0x1a2238, { roughness: 0.9 }), false); road.position.set(DX + 150, 0.016, 0); scene.add(road);
  for (let i = 0; i < 60; i++) { const d = box(1.8, 0.03, 0.14, std(0xd8dde8), false); d.position.set(DX + 6 + i * 5, 0.03, 0); scene.add(d); }
  const lampHeads: THREE.MeshStandardMaterial[] = [];
  for (let i = 0; i < 14; i++) {
    const x = DX + 10 + i * 12, z = i % 2 ? 4.2 : -4.2, s = Math.sign(z);
    const pole = cyl(0.08, 6, std(0x3b4560, { metalness: 0.4 }), 10); pole.position.set(x, 3, z); scene.add(pole);
    const arm = box(0.08, 0.08, 1.2, std(0x3b4560)); arm.position.set(x, 5.95, z - s * 0.55); scene.add(arm);
    const hm = glow(0xffe2b0, 0); lampHeads.push(hm);
    const head = box(0.3, 0.12, 0.5, hm, false); head.position.set(x, 5.9, z - s * 1.1); scene.add(head);
  }

  // ================= 8. everywhere: the planet, its continents, and orders on every route
  const planet = new THREE.Group(); planet.position.copy(GLOBE); scene.add(planet);
  const ll = (lat: number, lon: number, r = 10) => { const ph = (90 - lat) * Math.PI / 180, th = (lon + 180) * Math.PI / 180; return v(-r * Math.sin(ph) * Math.cos(th), r * Math.cos(ph), r * Math.sin(ph) * Math.sin(th)); };
  planet.add(new THREE.Points(new THREE.BufferGeometry().setFromPoints((LAND as [number, number][]).map(([lon, lat]) => ll(lat, lon))),
    new THREE.PointsMaterial({ color: 0xcfdcff, size: 0.34, map: glowTex, transparent: true, depthWrite: false, opacity: 0.95, fog: false })));
  planet.add(new THREE.Mesh(new THREE.SphereGeometry(9.92, 64, 64), new THREE.MeshBasicMaterial({ color: 0x04060d, fog: false })));
  planet.add(new THREE.Mesh(new THREE.SphereGeometry(10.9, 64, 64), new THREE.ShaderMaterial({
    transparent: true, side: THREE.BackSide, depthWrite: false, blending: THREE.AdditiveBlending,
    vertexShader: 'varying vec3 vN; void main(){ vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'varying vec3 vN; void main(){ float t = clamp(-vN.z / 0.55, 0.0, 1.0); float i = pow(t, 2.4); gl_FragColor = vec4(mix(vec3(0.3,0.45,1.0), vec3(1.0,0.55,0.3), 0.25) * i * 1.4, i); }',
  })));
  const HUBS = [[40.7, -74], [34, -118.2], [41.9, -87.6], [51.5, -0.1], [51.9, 4.5], [48.9, 2.4], [19.1, 72.9], [1.35, 103.8], [35.7, 139.7], [-33.9, 151.2], [-23.5, -46.6], [25.2, 55.3], [52.5, 13.4], [43.7, -79.4]];
  const arcs: THREE.Vector3[][] = [];
  const arcMat = new THREE.LineBasicMaterial({ color: COL.signal, transparent: true, opacity: 0.6, fog: false });
  const arcHot = new THREE.LineBasicMaterial({ color: COL.ember, transparent: true, opacity: 0.95, fog: false });
  for (let i = 0; i < 48; i++) {
    const r = mulberry(i * 13 + 5), a = HUBS[Math.floor(r() * HUBS.length)], b = HUBS[Math.floor(r() * HUBS.length)];
    if (a === b) continue;
    const A = ll(a[0], a[1]), B = ll(b[0], b[1]), ang = A.angleTo(B);
    if (ang > 1.9) continue;                                     // near-opposite routes would wrap the planet
    const axis = A.clone().cross(B).normalize(), lift = 0.02 + ang * 0.05;
    const pts = Array.from({ length: 64 }, (_, k) => { const t = k / 63; return A.clone().applyAxisAngle(axis, ang * t).multiplyScalar(1 + lift * Math.sin(Math.PI * t)); });
    arcs.push(pts);
    planet.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), arcs.length === 1 ? arcHot : arcMat));
  }
  const hubPts = new THREE.Points(new THREE.BufferGeometry().setFromPoints(HUBS.map(h => ll(h[0], h[1], 10.05))), new THREE.PointsMaterial({ color: COL.ember, size: 0.55, map: glowTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false }));
  planet.add(hubPts);
  const pulses = points(arcs.length * 2, 0xffd2b3, 0.4, glowTex); pulses.mat.fog = false; planet.add(pulses.obj);
  const starPos = new Float32Array(1500 * 3);
  for (let i = 0; i < 1500; i++) { const r = mulberry(i + 99); const d = v(r() - 0.5, r() - 0.5, r() - 0.5).normalize().multiplyScalar(140 + r() * 60); starPos.set([d.x, d.y, d.z], i * 3); }
  const starGeo = new THREE.BufferGeometry(); starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ color: 0xffffff, size: 0.5, transparent: true, opacity: 0.8, depthWrite: false, fog: false }));
  stars.position.copy(GLOBE); scene.add(stars);

  // ================= life: everything else that is going on, so no frame is empty
  // (all time-driven loops, independent of scroll; counts are lower on phones)
  const lifeFns: ((time: number, p: number) => void)[] = [];
  const rndL = mulberry(4242);
  const loopAt = (pts: THREE.Vector3[], t: number) => pathAt([...pts, pts[0]], ((t % 1) + 1) % 1);

  // a city on the horizon: layered blocks with lit windows, fading into the fog
  {
    const wc = document.createElement('canvas'); wc.width = 64; wc.height = 128;
    const wg = wc.getContext('2d')!;
    wg.fillStyle = '#1a2335'; wg.fillRect(0, 0, 64, 128);
    for (let y = 6; y < 128; y += 10) for (let x = 6; x < 64; x += 12) {
      wg.fillStyle = rndL() > 0.55 ? `rgba(255,${200 + Math.floor(rndL() * 40)},150,${0.5 + rndL() * 0.5})` : 'rgba(40,52,78,1)';
      wg.fillRect(x, y, 6, 5);
    }
    const wtex = new THREE.CanvasTexture(wc); wtex.colorSpace = THREE.SRGBColorSpace; wtex.wrapS = wtex.wrapT = THREE.RepeatWrapping;
    const mat = new THREE.MeshStandardMaterial({ color: 0x9aa6bd, map: wtex, emissive: 0xffffff, emissiveMap: wtex, emissiveIntensity: 0.55, roughness: 0.9 });
    const n = small ? 70 : 140;
    const city = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), mat, n);
    for (let i = 0; i < n; i++) {
      const w = 4 + rndL() * 6, h = 7 + Math.pow(rndL(), 1.6) * 30, d = 4 + rndL() * 5;
      city.setMatrixAt(i, new THREE.Matrix4().compose(v(-40 + (i / n) * 300 + rndL() * 4, h / 2, -50 - rndL() * 24), new THREE.Quaternion(), v(w, h, d)));
    }
    scene.add(city);
  }

  // other orders, always on the move beside ours: the system is busy
  {
    const n = small ? 18 : 34;
    const geo = new RoundedBoxGeometry(0.34, 0.34, 0.34, 2, 0.07);
    const flow = new THREE.InstancedMesh(geo, new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 1.2, roughness: 0.4 }), n);
    const seeds = Array.from({ length: n }, (_, i) => ({ z: (i % 2 ? 1.45 : -1.45) + (rndL() - 0.5) * 0.3, speed: 3 + rndL() * 3.5, off: rndL() * 230, c: new THREE.Color(i % 3 ? COL.ember : COL.go) }));
    seeds.forEach((s, i) => flow.setColorAt(i, s.c));
    scene.add(flow);
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), sc = v(1, 1, 1), at = new THREE.Vector3();
    lifeFns.push(time => {
      seeds.forEach((s, i) => {
        const x = -12 + ((s.off + time * s.speed) % 232);
        const hidden = (x > PX - 3.2 && x < PX + 3) || (x > DX - 1 && x < DX + 9);   // they slip under the packing table and the dock
        at.set(x, hidden ? -2 : 0.3 + Math.abs(Math.sin(time * 3 + i)) * 0.05, s.z);
        q.setFromEuler(new THREE.Euler(0, time * 0.8 + i, 0));
        flow.setMatrixAt(i, m.compose(at, q, sc));
      });
      flow.instanceMatrix.needsUpdate = true;
    });
  }

  // dust in the air, around wherever the camera is looking
  {
    const n = small ? 160 : 360;
    const dust = points(n, 0xfff4e0, 0.07, glowTex);
    dust.mat.opacity = 0.55;
    const seeds = Array.from({ length: n }, () => [rndL() * 30 - 15, rndL() * 9, rndL() * 22 - 11, rndL()]);
    scene.add(dust.obj);
    lifeFns.push(time => {
      seeds.forEach(([x, y, z, s], i) => {
        dust.pos.set([camLook.x + x + Math.sin(time * 0.2 + s * 9) * 0.6, ((y + time * (0.08 + s * 0.12)) % 9) + 0.2, camLook.z + z + Math.cos(time * 0.17 + s * 7) * 0.6], i * 3);
      });
      dust.dirty();
    });
  }

  // 0. other channels the same customer could have used: a laptop and a till, and products in the air
  {
    const laptop = new THREE.Group();
    const base = rbox(2.4, 0.1, 1.6, 0.04, std(0xc9ced8, { metalness: 0.6, roughness: 0.3 })); laptop.add(base);
    const lid = new THREE.Group(); lid.position.set(0, 0.05, -0.8); lid.rotation.x = -1.85; laptop.add(lid);
    const lidM = rbox(2.4, 1.6, 0.06, 0.04, std(0xc9ced8, { metalness: 0.6, roughness: 0.3 })); lidM.position.set(0, 0.8, 0); lid.add(lidM);
    const lscr = panel(2.2, 1.4, 520, (g, w, h) => {
      g.fillStyle = '#f7f8fb'; g.fillRect(0, 0, w, h);
      g.fillStyle = '#232f3e'; g.fillRect(0, 0, w, 46); text(g, 'Marketplace · Seller orders', 20, 24, 22, '#fff', FONT, 700);
      ['#9921 · 1 item · Prime', '#9922 · 3 items', '#9923 · 1 item'].forEach((l, i) => { g.fillStyle = i === 0 ? '#e8f7f0' : '#ffffff'; rr(g, 16, 64 + i * 64, w - 32, 54, 10); g.fill(); text(g, l, 32, 91 + i * 64, 22, '#0b1222', FONT, 600); });
    });
    lscr.mesh.position.set(0, 0.8, 0.04); lid.add(lscr.mesh);
    laptop.position.set(-5.2, 0.45, 2.6); laptop.rotation.y = 0.55;
    const deskA = rbox(3.2, 0.4, 2.2, 0.1, std(0xffffff, { roughness: 0.3 })); deskA.position.set(-5.2, 0.2, 2.6);
    scene.add(deskA, laptop);
    const till = new THREE.Group();
    const tillBase = rbox(1.6, 0.9, 1.1, 0.1, std(0xe9edf4, { roughness: 0.35 })); tillBase.position.y = 0.45; till.add(tillBase);
    const tscr = panel(1.2, 0.85, 360, (g, w, h) => {
      g.fillStyle = '#0e131f'; rr(g, 0, 0, w, h, 18); g.fill();
      text(g, 'Store POS', 20, 34, 26, '#fff', FONT, 700); text(g, 'Sale · 2 items', 20, 84, 24, '#9fe9c8', MONO, 600); text(g, '$89.00', 20, 130, 34, '#fff', FONT, 800);
    });
    tscr.mesh.position.set(0, 1.35, 0.1); tscr.mesh.rotation.x = -0.35; till.add(tscr.mesh);
    till.position.set(5.6, 0, 2.4); till.rotation.y = -0.5; scene.add(till);
    const prodCols = [COL.signal, COL.ember, COL.go, 0xd2a86f, 0xf4f6fa, COL.warn];
    const prods = prodCols.map((c, i) => { const m = rbox(0.5 + (i % 3) * 0.12, 0.45, 0.42, 0.08, std(c, { roughness: 0.35 })); scene.add(m); return m; });
    lifeFns.push((time, p) => {
      const on = p < CHAPTERS[1].from;
      prods.forEach((m, i) => {
        m.visible = on;
        const a = time * 0.35 + (i / prods.length) * Math.PI * 2;
        m.position.set(Math.cos(a) * 3.6, 5.6 + Math.sin(time * 0.9 + i) * 0.35 + (i % 2) * 0.6, Math.sin(a) * 2.4 - 0.5);
        m.rotation.set(time * 0.4 + i, time * 0.6 + i, 0);
      });
    });
  }

  // 1. Nexus: a live order feed, more racks behind the ERP, shelves behind the stores, and screens in orbit
  {
    const feed = panel(5.2, 3.9, 780, () => { });
    feed.mesh.position.set(NX + 7.2, 6.4, -4.4); scene.add(feed.mesh);
    const SRC = ['Shopify', 'Amazon', 'Store POS', 'WooCommerce', 'B2B portal', 'BigCommerce', 'Walmart', 'EDI'];
    const LOCN = ['East', 'New York', 'Boston', 'Dallas'];
    let feedTick = -1;
    const paintFeed = (k: number) => feed.repaint((g, w, h) => {
      card(g, w, h, 'rgba(255,138,76,.75)');
      text(g, 'Orders arriving now', 40, 58, 38, '#fff', FONT, 750);
      for (let i = 0; i < 6; i++) {
        const id = 4900 + k - i, s = SRC[(k - i + 80) % SRC.length], l = LOCN[(k * 3 - i + 40) % LOCN.length];
        const y = 120 + i * 78;
        g.globalAlpha = 1 - i * 0.13;
        g.fillStyle = i === 0 ? 'rgba(255,138,76,.18)' : 'rgba(255,255,255,.05)'; rr(g, 32, y - 30, w - 64, 62, 12); g.fill();
        text(g, `#${id}`, 52, y, 28, '#fff', MONO, 700);
        text(g, s, 200, y, 26, '#c7d0e0', FONT, 600);
        text(g, `→ ${l}`, w - 52, y, 26, '#3ddc97', FONT, 600, 'right');
        g.globalAlpha = 1;
      }
    });
    const racksBack: THREE.MeshStandardMaterial[] = [];
    for (let i = 0; i < 4; i++) {
      const r = rbox(1.8, 4.2, 1.4, 0.08, std(0x1a2236, { roughness: 0.3, metalness: 0.4 })); r.position.set(NX - 15 + i * 2.2, 2.1, -10.5); scene.add(r);
      for (let j = 0; j < 7; j++) { const lm = glow(j % 2 ? COL.signal : COL.go, 2); racksBack.push(lm); const led = box(1.3, 0.05, 0.02, lm, false); led.position.set(NX - 15 + i * 2.2, 0.9 + j * 0.45, -9.78); scene.add(led); }
    }
    for (let i = 0; i < 2; i++) {
      const shelf = new THREE.Group(); shelf.position.set(NX + 12 + i * 3.4, 0, -10.2);
      for (let s = 0; s < 3; s++) {
        const b = box(3, 0.06, 1, std(0xd5dbe6), false); b.position.y = 0.6 + s * 1.1; shelf.add(b);
        for (let k = 0; k < 4; k++) { const pr = rbox(0.5, 0.6, 0.5, 0.06, std([COL.signal, COL.ember, COL.go, 0xd2a86f][(k + s + i) % 4], { roughness: 0.4 })); pr.position.set(-1.1 + k * 0.72, 0.95 + s * 1.1, 0); shelf.add(pr); }
      }
      for (const x of [-1.5, 1.5]) { const u = box(0.08, 3.6, 1, std(0x9aa3b4), false); u.position.set(x, 1.8, 0); shelf.add(u); }
      scene.add(shelf);
    }
    const orbiters = Array.from({ length: 5 }, (_, i) => {
      const o = panel(1.5, 0.9, 260, (g, w, h) => {
        card(g, w, h, 'rgba(91,140,255,.8)');
        text(g, ['Stock', 'Routes', 'Holds', 'Returns', 'Rules'][i], 22, 34, 30, '#fff', FONT, 700);
        g.fillStyle = 'rgba(91,140,255,.8)';
        for (let b = 0; b < 6; b++) { const bh = 14 + ((i * 7 + b * 13) % 40); g.fillRect(24 + b * 34, h - 20 - bh, 20, bh); }
      });
      scene.add(o.mesh); return o;
    });
    lifeFns.push((time, p) => {
      const on = p > CHAPTERS[1].from - 0.02 && p < CHAPTERS[3].from;
      feed.mesh.visible = on;
      orbiters.forEach(o => (o.mesh.visible = on));
      if (!on) return;
      const k = Math.floor(time / 0.8);
      if (k !== feedTick) { paintFeed(k); feedTick = k; }
      feed.mesh.lookAt(camera.position);
      orbiters.forEach((o, i) => {
        const a = time * 0.25 + (i / orbiters.length) * Math.PI * 2;
        o.mesh.position.set(NX + Math.cos(a) * 5.2, 4.8 + Math.sin(time * 0.7 + i) * 0.3, Math.sin(a) * 5.2);
        o.mesh.lookAt(camera.position);
      });
      racksBack.forEach((m, i) => (m.emissiveIntensity = 0.6 + (Math.sin(time * 6 + i * 1.7) > 0.3 ? 2 : 0)));
    });
  }

  // 2. two more lanes through the approval gates, with other orders passing
  {
    const side = [-7.5, 7.5].map(z => {
      const g = new THREE.Group(); g.position.set(GX, 0, z); scene.add(g);
      for (const dz of [-1.6, 1.6]) { const pl = rbox(0.5, 3.4, 0.5, 0.1, std(0x18213a, { roughness: 0.3, metalness: 0.4 })); pl.position.set(0, 1.7, dz); g.add(pl); }
      const top = rbox(0.6, 0.45, 3.8, 0.1, std(0x18213a, { roughness: 0.3, metalness: 0.4 })); top.position.y = 3.5; g.add(top);
      const lm = glow(COL.go, 2.4); const strip = box(0.05, 0.05, 3, lm, false); strip.position.set(0.32, 3.25, 0); g.add(strip);
      const lane = box(18, 0.02, 0.3, glow(COL.signal, 0.8), false); lane.position.set(0, 0.03, 0); g.add(lane);
      const toks = Array.from({ length: 3 }, (_, i) => { const t = rbox(0.6, 0.6, 0.6, 0.14, glow(i % 2 ? COL.ember : COL.go, 1.4)); g.add(t); return t; });
      return { g, toks, lm };
    });
    lifeFns.push(time => {
      side.forEach((s, j) => s.toks.forEach((t, i) => {
        const x = -9 + ((time * 2.4 + i * 6 + j * 3) % 18);
        t.position.set(x, 1.1 + Math.sin(time * 2 + i) * 0.08, 0);
        t.rotation.set(time * 0.6 + i, time * 0.8, 0);
        const through = x > 0;
        (t.material as THREE.MeshStandardMaterial).emissive.setHex(through ? COL.go : COL.ember);
        (t.material as THREE.MeshStandardMaterial).color.setHex(through ? COL.go : COL.ember);
      }));
    });
  }

  // 3. a staging area of pallets, and a second forklift at work
  {
    const pal = () => {
      const g = new THREE.Group();
      const d = box(2, 0.2, 1.6, std(COL.wood, { roughness: 0.8 })); d.position.y = 0.1; g.add(d);
      for (let k = 0; k < 4; k++) { const c = rbox(0.9, 0.7, 0.75, 0.05, std(k % 2 ? COL.card : COL.cardDark, { roughness: 0.8 })); c.position.set(k % 2 ? 0.48 : -0.48, 0.55 + Math.floor(k / 2) * 0.72, 0); g.add(c); }
      return g;
    };
    for (let i = 0; i < 8; i++) { const p = pal(); p.position.set(SX + 13 + (i % 4) * 2.6, 0, -9.5 - Math.floor(i / 4) * 2.4); p.rotation.y = (rndL() - 0.5) * 0.08; scene.add(p); }
    const f2 = forklift(); const load = pal(); load.position.set(-2.2, 0.55, 0); f2.g.add(load);
    f2.g.scale.setScalar(0.9); scene.add(f2.g);
    const route = [v(SX + 21, 0, -14.2), v(SX + 11, 0, -14.2), v(SX + 11, 0, -15.2), v(SX + 21, 0, -15.2)];   // stays clear of the racks
    lifeFns.push(time => {
      const w = loopAt(route, time * 0.035);
      f2.g.position.copy(w.pos); f2.g.rotation.y = w.heading + Math.PI / 2;
      f2.lift(0.5 + Math.sin(time * 0.4) * 0.1);
      f2.beacon.visible = Math.sin(time * 8 + 1) > 0;
    });
  }

  // 4–5. a fuller warehouse: another rack row, other pickers on their own routes, a reach truck
  {
    racks(scene, [{ x0: WX + 2, z: -14.4, bays: 8 }]);
    const pickers = [0, 1, 2].map(i => {
      const pp = person([COL.ember, COL.go, COL.signal][i], [COL.signal, COL.ink, COL.ember][i]);
      const c = box(0.9, 0.5, 0.6, std(COL.signal, { roughness: 0.45 })); c.position.set(0.95, 0.75, 0);
      const rig = new THREE.Group(); pp.g.rotation.y = Math.PI / 2; rig.add(pp.g, c); scene.add(rig);
      return { pp, rig };
    });
    const routes = [
      [v(WX + 3, 0, -7.2), v(WX + 20, 0, -7.2), v(WX + 20, 0, -7.6), v(WX + 3, 0, -7.6)],
      [v(WX + 6, 0, -12), v(WX + 21, 0, -12), v(WX + 21, 0, -11.6), v(WX + 6, 0, -11.6)],
      [v(WX + 22, 0, 7.4), v(WX + 4, 0, 7.4), v(WX + 4, 0, 7.8), v(WX + 22, 0, 7.8)],
    ];
    const reach = forklift(); reach.g.scale.setScalar(0.85); scene.add(reach.g);
    const reachRoute = [v(WX + 24, 0, -16.8), v(WX + 2, 0, -16.8)];
    lifeFns.push(time => {
      pickers.forEach((k, i) => {
        const w = loopAt(routes[i], time * (0.028 + i * 0.006) + i * 0.3);
        k.rig.position.copy(w.pos); k.rig.rotation.y = w.heading - Math.PI / 2;
        k.pp.walk(time * 5 + i, 1);
      });
      const tt = (Math.sin(time * 0.12) + 1) / 2;
      reach.g.position.copy(reachRoute[0]).lerp(reachRoute[1], tt);
      reach.g.rotation.y = Math.cos(time * 0.12) > 0 ? Math.PI : 0;
      reach.lift(1.2 + Math.sin(time * 0.5) * 1.1);
      reach.beacon.visible = Math.sin(time * 8 + 2) > 0;
    });
  }

  // 6. more packing stations, and parcels on the belt to the dock
  {
    const stations = [-4.6, -9].map(z => {
      const g = new THREE.Group(); g.position.set(PX - 1, 0, z); scene.add(g);
      const t = rbox(3.6, 0.14, 1.8, 0.05, std(0xf6f7fa, { roughness: 0.35 })); t.position.y = 1; g.add(t);
      for (const [x, dz] of [[1.6, 0.75], [-1.6, 0.75], [1.6, -0.75], [-1.6, -0.75]]) { const l = box(0.08, 1, 0.08, std(COL.steel)); l.position.set(x, 0.5, dz); g.add(l); }
      const pk = parcel(1.0, 0.7, 0.8); pk.g.position.set(0.3, 1.07, 0); pk.close(0.4); g.add(pk.g);
      const stack = new THREE.Group(); for (let k = 0; k < 5; k++) { const f = box(1.2, 0.05, 0.9, std(COL.cardDark, { roughness: 0.9 })); f.position.y = k * 0.06; stack.add(f); }
      stack.position.set(-1.2, 1.08, 0); g.add(stack);
      const man = person(COL.go, COL.ink); man.g.position.set(0, 0, -1.3); g.add(man.g);
      return { man, pk };
    });
    // finished parcels waiting for the truck, and a roller cage of boxes behind the table
    const done = new THREE.Group(); done.position.set(PX + 4.6, 0, -2.8); scene.add(done);
    const dp = box(2, 0.2, 1.6, std(COL.wood, { roughness: 0.8 })); dp.position.y = 0.1; done.add(dp);
    for (let k = 0; k < 9; k++) { const c = rbox(0.6, 0.45, 0.5, 0.05, std(k % 3 ? COL.card : COL.cardDark, { roughness: 0.8 })); c.position.set(-0.65 + (k % 3) * 0.65, 0.45 + Math.floor(k / 3) * 0.47, (k % 2 ? 0.25 : -0.25)); done.add(c); }
    const lbl = panel(1.6, 0.5, 300, (g, w, h) => { card(g, w, h, 'rgba(61,220,151,.9)'); text(g, 'Ready · Dock 3', w / 2, h / 2, 34, '#fff', MONO, 700, 'center'); });
    lbl.mesh.position.set(0, 2.2, 0); done.add(lbl.mesh);
    const cage = new THREE.Group(); cage.position.set(PX - 4.2, 0, -2.6); scene.add(cage);
    for (const [x, z] of [[-0.6, -0.45], [0.6, -0.45], [-0.6, 0.45], [0.6, 0.45]]) { const post = box(0.05, 1.8, 0.05, std(COL.steel, { metalness: 0.6 })); post.position.set(x, 0.95, z); cage.add(post); }
    for (const y of [0.15, 0.9, 1.75]) { const sh = box(1.25, 0.04, 0.95, std(COL.steel, { metalness: 0.6 }), false); sh.position.y = y; cage.add(sh); }
    for (let k = 0; k < 6; k++) { const c = rbox(0.5, 0.55, 0.4, 0.05, std(k % 2 ? COL.card : 0xf4f6fa, { roughness: 0.7 })); c.position.set(-0.28 + (k % 2) * 0.56, 0.45 + Math.floor(k / 2) * 0.62, 0); cage.add(c); }
    lifeFns.push(() => lbl.mesh.lookAt(camera.position));
    const beltN = small ? 5 : 9;
    const beltBoxes = Array.from({ length: beltN }, (_, i) => { const b = rbox(0.8 - (i % 3) * 0.1, 0.55, 0.7, 0.05, std(i % 2 ? COL.card : COL.cardDark, { roughness: 0.8 })); scene.add(b); return b; });
    const B0 = PX + 4, B1 = DX - 3.4;   // the visible run of the belt
    lifeFns.push(time => {
      stations.forEach((s, i) => { s.man.reach(Math.abs(Math.sin(time * 3 + i))) ; s.pk.close((Math.sin(time * 0.6 + i * 2) + 1) / 2); });
      beltBoxes.forEach((b, i) => { const x = B0 + ((time * 1.6 + i * ((B1 - B0) / beltN)) % (B1 - B0)); b.position.set(x, 1.3, (i % 2 ? 0.15 : -0.15)); });
    });
  }

  // 7. more doors at the dock, trailers waiting, a yard truck shuttling
  {
    const wallMat2 = std(0x2a3450, { roughness: 0.8 });
    for (const z of [-12.4, 12.4]) {
      for (const dz of [-3.1, 3.1]) { const w = box(0.6, 4.6, 0.6, wallMat2); w.position.set(DX, 2.3, z + dz); scene.add(w); }
      const head = box(0.6, 0.5, 6.4, wallMat2); head.position.set(DX, 4.35, z); scene.add(head);
      const door = box(0.15, 4.0, 5.6, std(0x3a4566)); door.position.set(DX + 0.1, 2.1, z); scene.add(door);
      const lamp = box(0.12, 0.18, 1.4, glow(z < 0 ? COL.stop : COL.go, 3), false); lamp.position.set(DX + 0.45, 4.7, z); scene.add(lamp);
      const t = truck(); t.g.position.set(DX + 4.75, 0, z); t.lights(0); scene.add(t.g);
    }
    for (const z of [-9.3, 9.3, -15.5, 15.5]) { const w = box(0.6, 4.6, 0.6, wallMat2); w.position.set(DX, 2.3, z); scene.add(w); }
    const yard = truck(); yard.g.scale.setScalar(0.8); scene.add(yard.g);
    lifeFns.push(time => {
      const tt = (time * 0.05) % 1, x = DX + 18 + Math.sin(tt * Math.PI * 2) * 12;
      yard.g.position.set(x, 0, -20);
      yard.g.rotation.y = Math.cos(tt * Math.PI * 2) > 0 ? 0 : Math.PI;
      yard.roll(x); yard.lights(1);
    });
  }

  // ------------------------------------------------------------------ camera: shots set at moments within chapters

  type Shot = { p: number; pos: () => THREE.Vector3; look: () => THREE.Vector3 };
  const follow = (o: THREE.Object3D, off: THREE.Vector3, lookOff = v(0, 1.2, 0)) => ({ pos: () => o.position.clone().add(off), look: () => o.position.clone().add(lookOff) });
  const screenPoint = (d: number) => ({
    pos: () => phone.localToWorld(v(0, 0.1, 0.14)).add(v(0, 0, 1).applyQuaternion(phone.quaternion).multiplyScalar(d)),
    look: () => phone.localToWorld(v(0, 0.1, 0.14)),
  });
  const SHOTS: Shot[] = [
    { p: at(0, 0), pos: () => v(11, 7, 17), look: () => v(0, 3, 0) },
    { p: at(0, 0.35), pos: () => v(7, 5.2, 12.5), look: () => v(0, 3.2, 0) },
    { p: at(0, 0.62), ...screenPoint(5.2) },
    { p: at(0, 0.97), ...screenPoint(0.35) },                                  // through the screen…
    { p: at(1, 0.1), pos: () => v(NX + 6, 16, 26), look: () => v(NX, 3, -1) }, // …and out inside Nexus
    { p: at(1, 0.55), pos: () => v(NX + 4, 8.5, 17), look: () => v(NX, 4.2, -2.5) },
    { p: at(1, 0.95), pos: () => v(NX + 7, 6.5, 13), look: () => v(NX + 4, 3, -1) },
    { p: at(2, 0.25), pos: () => v(GX - 8, 6.5, 13), look: () => v(GX, 2.6, -1.2) },
    { p: at(2, 0.9), pos: () => v(GX + 4, 5.5, 12), look: () => v(GX - 0.5, 2.6, -1.5) },
    { p: at(3, 0.18), pos: () => v(SX - 6, 15, 20), look: () => v(SX + 6, 1, -1) },
    { p: at(3, 0.5), pos: () => v(SX + 5, 6.5, 9), look: () => v(SX + 7, 2.4, -6) },
    { p: at(3, 0.8), pos: () => v(SX + 1, 5, 6), look: () => v(SX + 5, 1.4, -4.4) },
    { p: at(3, 1), pos: () => v(SX + 4, 7, 9), look: () => v(SX + 9, 1.6, -4) },
    { p: at(4, 0.2), pos: () => v(WX - 4, 14, 17), look: () => v(WX + 9, 1.5, -1) },
    { p: at(4, 0.85), pos: () => v(WX + 2, 7.5, 14), look: () => v(WX + 6, 3.8, -0.5) },
    { p: at(5, 0.12), ...follow(pickerRig, v(-5, 5.2, 7.5)) },
    { p: at(5, 0.9), ...follow(pickerRig, v(-4, 6, 8)) },
    { p: at(6, 0.12), pos: () => v(PX - 3, 5.8, 8), look: () => v(PX, 1.8, 0) },
    { p: at(6, 0.7), pos: () => v(PX + 1.6, 4.4, 5), look: () => v(PX, 1.6, 0) },
    { p: at(6, 1), pos: () => v(PX + 6, 6, 9), look: () => v(PX + 6, 1.4, 0) },
    { p: at(7, 0.18), pos: () => v(DX + 15, 9.5, 16), look: () => v(DX + 1, 1.6, 0) },
    { p: at(7, 0.62), pos: () => v(DX + 12, 7.5, 13), look: () => v(DX + 1.5, 1.8, 0) },
    { p: at(7, 0.82), ...follow(van.g, v(-4, 6.5, 13), v(2, 1.5, 0)) },
    { p: at(7, 1), ...follow(van.g, v(-8, 40, 12), v(10, 0, 0)) },
    { p: at(8, 0.04), pos: () => GLOBE.clone().add(v(0, 8, 80)), look: () => GLOBE.clone() },
    { p: at(8, 0.55), pos: () => GLOBE.clone().add(v(-10, 9, 50)), look: () => GLOBE.clone().add(v(-8, 0, 0)) },
    { p: 1, pos: () => GLOBE.clone().add(v(-12, 12, 44)), look: () => GLOBE.clone().add(v(-9, 0, 0)) },
  ];
  const camPos = new THREE.Vector3(), camLook = new THREE.Vector3();
  let pullBack = 1;
  const diving = (p: number) => p > at(0, 0.5) && p < at(1, 0.12);
  const shotAt = (p: number) => {
    let i = 0;
    while (i < SHOTS.length - 2 && p > SHOTS[i + 1].p) i++;
    const a = SHOTS[i], b = SHOTS[i + 1];
    const t = ease(seg(p, a.p, b.p));
    camPos.copy(a.pos()).lerp(b.pos(), t); camLook.copy(a.look()).lerp(b.look(), t);
    if (pullBack !== 1 && !diving(p)) camPos.sub(camLook).multiplyScalar(pullBack).add(camLook);
  };

  // ------------------------------------------------------------------ post-processing

  const composer = new EffectComposer(renderer);
  composer.addPass(new RenderPass(scene, camera));
  let gtao: GTAOPass | null = null;
  if (hi) {
    gtao = new GTAOPass(scene, camera, 1, 1);
    gtao.output = GTAOPass.OUTPUT.Default;
    gtao.blendIntensity = 0.85;
    gtao.updateGtaoMaterial({ radius: 0.6, distanceExponent: 1.5, thickness: 1.2, scale: 1 });
    composer.addPass(gtao);
  }
  const bloom = new UnrealBloomPass(new THREE.Vector2(256, 256), 0.6, 0.55, 0.82);
  composer.addPass(bloom);
  composer.addPass(new OutputPass());
  if (!small) composer.addPass(new SMAAPass());
  const finish = new ShaderPass({
    uniforms: { tDiffuse: { value: null }, uTime: { value: 0 }, uVignette: { value: 0.35 }, uGrain: { value: 0.035 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: FINISH,
  });
  composer.addPass(finish);

  // ------------------------------------------------------------------ state at progress p

  const TOKEN_IN = new THREE.CatmullRomCurve3([v(NX, 14, 2), v(NX + 1, 9, 1), v(NX, 3.1, 0)]);
  const TOKEN_GATE = new THREE.CatmullRomCurve3([v(NX, 3.1, 0), v(NX + 6, 2.4, 0.4), v(GX - 3, 1.9, 0), v(GX + 4, 1.9, 0)]);
  const TOKEN_B2C = new THREE.CatmullRomCurve3([v(GX + 4, 1.9, 0), v(SX - 2, 1.4, 1.8), v(SX + 8, 1.2, 3.4), v(WX - 2, 1.2, 1)]);
  const PACK_AT = v(PX, 1.08, 0.1);
  const CONV_START = PACK_AT.clone().setY(1.02).add(v(2.4, 0, -0.1));
  const CONV_END = v(DX - 0.6, 1.02, 0);
  const TRUCK_DOCK = v(DX + 4.6, 0, 0);
  const PAL = v(SX + 3, 0, -4.4);
  const tmp = new THREE.Vector3();
  let lastP = -1;

  const update = (p: number, time: number) => {
    const e = envAt(p);
    (scene.background as THREE.Color).copy(e.bg);
    fog.color.copy(e.bg); fog.near = e.fogNear; fog.far = e.fogFar;
    hemi.color.copy(e.sky); hemi.groundColor.copy(e.ground); hemi.intensity = e.hemi;
    sun.color.copy(e.sun); sun.intensity = e.sunI;
    floorMat.color.copy(e.floor); gridMat.color.copy(e.grid);
    renderer.toneMappingExposure = e.exposure;
    bloom.strength = e.bloom; bloom.threshold = e.bloomT;
    const ci = chapterIdx(p);
    words.forEach((w, i) => { w.mat.color.copy(e.words); w.mat.opacity = e.wordsA * (i === Math.min(7, ci) ? 1 : 0.3); });

    const routeX = lerp(-6, DX + 2, clamp(p / 0.86));
    routeLit.scale.x = Math.max(0.001, routeX + 6); routeLit.position.x = -6 + (routeX + 6) / 2;

    // 0. phone, tiles, burst
    const c0 = L(p, 0);
    const placed = c0 > 0.42 ? 1 : 0;
    if (placed !== screenState) { paintScreen(placed); screenState = placed; }
    phone.rotation.y = 0.35 + Math.sin(time * 0.4) * 0.035;
    phone.position.y = 3.2 + Math.sin(time * 0.8) * 0.05;
    screenLight.intensity = 6 * seg(c0, 0.6, 1);
    const ringT = time * 0.1 + p * 3;
    const tilesOn = p < CHAPTERS[1].from;
    tiles.forEach((t, i) => {
      t.visible = tilesOn;
      if (!tilesOn) return;
      const a = (i / tiles.length) * Math.PI * 2 + ringT, r = small ? 5.4 : 6.4;
      t.position.set(Math.cos(a) * r, 3.0 + Math.sin(a * 2 + time * 0.6) * 0.35 + (i % 2) * 1.0 - 0.5, Math.sin(a) * r * 0.62);
      t.lookAt(camera.position);
      t.scale.setScalar(i === 0 && c0 > 0.3 && c0 < 0.6 ? 1.15 : 1);
    });
    const b0 = seg(c0, 0.42, 0.62);
    burst.mat.opacity = b0 > 0 && b0 < 1 ? 1 - b0 : 0;
    for (let i = 0; i < burstN; i++) { tmp.copy(burstDirs[i]).multiplyScalar(easeOut(b0)); burst.pos.set([tmp.x, 3.5 + tmp.y - b0 * b0 * 1.5, tmp.z + 0.4], i * 3); }
    burst.dirty();

    // the order token
    const c1 = L(p, 1), c2 = L(p, 2), c3 = L(p, 3);
    let tp: THREE.Vector3 | null = null;
    if (ci === 1) tp = TOKEN_IN.getPoint(ease(seg(c1, 0.12, 0.4)));
    else if (ci === 2) tp = TOKEN_GATE.getPoint(ease(seg(c2, 0.05, 0.85)));
    else if (ci === 3) tp = TOKEN_B2C.getPoint(ease(seg(c3, 0.05, 0.5)));
    token.visible = !!tp;
    if (tp) { token.position.copy(tp); if (tp.y < 13) token.position.y += Math.sin(time * 2.2) * 0.06; }
    tokenCube.rotation.set(time * 0.6, time * 0.8, 0);
    const approved = p > at(2, 0.72);
    const tc = approved ? COL.go : COL.ember;
    tokenMat.color.setHex(tc); tokenMat.emissive.setHex(tc); tokenLight.color.setHex(tc); trail.mat.color.setHex(tc);
    tokenTag.mesh.lookAt(camera.position);
    if (Math.abs(p - lastP) > 0.0004) { trailPts.pop(); trailPts.unshift(token.visible ? token.position.clone() : v(0, -60, 0)); }
    trailPts.forEach((q, i) => trail.pos.set([q.x, q.y, q.z], i * 3));
    trail.dirty();
    trail.mat.opacity = token.visible ? 0.7 : 0;

    // 1. Nexus
    const inNexus = p > CHAPTERS[1].from - 0.01 && p < CHAPTERS[3].from;
    brain.rotation.y = time * 0.25; brain.rotation.x = time * 0.11; shell.rotation.y = -time * 0.15; shell.rotation.z = time * 0.07;
    rings.forEach((r, i) => (r.rotation.z = time * (0.5 - i * 0.22)));
    brainMat.emissiveIntensity = 1.2 + Math.sin(time * 2.2) * 0.3 + seg(c1, 0.3, 0.5) * 1.2;
    coreLight.intensity = inNexus ? 26 : 0;
    const hexOn = seg(c1, 0.1, 0.4) * (inNexus ? 1 : 0);
    for (let i = 0; i < hexN; i++) {
      const w = Math.max(0, Math.sin(time * 1.6 - hexR[i] * 0.9 + i * 0.2));
      hexes.setColorAt(i, hc.copy(hcOff).lerp(hcOn, w * 0.8 * hexOn));
    }
    hexes.instanceColor!.needsUpdate = true;
    vort.mat.opacity = inNexus ? 0.9 * seg(c1, 0.05, 0.3) : 0;
    if (vort.mat.opacity > 0) {
      for (let i = 0; i < vortN; i++) {
        const [a0, r, h, sp] = vortSeed[i], a = a0 + time * sp;
        vort.pos.set([Math.cos(a) * r, 0.6 + ((h + time * sp * 0.6) % 5.5), Math.sin(a) * r], i * 3);
      }
      vort.dirty();
    }
    beamMat.uniforms.uTime.value = time;
    beamMat.uniforms.uOn.value = seg(c1, 0.3, 0.45) * (1 - seg(p, at(2, 0.4), at(2, 0.6)));
    const sk = Math.round(seg(c1, 0.4, 0.95) * 20) / 20;
    if (sk !== stockStep) { paintStock(sk); stockStep = sk; }
    stock.mat.opacity = seg(c1, 0.35, 0.45) * (1 - seg(p, at(2, 0.05), at(2, 0.2)));
    stock.mesh.visible = stock.mat.opacity > 0.01; stock.mesh.lookAt(camera.position);
    [coreTag, erpTag, posTag].forEach(t => t.mesh.lookAt(camera.position));

    // 2. approval
    const scan = seg(c2, 0.4, 0.72);
    scanPlane.position.y = 0.4 + (0.5 + 0.5 * Math.sin(time * 3.2)) * 3.6;
    scanMat.color.setHex(approved ? COL.go : COL.signal);
    scanMat.opacity = p > at(2, 0.3) && p < at(3, 0.2) ? 0.55 : 0.12;
    gateLightMat.emissive.setHex(approved ? COL.go : COL.signal); gateLightMat.color.setHex(approved ? COL.go : COL.signal);
    const nChecks = Math.floor(scan * 4.999), cs = nChecks * 2 + (approved ? 1 : 0);
    if (cs !== checksStep) { paintChecks(nChecks, approved && nChecks >= 4); checksStep = cs; }
    checks.mat.opacity = seg(c2, 0.15, 0.3) * (1 - seg(p, at(3, 0.15), at(3, 0.3)));
    checks.mesh.visible = checks.mat.opacity > 0.01; checks.mesh.lookAt(camera.position);

    // 3. ship date; at release a forklift lifts the pallet away
    const calK = seg(c3, 0.35, 0.68), ck = Math.min(4, Math.floor(calK * 5));
    if (ck !== calStep) { paintCal(calK); calStep = ck; }
    cal.mat.opacity = seg(c3, 0.2, 0.3) * (1 - seg(p, at(4, 0.1), at(4, 0.3)));
    cal.mesh.visible = cal.mat.opacity > 0.01; cal.mesh.lookAt(camera.position);
    [signC, signB].forEach(s => s.mesh.lookAt(camera.position));
    const approach = ease(seg(c3, 0.68, 0.8)), raise = ease(seg(c3, 0.8, 0.86)), away = ease(seg(c3, 0.86, 1)) + (p >= CHAPTERS[4].from ? 1 : 0);
    const forkX = lerp(SX + 13, PAL.x + 2.2, approach) + away * 18;
    lift.g.position.set(forkX, 0, PAL.z);
    lift.lift(raise * 0.55);
    lift.beacon.visible = Math.sin(time * 8) > 0;
    pallet.position.set(raise > 0 ? forkX - 2.2 : PAL.x, raise * 0.55, PAL.z);
    palletTag.mesh.lookAt(camera.position);

    // 4. wave
    const c4 = L(p, 4);
    const wk = Math.round(seg(c4, 0.25, 0.85) * 16) / 16;
    if (wk !== waveStep) { paintWave(wk); waveStep = wk; }
    wave.mat.opacity = seg(c4, 0.15, 0.3) * (1 - seg(p, at(5, 0.12), at(5, 0.25)));
    wave.mesh.visible = wave.mat.opacity > 0.01; wave.mesh.lookAt(camera.position);

    // 5. picking
    const wt = seg(L(p, 5), 0.04, 0.96);
    const ph = pause(wt);
    const wp = pathAt(PICK_PATH, ph.t);
    pickerRig.position.copy(wp.pos);
    pickerRig.rotation.y = wt >= 1 ? 0 : wp.heading - Math.PI / 2;
    picker.walk(wp.dist * 2.3, wt > 0 && wt < 1 && ph.picking < 0 ? 1 : 0);
    picker.reach(ph.picking >= 0 ? Math.sin(Math.PI * clamp(ph.picking)) : 0);
    pickerTag.mesh.lookAt(camera.position);
    const pickedA = wt > A1 + HOLD * 0.5, pickedB = wt > B1 + HOLD * 0.5;
    const showBins = p > at(4, 0.3) && p < CHAPTERS[6].from;
    markA.ring.visible = markA.tag.mesh.visible = showBins && !pickedA;
    markB.ring.visible = markB.tag.mesh.visible = showBins && !pickedB;
    markA.ring.scale.setScalar(1 + Math.sin(time * 5) * 0.08); markB.ring.scale.setScalar(1 + Math.sin(time * 5 + 1) * 0.08);
    [markA.tag.mesh, markB.tag.mesh].forEach(m => m.lookAt(camera.position));
    const scanning = ph.picking >= 0 && ph.picking < 0.5;
    scanMatR.opacity = scanning ? 0.9 : 0;
    if (scanning) {
      pickerRig.updateMatrixWorld(true);
      const hand = picker.g.localToWorld(v(0.4, 1.4, 0.3)), bin = ph.t === STOP_A ? BIN_A : BIN_B;
      scanBeam.position.copy(hand).lerp(bin, 0.5); scanBeam.scale.y = hand.distanceTo(bin);
      scanBeam.quaternion.setFromUnitVectors(v(0, 1, 0), bin.clone().sub(hand).normalize());
    }
    const toteWorld = tote.getWorldPosition(new THREE.Vector3());

    // items: shelf → tote → table → box
    const c6 = L(p, 6);
    const place = (item: THREE.Mesh, bin: THREE.Vector3, picked: boolean, pickT: number, slot: number) => {
      if (p < CHAPTERS[6].from) {
        if (!picked) item.position.copy(bin).lerp(toteWorld.clone().add(v(0, 0.35 + slot * 0.3, 0)), pickT > 0 && ph.picking >= 0 ? ease(clamp(pickT)) : 0);
        else item.position.copy(toteWorld).add(v(slot * 0.2 - 0.1, 0.32 + slot * 0.32, 0));
      } else {
        const onTable = PACK_AT.clone().add(v(-1.6 + slot * 0.7, 0.22, 0.6)), inBox = PACK_AT.clone().add(v(slot * 0.3 - 0.15, 0.22 + slot * 0.35, 0));
        const arrive = ease(seg(c6, 0, 0.12)), packIn = seg(c6, 0.42, 0.62);
        if (packIn <= 0) item.position.copy(toteWorld.clone().add(v(0, 0.35 + slot * 0.3, 0))).lerp(onTable, arrive);
        else item.position.copy(onTable).lerp(inBox, ease(clamp(packIn * 2 - slot)));
      }
      item.visible = p < at(6, 0.7);
    };
    place(itemA, BIN_A, pickedA, (wt - A1) / HOLD, 0);
    place(itemB, BIN_B, pickedB, (wt - B1) / HOLD, 1);

    // 6. packing
    const mk = Math.round(seg(c6, 0.12, 0.85) * 10) / 10;
    if (mk !== monStep) { paintMonitor(mk); monStep = mk; }
    monitor.mat.opacity = seg(c6, 0, 0.1);
    monitor.mesh.visible = monitor.mat.opacity > 0.01;
    ghostMat.opacity = seg(c6, 0.18, 0.28) * (1 - seg(c6, 0.36, 0.42));
    const boxIn = ease(seg(c6, 0.26, 0.4)), close = ease(seg(c6, 0.66, 0.76)), labelOn = ease(seg(c6, 0.82, 0.92));
    const c7 = L(p, 7);
    const ride = ease(seg(c7, 0.08, 0.5)), loadIn = ease(seg(c7, 0.5, 0.62));
    const inTruck = TRUCK_DOCK.clone().add(v(-2.6, 0.62, 0.4));
    const pkgPos = PACK_AT.clone().add(v(0, (1 - boxIn) * 2.4, 0));
    if (p > CHAPTERS[7].from) pkgPos.copy(PACK_AT).lerp(CONV_START, ease(seg(c7, 0, 0.08))).lerp(CONV_END, ride).lerp(inTruck, loadIn);
    pkg.g.position.copy(pkgPos);
    pkg.g.visible = p > at(6, 0.24) && p < at(7, 0.75);
    pkg.g.scale.setScalar(lerp(0.6, 1, boxIn));
    pkg.close(close);
    label.mesh.position.copy(v(PX + 1.65, 1.6, -0.28)).lerp(pkgPos.clone().add(v(0.15, pkg.h + 0.035, 0.05)), labelOn);
    label.mesh.visible = p > at(6, 0.78) && pkg.g.visible;
    labelPaper.visible = c6 > 0.76 && c6 < 0.82;
    printLed.visible = c6 > 0.74 && c6 < 0.84 && Math.sin(time * 18) > 0;
    packer.reach(c6 > 0.4 && c6 < 0.9 ? Math.abs(Math.sin(time * 4)) * 0.6 : 0);

    // 7. dispatch: the truck reverses in, loads, shuts, lights up and drives into the evening
    const backIn = ease(seg(c7, 0.0, 0.3)), doorUp = seg(c7, 0.22, 0.32) * (1 - seg(c7, 0.68, 0.76)), drive = seg(c7, 0.78, 1) + (ci === 8 ? 1 : 0);
    const truckX = lerp(DX + 28, TRUCK_DOCK.x + 0.15, backIn) + Math.pow(drive, 1.7) * 80;
    van.g.position.set(truckX, 0, 0);
    van.open(doorUp); van.roll(truckX); van.lights(seg(c7, 0.7, 0.8));
    const dOpen = seg(c7, 0.15, 0.25) * (1 - seg(c7, 0.76, 0.84));
    dockDoor.scale.y = Math.max(0.04, 1 - dOpen); dockDoor.position.y = 4.1 - 2.0 * dockDoor.scale.y;
    lampHeads.forEach(m => (m.emissiveIntensity = 3 * seg(p, at(6, 0.95), at(7, 0.2))));
    extra.forEach((x, i) => {
      const s = 0.28 + i * 0.06;
      x.position.copy(CONV_START.clone().add(v(1.2 + i * 1.4, 0.35, 0))).lerp(CONV_END.clone().setY(1.37), ease(seg(c7, s - 0.2, s)));
      if (c7 > s) x.position.copy(CONV_END.clone().setY(1.37)).lerp(TRUCK_DOCK.clone().add(v(-3.4 + i * 0.6, 0.86, -0.6 + (i % 2) * 1.1)), ease(seg(c7, s, s + 0.08)));
      x.visible = c7 > 0.05 && c7 < 0.75;
    });

    // 8. everywhere
    const c8 = L(p, 8);
    planet.rotation.set(0.35, 0.6 + time * 0.04 + c8 * 0.8, 0);
    arcs.forEach((pts, i) => {
      for (const k of [0, 1]) {
        const t = (time * (0.12 + (i % 5) * 0.03) + i * 0.17 + k * 0.5) % 1, f = t * (pts.length - 1), i0 = Math.floor(f);
        const q = pts[i0].clone().lerp(pts[Math.min(pts.length - 1, i0 + 1)], f - i0);
        pulses.pos.set([q.x, q.y, q.z], (i * 2 + k) * 3);
      }
    });
    pulses.dirty();

    // camera, the light that follows it, and the finish
    shotAt(p);
    lifeFns.forEach(f => f(time, p));
    camera.position.copy(camPos); camera.lookAt(camLook);
    if (!diving(p)) { camera.position.x += Math.sin(time * 0.6) * 0.06; camera.position.y += Math.sin(time * 0.8 + 1) * 0.05; }
    sun.position.copy(camLook).add(v(-12, 24, 14)); sun.target.position.copy(camLook);
    finish.uniforms.uTime.value = time;
    lastP = p;
  };

  // ------------------------------------------------------------------ size, loop

  const resize = () => {
    const w = host.clientWidth || 1, h = host.clientHeight || 1;
    renderer.setSize(w, h, false);
    composer.setPixelRatio(renderer.getPixelRatio()); composer.setSize(w, h);
    bloom.resolution.set(w / 2, h / 2);
    camera.aspect = w / h;
    camera.fov = w / h < 0.8 ? 58 : w / h < 1.2 ? 46 : 38;
    // on tall screens the captions take the lower half, so the picture is framed higher and every shot steps back
    if (w / h < 0.8) camera.setViewOffset(w, h, 0, h * 0.2, w, h); else camera.clearViewOffset();
    pullBack = w / h < 0.8 ? 1.45 : w / h < 1.2 ? 1.15 : 1;
    camera.updateProjectionMatrix();
  };
  resize();
  const ro = new ResizeObserver(resize); ro.observe(host);

  let target = 0, current = 0, running = false, raf = 0, visible = false;
  const t0 = performance.now();
  const frame = () => {
    current += (target - current) * (opts.reduced || SNAP ? 1 : 0.12);
    if (Math.abs(target - current) < 0.00005) current = target;
    update(current, (performance.now() - t0) / 1000);
    composer.render();
  };
  const loop = () => { if (!running) return; frame(); raf = requestAnimationFrame(loop); };
  const start = () => { if (running) return; running = true; raf = requestAnimationFrame(loop); };
  const stop = () => { running = false; cancelAnimationFrame(raf); };
  const io = new IntersectionObserver(([en]) => { visible = en.isIntersecting; visible && !document.hidden ? start() : stop(); }, { rootMargin: '100px' });
  io.observe(host);
  const onVis = () => (document.hidden ? stop() : visible && start());
  document.addEventListener('visibilitychange', onVis);
  frame();

  return {
    setProgress: p => { target = clamp(p); if (!running) frame(); },
    destroy: () => {
      stop(); io.disconnect(); ro.disconnect(); document.removeEventListener('visibilitychange', onVis);
      composer.dispose(); pmrem.dispose(); envTex.dispose(); renderer.dispose(); renderer.domElement.remove();
    },
  };
}
