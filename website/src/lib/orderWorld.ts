import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { CHANNELS } from './orderStory';

/**
 * The order's journey as one continuous 3D world, driven entirely by scroll progress (0..1): every position, every
 * colour and the camera are pure functions of progress, so scrolling back plays the film backwards exactly.
 * Idle motion (glows, rotating rings, the channel carousel) runs on the clock on top of that.
 *
 *   0 order placed on any channel  ·  1 into Nexus with stock from ERP and POS  ·  2 approval  ·  3 ship date: B2C now,
 *   B2B later  ·  4 wave and picker  ·  5 picking  ·  6 box predicted, packed, labelled  ·  7 dispatched, shipped
 *
 * Illustration with invented numbers; the steps are Nexus's steps.
 */

export { CHAPTERS, CHANNELS } from './orderStory';

export interface OrderWorld { setProgress(p: number): void; destroy(): void; }

// ------------------------------------------------------------------ small maths

const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const seg = (p: number, a: number, b: number) => clamp((p - a) / (b - a));
const ease = (t: number) => t * t * t * (t * (t * 6 - 15) + 10);           // smootherstep
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const v = (x: number, y: number, z: number) => new THREE.Vector3(x, y, z);

const COL = {
  bg: 0x070b16, floor: 0x0b1222, clay: 0xe6eaf2, clay2: 0xc4ccdb, ink: 0x1b2334, steel: 0x8792a8,
  signal: 0x5b8cff, ember: 0xff8a4c, go: 0x3ddc97, warn: 0xffc857, stop: 0xff5d6c,
  card: 0xc79a62, cardDark: 0xa97c46, wood: 0xa7784a, skin: 0xe2b896,
};

// ------------------------------------------------------------------ text on canvas

type Painter = (g: CanvasRenderingContext2D, w: number, h: number) => void;

/** A plane with a canvas texture; call repaint() when its content changes. */
function panel(wu: number, hu: number, px: number, paint: Painter, opts: { transparent?: boolean; glow?: boolean } = {}) {
  const c = document.createElement('canvas');
  c.width = px;
  c.height = Math.round(px * (hu / wu));
  const g = c.getContext('2d')!;
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: opts.transparent ?? true, toneMapped: false, side: THREE.DoubleSide });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(wu, hu), mat);
  const repaint = (p: Painter = paint) => { g.clearRect(0, 0, c.width, c.height); p(g, c.width, c.height); tex.needsUpdate = true; };
  repaint();
  return { mesh, repaint, mat };
}

const FONT = '"Inter Variable", Inter, system-ui, sans-serif';
const MONO = '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, monospace';
const DISPLAY = '"Unbounded Variable", Unbounded, system-ui, sans-serif';

function rr(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath();
  g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
}

function card(g: CanvasRenderingContext2D, w: number, h: number, border = 'rgba(255,255,255,.18)', fill = 'rgba(9,14,27,.92)') {
  rr(g, 4, 4, w - 8, h - 8, Math.min(w, h) * 0.08);
  g.fillStyle = fill; g.fill();
  g.lineWidth = 4; g.strokeStyle = border; g.stroke();
}

function text(g: CanvasRenderingContext2D, s: string, x: number, y: number, size: number, color = '#e8edf7', font = FONT, weight = 600, align: CanvasTextAlign = 'left') {
  g.font = `${weight} ${size}px ${font}`;
  g.fillStyle = color;
  g.textAlign = align;
  g.textBaseline = 'middle';
  g.fillText(s, x, y);
}

// ------------------------------------------------------------------ models

function std(color: number, extra: Partial<THREE.MeshStandardMaterialParameters> = {}) {
  return new THREE.MeshStandardMaterial({ color, roughness: 0.62, metalness: 0.05, ...extra });
}
function glow(color: number, intensity = 1.6) {
  return new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: intensity, roughness: 0.4 });
}
function rbox(w: number, h: number, d: number, r: number, mat: THREE.Material, shadow = true) {
  const m = new THREE.Mesh(new RoundedBoxGeometry(w, h, d, 3, r), mat);
  m.castShadow = shadow; m.receiveShadow = true;
  return m;
}
function box(w: number, h: number, d: number, mat: THREE.Material, shadow = true) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.castShadow = shadow; m.receiveShadow = true;
  return m;
}

/** A person: legs, body (vest colour), head, cap. Legs swing with walk(). */
function person(vest: number, cap: number) {
  const g = new THREE.Group();
  const legMat = std(COL.ink), skin = std(COL.skin);
  const legL = new THREE.Mesh(new THREE.CapsuleGeometry(0.13, 0.55, 4, 10), legMat);
  const legR = legL.clone();
  legL.position.set(-0.15, 0.42, 0); legR.position.set(0.15, 0.42, 0);
  const hipL = new THREE.Group(), hipR = new THREE.Group();
  hipL.position.set(0, 0.75, 0); hipR.position.set(0, 0.75, 0);
  legL.position.y -= 0.75; legR.position.y -= 0.75;
  hipL.add(legL); hipR.add(legR);
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.3, 0.55, 6, 14), std(vest));
  body.position.y = 1.2;
  const armMat = std(vest);
  const armL = new THREE.Mesh(new THREE.CapsuleGeometry(0.09, 0.5, 4, 8), armMat);
  const armR = armL.clone();
  const shoulderL = new THREE.Group(), shoulderR = new THREE.Group();
  shoulderL.position.set(-0.38, 1.5, 0); shoulderR.position.set(0.38, 1.5, 0);
  armL.position.y = -0.3; armR.position.y = -0.3;
  shoulderL.add(armL); shoulderR.add(armR);
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.24, 20, 16), skin);
  head.position.y = 1.86;
  const capM = new THREE.Mesh(new THREE.SphereGeometry(0.25, 20, 10, 0, Math.PI * 2, 0, Math.PI / 2), std(cap));
  capM.position.y = 1.9;
  const brim = box(0.3, 0.03, 0.22, std(cap)); brim.position.set(0, 1.92, 0.22);
  [legL, legR, body, armL, armR, head, capM].forEach(m => { m.castShadow = true; });
  g.add(hipL, hipR, body, shoulderL, shoulderR, head, capM, brim);
  const walk = (phase: number, amount: number) => {
    const s = Math.sin(phase) * 0.6 * amount;
    hipL.rotation.x = s; hipR.rotation.x = -s;
    shoulderL.rotation.x = -s * 0.8; shoulderR.rotation.x = s * 0.8;
    body.position.y = 1.2 + Math.abs(Math.cos(phase)) * 0.04 * amount;
  };
  const reach = (t: number) => { shoulderR.rotation.x = -t * 2.2; shoulderL.rotation.x = -t * 0.4; };
  return { g, walk, reach };
}

/** An open cardboard box whose top flaps fold closed with close(0..1). */
function parcel(w: number, h: number, d: number) {
  const g = new THREE.Group();
  const m = std(COL.card), md = std(COL.cardDark), t = 0.04;
  const base = box(w, t, d, m); base.position.y = t / 2;
  const front = box(w, h, t, m); front.position.set(0, h / 2, d / 2);
  const back = box(w, h, t, m); back.position.set(0, h / 2, -d / 2);
  const left = box(t, h, d, md); left.position.set(-w / 2, h / 2, 0);
  const right = box(t, h, d, md); right.position.set(w / 2, h / 2, 0);
  const flapF = new THREE.Group(), flapB = new THREE.Group();
  flapF.position.set(0, h, d / 2); flapB.position.set(0, h, -d / 2);
  const ff = box(w, t, d / 2, m); ff.position.z = -d / 4;
  const fb = box(w, t, d / 2, m); fb.position.z = d / 4;
  flapF.add(ff); flapB.add(fb);
  const tape = box(0.14, 0.012, d + 0.02, std(0xe9d6ad)); tape.position.y = h + 0.03; tape.visible = false;
  g.add(base, front, back, left, right, flapF, flapB, tape);
  const close = (c: number) => {
    flapF.rotation.x = lerp(-2.0, 0, c);
    flapB.rotation.x = lerp(2.0, 0, c);
    tape.visible = c > 0.98;
  };
  close(0);
  return { g, close, h };
}

/** A box truck facing +x, with a rear roller door. */
function truck() {
  const g = new THREE.Group();
  const white = std(0xf1f3f8, { roughness: 0.4 }), dark = std(COL.ink), glass = std(0x223047, { roughness: 0.15, metalness: 0.3 });
  const body = rbox(6.2, 3, 2.6, 0.12, white); body.position.set(-1.2, 2.0, 0);
  const stripe = box(6.22, 0.35, 2.62, std(COL.signal)); stripe.position.set(-1.2, 1.25, 0);
  const cab = rbox(2.1, 2.3, 2.5, 0.25, std(COL.signal, { roughness: 0.4 })); cab.position.set(3.0, 1.65, 0);
  const wind = box(0.05, 1.0, 2.1, glass); wind.position.set(4.06, 2.1, 0);
  const door = new THREE.Group(); door.position.set(-4.3, 3.45, 0);
  const doorPanel = box(0.08, 2.85, 2.4, std(0xdfe3ec)); doorPanel.position.y = -1.43; door.add(doorPanel);
  g.add(body, stripe, cab, wind, door);
  const wheels: THREE.Mesh[] = [];
  for (const [x, z] of [[2.9, 1.2], [2.9, -1.2], [-0.4, 1.2], [-0.4, -1.2], [-3.2, 1.2], [-3.2, -1.2]]) {
    const w = new THREE.Mesh(new THREE.CylinderGeometry(0.48, 0.48, 0.38, 24), dark);
    w.rotation.x = Math.PI / 2; w.position.set(x, 0.48, z); w.castShadow = true;
    wheels.push(w); g.add(w);
  }
  const lamp = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.2, 0.5), glow(COL.warn, 2));
  lamp.position.set(4.07, 1.2, 0.85); g.add(lamp);
  const lamp2 = lamp.clone(); lamp2.position.z = -0.85; g.add(lamp2);
  const open = (t: number) => { door.scale.y = Math.max(0.04, 1 - t); };   // rolls up into the roof
  const roll = (d: number) => wheels.forEach(w => (w.rotation.y = d / 0.48));
  return { g, open, roll };
}

/** A rack bay: uprights, three shelves and cartons on them (instanced). */
function racks(scene: THREE.Scene, rows: { x0: number; z: number; bays: number }[]) {
  const upright = std(COL.signal, { roughness: 0.5 }), beam = std(COL.ember, { roughness: 0.5 }), deck = std(0x2a3348);
  const cartonGeo = new RoundedBoxGeometry(0.9, 0.62, 0.85, 2, 0.04);
  const cartons: THREE.Matrix4[] = [];
  const rnd = mulberry(7);
  for (const r of rows) {
    for (let b = 0; b <= r.bays; b++) {
      for (const dz of [-0.6, 0.6]) {
        const u = box(0.12, 4.2, 0.12, upright); u.position.set(r.x0 + b * 2.4, 2.1, r.z + dz); scene.add(u);
      }
    }
    for (let b = 0; b < r.bays; b++) {
      for (let s = 0; s < 3; s++) {
        const y = 0.3 + s * 1.35;
        const d = box(2.3, 0.08, 1.25, deck, false); d.position.set(r.x0 + 1.2 + b * 2.4, y, r.z); d.receiveShadow = true; scene.add(d);
        for (const dz of [-0.62, 0.62]) { const be = box(2.3, 0.14, 0.08, beam, false); be.position.set(r.x0 + 1.2 + b * 2.4, y, r.z + dz); scene.add(be); }
        for (let k = 0; k < 2; k++) {
          if (rnd() < 0.15) continue;
          const m = new THREE.Matrix4().makeTranslation(r.x0 + 0.65 + k * 1.1 + b * 2.4, y + 0.36, r.z + (rnd() - 0.5) * 0.12);
          cartons.push(m);
        }
      }
    }
  }
  const inst = new THREE.InstancedMesh(cartonGeo, std(COL.card), cartons.length);
  cartons.forEach((m, i) => inst.setMatrixAt(i, m));
  inst.castShadow = true; inst.receiveShadow = true;
  scene.add(inst);
}

function mulberry(seed: number) {
  return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

/** A walk along waypoints by distance: returns position and heading at fraction t. */
function pathAt(points: THREE.Vector3[], t: number) {
  const lens = points.slice(1).map((p, i) => p.distanceTo(points[i]));
  const total = lens.reduce((a, b) => a + b, 0);
  let d = clamp(t) * total;
  for (let i = 0; i < lens.length; i++) {
    if (d <= lens[i] || i === lens.length - 1) {
      const k = lens[i] === 0 ? 0 : d / lens[i];
      const pos = points[i].clone().lerp(points[i + 1], clamp(k));
      const dir = points[i + 1].clone().sub(points[i]);
      return { pos, heading: Math.atan2(dir.x, dir.z), dist: clamp(t) * total };
    }
    d -= lens[i];
  }
  return { pos: points[points.length - 1].clone(), heading: 0, dist: total };
}

// ------------------------------------------------------------------ the world

export function mountOrderWorld(host: HTMLElement, opts: { reduced: boolean }): OrderWorld | null {
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  } catch {
    return null;
  }
  const small = window.innerWidth < 760;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, small ? 1.5 : 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-hidden', 'true');

  // where each part of the journey sits along the route (x)
  const NX = 28, GX = 44, SX = 62, WX = 84, PX = 124, DX = 146;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(COL.bg);
  scene.fog = new THREE.Fog(COL.bg, 34, 95);
  const pmrem = new THREE.PMREMGenerator(renderer);
  const env = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environment = env;
  scene.environmentIntensity = 0.45;

  const camera = new THREE.PerspectiveCamera(small ? 50 : 38, 1, 0.1, 400);
  scene.add(new THREE.HemisphereLight(0xbfd2ff, 0x0b1020, 0.9));
  const sun = new THREE.DirectionalLight(0xffffff, 2.2);
  sun.castShadow = true;
  sun.shadow.mapSize.set(small ? 1024 : 2048, small ? 1024 : 2048);
  sun.shadow.camera.left = -22; sun.shadow.camera.right = 22; sun.shadow.camera.top = 22; sun.shadow.camera.bottom = -22;
  sun.shadow.camera.near = 1; sun.shadow.camera.far = 90;
  sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.03;
  scene.add(sun, sun.target);

  // ground, grid, and the order's route that lights up as it travels
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(600, 200), std(COL.floor, { roughness: 0.95 }));
  ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; ground.position.x = 100;
  scene.add(ground);
  const grid = new THREE.GridHelper(600, 300, 0x1c2742, 0x121a2e);
  (grid.material as THREE.Material).transparent = true; (grid.material as THREE.Material).opacity = 0.55;
  grid.position.set(100, 0.01, 0);
  scene.add(grid);
  const ROUTE_START = 0, ROUTE_END = 200;
  const routeBase = box(ROUTE_END - ROUTE_START, 0.02, 0.5, std(0x18223a), false);
  routeBase.position.set((ROUTE_START + ROUTE_END) / 2, 0.02, 0); scene.add(routeBase);
  const routeLit = box(1, 0.03, 0.5, glow(COL.signal, 1.4), false);
  routeLit.position.set(ROUTE_START, 0.03, 0); scene.add(routeLit);

  // ---------------- zone 0: the customer's phone and every channel
  const phone = new THREE.Group();
  const phoneBody = rbox(2.3, 4.6, 0.26, 0.28, std(0x111827, { roughness: 0.3, metalness: 0.4 }));
  phone.add(phoneBody);
  let screenState = -1;
  const screen = panel(2.08, 4.3, 520, () => { });
  screen.mesh.position.z = 0.135;
  phone.add(screen.mesh);
  phone.position.set(0, 3.1, 0);
  phone.rotation.set(-0.08, 0.35, 0);
  scene.add(phone);
  const paintScreen = (state: number) => {
    screen.repaint((g, w, h) => {
      g.fillStyle = '#f6f7fb'; rr(g, 0, 0, w, h, 40); g.fill();
      text(g, 'Northwind Outfitters', 36, 70, 30, '#0b1222', FONT, 700);
      text(g, 'Checkout', 36, 112, 24, '#566074', FONT, 500);
      const item = (y: number, name: string, price: string, color: string) => {
        g.fillStyle = color; rr(g, 36, y, 90, 90, 14); g.fill();
        text(g, name, 146, y + 30, 26, '#0b1222', FONT, 600);
        text(g, price, 146, y + 66, 24, '#566074', FONT, 500);
      };
      item(160, 'Navy jacket · M', '$120.00', '#5b8cff');
      item(280, 'Wool scarf', '$35.00', '#ff8a4c');
      g.fillStyle = '#e3e7ef'; g.fillRect(36, 400, w - 72, 2);
      text(g, 'Total', 36, 446, 26, '#0b1222', FONT, 600);
      text(g, '$155.00', w - 36, 446, 26, '#0b1222', FONT, 700, 'right');
      if (state === 0) {
        g.fillStyle = '#0b1222'; rr(g, 36, h - 170, w - 72, 96, 48); g.fill();
        text(g, 'Place order', w / 2, h - 122, 30, '#ffffff', FONT, 700, 'center');
      } else {
        g.fillStyle = '#3ddc97'; rr(g, 36, h - 230, w - 72, 160, 26); g.fill();
        text(g, '✓ Order placed', w / 2, h - 182, 32, '#05261a', FONT, 800, 'center');
        text(g, '#4821 · on its way to Nexus', w / 2, h - 128, 22, '#05261a', MONO, 600, 'center');
      }
    });
  };
  paintScreen(0); screenState = 0;

  const channelTiles = CHANNELS.map((name, i) => {
    const t = panel(2.3, 0.78, 360, (g, w, h) => {
      card(g, w, h, i === 0 ? 'rgba(91,140,255,.9)' : 'rgba(255,255,255,.22)');
      text(g, name, w / 2, h / 2 + 2, name.length > 14 ? 34 : 40, '#ffffff', FONT, 700, 'center');
    });
    scene.add(t.mesh);
    return t;
  });

  // the order itself: a glowing token with its number
  const token = new THREE.Group();
  const tokenCube = rbox(0.9, 0.9, 0.9, 0.18, glow(COL.ember, 1.3));
  token.add(tokenCube);
  const tokenTag = panel(1.9, 0.6, 300, (g, w, h) => { card(g, w, h, 'rgba(255,138,76,.9)'); text(g, 'Order #4821', w / 2, h / 2, 40, '#fff', MONO, 700, 'center'); });
  tokenTag.mesh.position.y = 1.0;
  token.add(tokenTag.mesh);
  const tokenLight = new THREE.PointLight(COL.ember, 6, 7, 2);
  token.add(tokenLight);
  scene.add(token);

  // ---------------- zone 1: Nexus, with stock flowing in from ERP and POS
  const core = new THREE.Group(); core.position.set(NX, 0, 0); scene.add(core);
  const plinth = new THREE.Mesh(new THREE.CylinderGeometry(4.2, 4.6, 0.5, 6), std(0x1a2338, { roughness: 0.4 }));
  plinth.position.y = 0.25; plinth.receiveShadow = true; plinth.castShadow = true; core.add(plinth);
  const brain = new THREE.Mesh(new THREE.IcosahedronGeometry(1.25, 3), glow(COL.signal, 1.1));
  brain.position.y = 2.9; core.add(brain);
  const ringA = new THREE.Mesh(new THREE.TorusGeometry(2.2, 0.05, 12, 120), glow(COL.signal, 2));
  ringA.position.y = 2.9; ringA.rotation.x = Math.PI / 2.4; core.add(ringA);
  const ringB = new THREE.Mesh(new THREE.TorusGeometry(2.7, 0.035, 12, 120), glow(COL.ember, 2));
  ringB.position.y = 2.9; ringB.rotation.x = Math.PI / 1.7; core.add(ringB);
  core.add(new THREE.PointLight(COL.signal, 18, 14, 2).translateY(3));
  const coreTag = panel(3.2, 0.8, 420, (g, w, h) => { card(g, w, h, 'rgba(91,140,255,.8)'); text(g, 'NEXUS', w / 2, h / 2 + 2, 52, '#fff', DISPLAY, 700, 'center'); });
  coreTag.mesh.position.set(0, 5.1, 0); core.add(coreTag.mesh);

  const erp = new THREE.Group(); erp.position.set(NX - 8, 0, -6); scene.add(erp);
  erp.add(rbox(2.2, 4.4, 1.6, 0.08, std(0x1c2436, { roughness: 0.35, metalness: 0.3 })).translateY(2.2));
  for (let i = 0; i < 9; i++) { const led = box(1.6, 0.06, 0.02, glow(i % 3 === 0 ? COL.go : COL.signal, 1.6), false); led.position.set(0, 0.8 + i * 0.42, 0.81); erp.add(led); }
  const erpTag = panel(3.6, 0.95, 460, (g, w, h) => { card(g, w, h); text(g, 'ERP', 30, h * 0.36, 40, '#fff', DISPLAY, 700); text(g, 'NetSuite · SAP · any', 30, h * 0.72, 26, '#8b97ad', MONO, 500); });
  erpTag.mesh.position.set(0, 5.1, 0); erp.add(erpTag.mesh);

  const pos = new THREE.Group(); pos.position.set(NX + 8, 0, -6); scene.add(pos);
  pos.add(rbox(3.2, 1.2, 1.4, 0.1, std(COL.clay2)).translateY(0.6));
  const posScreen = rbox(0.9, 0.7, 0.08, 0.04, std(0x111827, { roughness: 0.3 })); posScreen.position.set(0.4, 1.7, 0); posScreen.rotation.x = -0.4; pos.add(posScreen);
  const posGlow = box(0.78, 0.56, 0.01, glow(COL.go, 1.2), false); posGlow.position.set(0.4, 1.71, 0.05); posGlow.rotation.x = -0.4; pos.add(posGlow);
  const posTag = panel(3.6, 0.95, 460, (g, w, h) => { card(g, w, h); text(g, 'Stores · POS', 30, h * 0.36, 38, '#fff', DISPLAY, 700); text(g, 'Shopify POS · any POS', 30, h * 0.72, 26, '#8b97ad', MONO, 500); });
  posTag.mesh.position.set(0, 3.2, 0); pos.add(posTag.mesh);

  // data streams into the core
  const streams = [erp.position.clone().setY(3.4), pos.position.clone().setY(1.8)].map(from => new THREE.QuadraticBezierCurve3(from, from.clone().lerp(v(NX, 6, 0), 0.5).setY(6.5), v(NX, 3, 0)));
  const sCount = 90;
  const sPos = new Float32Array(sCount * 3);
  const sGeo = new THREE.BufferGeometry(); sGeo.setAttribute('position', new THREE.BufferAttribute(sPos, 3));
  const sMat = new THREE.PointsMaterial({ color: COL.go, size: 0.16, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending });
  scene.add(new THREE.Points(sGeo, sMat));

  // stock hologram: per location, on hand, minus the safety threshold = what can be sold
  const LOCS = [{ n: 'Warehouse · East', on: 41, safe: 5 }, { n: 'Store · New York', on: 3, safe: 2 }, { n: 'Store · Boston', on: 4, safe: 2 }];
  let stockStep = -1;
  const stock = panel(6.2, 3.4, 900, () => { });
  stock.mesh.position.set(NX - 1.5, 6.6, -4.2);
  scene.add(stock.mesh);
  const paintStock = (k: number) => stock.repaint((g, w, h) => {
    card(g, w, h, 'rgba(61,220,151,.6)');
    text(g, 'Navy jacket · M', 40, 60, 36, '#fff', FONT, 700);
    text(g, 'on hand  −  safety stock  =  sellable', w - 40, 60, 24, '#8b97ad', MONO, 500, 'right');
    LOCS.forEach((l, i) => {
      const y = 140 + i * 130, max = 45, bw = w - 330;
      const shown = Math.round(l.on * clamp(k * 1.4 - i * 0.15));
      text(g, l.n, 40, y, 26, '#c7d0e0', FONT, 600);
      g.fillStyle = 'rgba(255,255,255,.08)'; rr(g, 40, y + 26, bw, 30, 15); g.fill();
      g.fillStyle = '#3ddc97'; rr(g, 40, y + 26, Math.max(30, bw * (shown / max)), 30, 15); g.fill();
      if (k > 0.55) {
        const sx = 40 + bw * (Math.min(l.safe, shown) / max);
        g.fillStyle = 'rgba(255,93,108,.85)'; rr(g, 40, y + 26, Math.max(18, sx - 40), 30, 15); g.fill();
      }
      text(g, k > 0.55 ? `${l.on} − ${l.safe} = ${Math.max(0, l.on - l.safe)}` : String(shown), w - 40, y + 41, 30, '#fff', MONO, 700, 'right');
    });
  });

  // ---------------- zone 2: approval gate
  const gate = new THREE.Group(); gate.position.set(GX, 0, 0); scene.add(gate);
  const pillarMat = std(0x1d2740, { roughness: 0.35, metalness: 0.3 });
  const pl = rbox(0.6, 4.2, 0.6, 0.1, pillarMat); pl.position.set(0, 2.1, -2.2); gate.add(pl);
  const pr = pl.clone(); pr.position.z = 2.2; gate.add(pr);
  const beamTop = rbox(0.7, 0.5, 5.0, 0.1, pillarMat); beamTop.position.y = 4.3; gate.add(beamTop);
  const gateLightMat = glow(COL.signal, 2.2);
  const gateStrip = box(0.08, 0.08, 4.4, gateLightMat, false); gateStrip.position.set(0.36, 4.05, 0); gate.add(gateStrip);
  const laser = new THREE.Mesh(new THREE.PlaneGeometry(4.2, 0.08), new THREE.MeshBasicMaterial({ color: COL.signal, transparent: true, opacity: 0.85, side: THREE.DoubleSide, toneMapped: false }));
  laser.rotation.y = Math.PI / 2; gate.add(laser);
  let checksStep = -1;
  const checks = panel(4.4, 3.1, 640, () => { });
  checks.mesh.position.set(GX + 1.5, 4.0, -4.6); checks.mesh.rotation.y = -0.25;
  scene.add(checks.mesh);
  const CHECKS = ['Payment captured', 'Address valid', 'Order value under limit', 'Not on a block list'];
  const paintChecks = (n: number, approved: boolean) => checks.repaint((g, w, h) => {
    card(g, w, h, approved ? 'rgba(61,220,151,.9)' : 'rgba(91,140,255,.7)');
    text(g, 'Approval rules', 36, 52, 34, '#fff', FONT, 700);
    CHECKS.forEach((c, i) => {
      const y = 120 + i * 62, ok = i < n;
      g.fillStyle = ok ? '#3ddc97' : 'rgba(255,255,255,.12)'; g.beginPath(); g.arc(52, y, 16, 0, Math.PI * 2); g.fill();
      if (ok) text(g, '✓', 52, y + 1, 22, '#05261a', FONT, 900, 'center');
      text(g, c, 86, y, 26, ok ? '#e8edf7' : '#6b768c', FONT, 500);
    });
    if (approved) { g.fillStyle = '#3ddc97'; rr(g, 36, h - 74, w - 72, 50, 25); g.fill(); text(g, 'APPROVED', w / 2, h - 48, 26, '#05261a', MONO, 800, 'center'); }
  });

  // ---------------- zone 3: ship date decides when fulfilment starts
  const laneB2C = box(22, 0.03, 0.5, glow(COL.go, 0.9), false); laneB2C.position.set(SX + 9, 0.035, 3.2); scene.add(laneB2C);
  const laneB2B = box(14, 0.03, 0.5, glow(COL.warn, 0.7), false); laneB2B.position.set(SX + 5, 0.035, -4.2); scene.add(laneB2B);
  const signC = panel(4.2, 1.25, 600, (g, w, h) => { card(g, w, h, 'rgba(61,220,151,.9)'); text(g, 'B2C · ship today', 30, h * 0.36, 40, '#fff', FONT, 700); text(g, 'Reserve stock now, fulfil now', 30, h * 0.72, 28, '#9fe9c8', MONO, 500); });
  signC.mesh.position.set(SX + 3, 2.4, 5.2); signC.mesh.rotation.y = -0.15; scene.add(signC.mesh);
  const signB = panel(4.6, 1.25, 640, (g, w, h) => { card(g, w, h, 'rgba(255,200,87,.9)'); text(g, 'B2B · ship by 15 Jan', 30, h * 0.36, 40, '#fff', FONT, 700); text(g, 'Hold; release near the ship date', 30, h * 0.72, 28, '#ffe2a3', MONO, 500); });
  signB.mesh.position.set(SX + 1, 2.4, -7.2); signB.mesh.rotation.y = 0.15; scene.add(signB.mesh);

  // the wholesale order: a pallet of cartons
  const pallet = new THREE.Group();
  pallet.add(box(2.2, 0.25, 1.8, std(COL.wood)).translateY(0.13));
  for (let x = 0; x < 2; x++) for (let z = 0; z < 2; z++) for (let y = 0; y < 2; y++) {
    const c = rbox(1.0, 0.75, 0.85, 0.05, std(y ? COL.card : COL.cardDark)); c.position.set(-0.52 + x * 1.04, 0.66 + y * 0.77, -0.44 + z * 0.88); pallet.add(c);
  }
  const palletTag = panel(2.6, 0.62, 400, (g, w, h) => { card(g, w, h, 'rgba(255,200,87,.9)'); text(g, 'PO-881 · 240 units', w / 2, h / 2, 34, '#fff', MONO, 700, 'center'); });
  palletTag.mesh.position.y = 2.6; pallet.add(palletTag.mesh);
  pallet.position.set(SX + 3, 0, -4.2);
  scene.add(pallet);
  let calStep = -1;
  const cal = panel(4.4, 3.3, 640, () => { });
  cal.mesh.position.set(SX + 8.5, 3.0, -7.4);
  scene.add(cal.mesh);
  const DAYS = ['12 Nov', '1 Dec', '20 Dec', '2 Jan', '12 Jan'];
  const paintCal = (k: number) => cal.repaint((g, w, h) => {
    const i = Math.min(DAYS.length - 1, Math.floor(k * DAYS.length));
    const released = i === DAYS.length - 1;
    card(g, w, h, released ? 'rgba(61,220,151,.9)' : 'rgba(255,200,87,.8)');
    text(g, 'Today', 36, 52, 26, '#8b97ad', MONO, 600);
    text(g, DAYS[i], 36, 112, 64, '#fff', DISPLAY, 700);
    text(g, 'Ship by 15 Jan', 36, 186, 28, '#c7d0e0', FONT, 600);
    g.fillStyle = 'rgba(255,255,255,.1)'; rr(g, 36, 230, w - 72, 22, 11); g.fill();
    g.fillStyle = released ? '#3ddc97' : '#ffc857'; rr(g, 36, 230, (w - 72) * ((i + 1) / DAYS.length), 22, 11); g.fill();
    text(g, released ? 'Released: reserve and pick now' : 'Not reserved yet: stock serves other orders', 36, 296, 24, released ? '#9fe9c8' : '#ffe2a3', FONT, 600);
  });

  // ---------------- zones 4–5: the warehouse, a wave and a picker
  racks(scene, [{ x0: WX + 2, z: -4.6, bays: 7 }, { x0: WX + 2, z: 0.0 - 9.2, bays: 7 }, { x0: WX + 2, z: 4.6, bays: 7 }]);
  const BIN_A = v(WX + 6.9, 1.65, -3.9), BIN_B = v(WX + 13.9, 3.0, 3.9);
  const binMarker = (p: THREE.Vector3, label: string) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.05, 10, 50), glow(COL.signal, 2.5));
    ring.position.copy(p); ring.rotation.y = Math.PI / 2; ring.visible = false; scene.add(ring);
    const tag = panel(1.8, 0.5, 300, (g, w, h) => { card(g, w, h, 'rgba(91,140,255,.9)'); text(g, label, w / 2, h / 2, 36, '#fff', MONO, 700, 'center'); });
    tag.mesh.position.copy(p).add(v(0, 0.9, 0)); tag.mesh.visible = false; scene.add(tag.mesh);
    return { ring, tag };
  };
  const markA = binMarker(BIN_A, 'A-03-2'), markB = binMarker(BIN_B, 'B-07-3');
  const itemA = rbox(0.6, 0.4, 0.5, 0.06, std(COL.signal, { roughness: 0.45 })); scene.add(itemA);
  const itemB = rbox(0.5, 0.3, 0.4, 0.06, std(COL.ember, { roughness: 0.45 })); scene.add(itemB);

  let waveStep = -1;
  const wave = panel(5.6, 3.3, 820, () => { });
  wave.mesh.position.set(WX + 5, 4.8, 0.5);
  scene.add(wave.mesh);
  const paintWave = (k: number) => wave.repaint((g, w, h) => {
    card(g, w, h, 'rgba(91,140,255,.8)');
    text(g, 'Pick wave W-12', 36, 54, 38, '#fff', FONT, 700);
    text(g, k > 0.5 ? '18 orders · 31 lines · 1 route' : 'Grouping orders…', 36, 104, 26, '#8b97ad', MONO, 600);
    const n = Math.round(18 * clamp(k * 1.3));
    for (let i = 0; i < 18; i++) {
      const x = 36 + (i % 9) * 82, y = 140 + Math.floor(i / 9) * 70;
      g.fillStyle = i < n ? (i === 0 ? '#ff8a4c' : '#5b8cff') : 'rgba(255,255,255,.08)';
      rr(g, x, y, 70, 54, 10); g.fill();
    }
    if (k > 0.75) {
      g.fillStyle = '#3ddc97'; rr(g, 36, h - 74, w - 72, 50, 25); g.fill();
      text(g, 'Assigned to picker: Sam', w / 2, h - 48, 26, '#05261a', MONO, 800, 'center');
    }
  });

  const picker = person(COL.ember, COL.signal);
  const cart = new THREE.Group();
  cart.add(box(1.0, 0.06, 0.7, std(COL.steel)).translateY(0.6));
  const tote = new THREE.Group();
  const toteMat = std(COL.signal, { roughness: 0.5 });
  for (const [w, h, d, x, y, z] of [[0.9, 0.05, 0.6, 0, 0, 0], [0.9, 0.4, 0.05, 0, 0.2, 0.3], [0.9, 0.4, 0.05, 0, 0.2, -0.3], [0.05, 0.4, 0.6, 0.45, 0.2, 0], [0.05, 0.4, 0.6, -0.45, 0.2, 0]] as const) {
    const m = box(w, h, d, toteMat); m.position.set(x, y, z); tote.add(m);
  }
  tote.position.y = 0.66; cart.add(tote);
  for (const [x, z] of [[0.4, 0.3], [-0.4, 0.3], [0.4, -0.3], [-0.4, -0.3]]) {
    const w = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.06, 12), std(COL.ink)); w.rotation.x = Math.PI / 2; w.position.set(x, 0.1, z); cart.add(w);
    const leg = box(0.04, 0.55, 0.04, std(COL.steel)); leg.position.set(x, 0.35, z); cart.add(leg);
  }
  const handle = box(0.05, 0.5, 0.7, std(COL.steel)); handle.position.set(-0.52, 0.95, 0); cart.add(handle);
  const pickerRig = new THREE.Group();
  picker.g.position.x = -0.95; picker.g.rotation.y = Math.PI / 2;
  pickerRig.add(picker.g, cart);
  scene.add(pickerRig);
  const pickerTag = panel(1.9, 0.5, 300, (g, w, h) => { card(g, w, h, 'rgba(255,138,76,.9)'); text(g, 'Sam · W-12', w / 2, h / 2, 34, '#fff', MONO, 700, 'center'); });
  pickerTag.mesh.position.set(-0.95, 2.6, 0); pickerRig.add(pickerTag.mesh);
  const AISLE_A = -2.3, AISLE_B = 2.3;
  const PICK_PATH = [v(WX - 1, 0, 0), v(WX + 1.5, 0, AISLE_A), v(BIN_A.x - 0.2, 0, AISLE_A), v(BIN_A.x + 0.2, 0, AISLE_A),
    v(BIN_B.x - 1.5, 0, AISLE_A), v(BIN_B.x - 1.0, 0, AISLE_B), v(BIN_B.x - 0.2, 0, AISLE_B), v(BIN_B.x + 0.2, 0, AISLE_B), v(WX + 21, 0, 0.6), v(PX - 3.6, 0, 1.2)];

  // where on the route the picker stops at each bin (waypoints 2 and 6), and how long each pick takes
  const HOLD = 0.07, WALK = 1 - 2 * HOLD;
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

  // ---------------- zone 6: packing
  const table = new THREE.Group(); table.position.set(PX, 0, 0); scene.add(table);
  table.add(rbox(4.2, 0.16, 2.0, 0.04, std(COL.clay))).children[0].position.y = 1.0;
  for (const [x, z] of [[1.9, 0.85], [-1.9, 0.85], [1.9, -0.85], [-1.9, -0.85]]) { const l = box(0.1, 1.0, 0.1, std(COL.steel)); l.position.set(x, 0.5, z); table.add(l); }
  const printer = rbox(0.7, 0.45, 0.6, 0.06, std(0x2a3348)); printer.position.set(1.6, 1.32, -0.55); table.add(printer);
  const labelPaper = box(0.5, 0.01, 0.42, std(0xffffff, { roughness: 0.8 })); labelPaper.position.set(1.6, 1.56, -0.3); table.add(labelPaper);
  const monitor = panel(2.4, 1.55, 520, () => { });
  monitor.mesh.position.set(PX - 1.0, 2.35, -1.05); monitor.mesh.rotation.y = 0.2;
  scene.add(monitor.mesh);
  let monStep = -1;
  const paintMonitor = (k: number) => monitor.repaint((g, w, h) => {
    card(g, w, h, k > 0.3 ? 'rgba(61,220,151,.9)' : 'rgba(91,140,255,.8)', 'rgba(9,14,27,.96)');
    text(g, 'Packing · #4821', 28, 44, 28, '#fff', FONT, 700);
    text(g, '2 items · 0.9 kg', 28, 84, 22, '#8b97ad', MONO, 600);
    if (k > 0.3) {
      text(g, 'Recommended box', 28, 136, 22, '#8b97ad', FONT, 600);
      text(g, 'M · 30 × 20 × 15 cm', 28, 180, 34, '#3ddc97', MONO, 800);
      text(g, '1 box · 78% full', 28, 224, 22, '#c7d0e0', MONO, 600);
    }
    if (k > 0.8) { g.fillStyle = '#ffc857'; rr(g, 28, h - 70, w - 56, 46, 23); g.fill(); text(g, 'Label printed · 1Z 999 AA1 01 2345 6784', w / 2, h - 46, 18, '#3a2a00', MONO, 800, 'center'); }
  });
  const ghost = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.5, 1.0, 1.1)), new THREE.LineBasicMaterial({ color: COL.go, transparent: true, opacity: 0 }));
  ghost.position.set(PX, 1.6, 0.1); scene.add(ghost);
  const pkg = parcel(1.5, 1.0, 1.1);
  scene.add(pkg.g);
  const label = panel(0.62, 0.5, 220, (g, w, h) => {
    g.fillStyle = '#ffffff'; g.fillRect(0, 0, w, h);
    text(g, 'UPS GROUND', 14, 26, 22, '#000', MONO, 800);
    for (let i = 0; i < 46; i++) { g.fillStyle = '#000'; g.fillRect(14 + i * 4, 52, (i * 7) % 3 + 1, 70); }
    text(g, '#4821', 14, 150, 22, '#000', MONO, 700);
  }, { transparent: false });
  label.mesh.rotation.x = -Math.PI / 2;
  scene.add(label.mesh);
  const packer = person(COL.go, COL.ink);
  packer.g.position.set(PX, 0, -1.6);
  scene.add(packer.g);

  // ---------------- zone 7: dispatch
  const conveyor = new THREE.Group(); scene.add(conveyor);
  const convLen = DX - 3 - (PX + 2.4);
  const belt = box(convLen, 0.12, 1.3, std(0x222b40, { roughness: 0.8 })); belt.position.set(PX + 2.4 + convLen / 2, 0.95, 0); conveyor.add(belt);
  for (let i = 0; i <= convLen; i += 2) { const l = box(0.1, 0.9, 1.3, std(COL.steel)); l.position.set(PX + 2.4 + i, 0.45, 0); conveyor.add(l); }
  const dock = new THREE.Group(); dock.position.set(DX, 0, 0); scene.add(dock);
  const wallMat = std(0x1a2236, { roughness: 0.8 });
  const wl = box(0.6, 4.6, 6, wallMat); wl.position.set(0, 2.3, -6.2); dock.add(wl);
  const wr = box(0.6, 4.6, 6, wallMat); wr.position.set(0, 2.3, 6.2); dock.add(wr);
  const wt = box(0.6, 0.5, 6.4, wallMat); wt.position.set(0, 4.35, 0); dock.add(wt);
  const dockDoor = box(0.15, 4.2, 6.4, std(0x2c3650)); dockDoor.position.set(0.1, 2.1, 0); dock.add(dockDoor);
  const dockLamp = box(0.1, 0.18, 1.4, glow(COL.go, 2), false); dockLamp.position.set(0.4, 4.4, 0); dock.add(dockLamp);
  const dockTag = panel(3.4, 0.8, 460, (g, w, h) => { card(g, w, h, 'rgba(61,220,151,.8)'); text(g, 'DOCK 3 · UPS 17:30', w / 2, h / 2, 36, '#fff', MONO, 700, 'center'); });
  dockTag.mesh.position.set(0.4, 5.3, 0); dockTag.mesh.rotation.y = Math.PI / 2; dock.add(dockTag.mesh);
  const extra = [0, 1, 2, 3].map(i => { const p = rbox(1.0 - i * 0.1, 0.7, 0.9, 0.05, std(i % 2 ? COL.card : COL.cardDark)); scene.add(p); return p; });
  const van = truck();
  // the truck faces away from the dock (+x), so it reverses in and drives straight off
  scene.add(van.g);
  const road = box(220, 0.02, 4.5, std(0x121a2c, { roughness: 0.9 }), false); road.position.set(DX + 120, 0.015, 0); scene.add(road);
  for (let i = 0; i < 40; i++) { const d = box(1.6, 0.03, 0.14, std(0x55607a), false); d.position.set(DX + 6 + i * 5, 0.03, 0); scene.add(d); }

  // ------------------------------------------------------------------ camera path

  type Shot = { p: number; pos: () => THREE.Vector3; look: () => THREE.Vector3 };
  const follow = (o: THREE.Object3D, off: THREE.Vector3, lookOff = v(0, 1.2, 0)) => ({ pos: () => o.position.clone().add(off), look: () => o.position.clone().add(lookOff) });
  const SHOTS: Shot[] = [
    { p: 0.0, pos: () => v(9, 6.5, 15), look: () => v(0, 2.8, 0) },
    { p: 0.07, pos: () => v(5.5, 5.2, 11.5), look: () => v(0, 2.9, 0) },
    { p: 0.12, pos: () => v(16, 9, 19), look: () => v(22, 2.6, -1) },
    { p: 0.2, pos: () => v(NX + 3, 7.5, 18), look: () => v(NX + 1, 4.2, -2.5) },
    { p: 0.25, pos: () => v(GX - 9, 7, 14), look: () => v(GX + 0.5, 2.6, -1.5) },
    { p: 0.33, pos: () => v(GX + 3, 6, 13), look: () => v(GX - 0.5, 2.4, -1.5) },
    { p: 0.38, pos: () => v(SX - 4, 13, 17), look: () => v(SX + 5, 1, -1) },
    { p: 0.45, pos: () => v(SX + 5, 6.5, 8), look: () => v(SX + 6, 2.2, -5) },
    { p: 0.5, pos: () => v(WX - 3, 13, 16), look: () => v(WX + 8, 1.5, -1) },
    { p: 0.555, pos: () => v(WX + 2, 7.5, 14), look: () => v(WX + 6, 3.6, -1) },
    { p: 0.6, ...follow(pickerRig, v(-5, 5.5, 7.5)) },
    { p: 0.68, ...follow(pickerRig, v(-4, 6, 8)) },
    { p: 0.72, pos: () => v(PX - 2.5, 5.6, 7.5), look: () => v(PX, 1.7, 0) },
    { p: 0.8, pos: () => v(PX + 1.5, 4.6, 5), look: () => v(PX, 1.6, 0) },
    { p: 0.85, pos: () => v(DX + 15, 10, 16), look: () => v(DX + 1, 1.6, 0) },
    { p: 0.93, pos: () => v(DX + 13, 8.5, 14), look: () => v(DX + 1.5, 1.6, 0) },
    { p: 0.965, ...follow(van.g, v(-4, 8, 15), v(1, 1.5, 0)) },
    { p: 1.0, ...follow(van.g, v(-6, 34, 16), v(6, 0, 0)) },
  ];
  const camPos = new THREE.Vector3(), camLook = new THREE.Vector3();
  const shotAt = (p: number) => {
    let i = 0;
    while (i < SHOTS.length - 2 && p > SHOTS[i + 1].p) i++;
    const a = SHOTS[i], b = SHOTS[i + 1];
    const t = ease(seg(p, a.p, b.p));
    camPos.copy(a.pos()).lerp(b.pos(), t);
    camLook.copy(a.look()).lerp(b.look(), t);
    // a tall screen sees less sideways, so every shot steps back
    if (pullBack !== 1) camPos.sub(camLook).multiplyScalar(pullBack).add(camLook);
  };
  let pullBack = 1;

  // ------------------------------------------------------------------ state for a given progress

  const TOKEN_ROUTE = new THREE.CatmullRomCurve3([v(0, 3.4, 0.4), v(6, 6, 2), v(16, 5, 1), v(NX - 2.5, 3.2, 0.5)]);
  const TOKEN_ON = new THREE.CatmullRomCurve3([v(NX, 3, 0), v(NX + 6, 2.2, 0.4), v(GX - 3, 1.8, 0), v(GX + 4, 1.8, 0)]);
  const TOKEN_B2C = new THREE.CatmullRomCurve3([v(GX + 4, 1.8, 0), v(SX - 2, 1.2, 1.8), v(SX + 6, 1.2, 3.2), v(WX - 2, 1.2, 1)]);
  const PACK_AT = v(PX, 1.08, 0.1);
  const CONV_START = PACK_AT.clone().setY(1.02).add(v(2.4, 0, -0.1));
  const TRUCK_DOCK = v(DX + 4.6, 0, 0);

  const update = (p: number, time: number) => {
    // route that lights up
    const routeX = lerp(ROUTE_START, DX + 2, clamp(p / 0.95));
    routeLit.scale.x = Math.max(0.001, routeX - ROUTE_START);
    routeLit.position.x = ROUTE_START + (routeX - ROUTE_START) / 2;

    // chapter 0: phone and channels
    const placed = p > 0.045 ? 1 : 0;
    if (placed !== screenState) { paintScreen(placed); screenState = placed; }
    const ringT = time * 0.12 + p * 2.5;
    const ringR = small ? 5.3 : 6.2;
    channelTiles.forEach((t, i) => {
      const a = (i / CHANNELS.length) * Math.PI * 2 + ringT;
      const fade = 1 - seg(p, 0.16, 0.24);
      t.mesh.position.set(Math.cos(a) * ringR, 3.0 + Math.sin(a * 2 + time * 0.6) * 0.35 + (i % 2) * 0.9 - 0.45, Math.sin(a) * ringR * 0.65);
      t.mesh.lookAt(camera.position);
      t.mat.opacity = fade;
      t.mesh.visible = fade > 0.01;
    });
    phone.rotation.y = 0.35 + Math.sin(time * 0.4) * 0.04;

    // the order token: phone → Nexus → gate → B2C lane → warehouse
    let tp: THREE.Vector3;
    if (p < 0.05) tp = TOKEN_ROUTE.getPoint(0);
    else if (p < 0.2) tp = TOKEN_ROUTE.getPoint(ease(seg(p, 0.05, 0.15)));
    else if (p < 0.24) tp = v(NX, 3 + Math.sin(time * 2) * 0.1, 0).lerp(TOKEN_ROUTE.getPoint(1), 1 - ease(seg(p, 0.15, 0.2)));
    else if (p < 0.34) tp = TOKEN_ON.getPoint(ease(seg(p, 0.24, 0.32)));
    else tp = TOKEN_B2C.getPoint(ease(seg(p, 0.35, 0.44)));
    token.position.copy(tp);
    token.visible = p < 0.475;
    tokenCube.rotation.set(time * 0.6, time * 0.8, 0);
    const approvedNow = p > 0.3;
    (tokenCube.material as THREE.MeshStandardMaterial).emissive.setHex(approvedNow ? COL.go : COL.ember);
    (tokenCube.material as THREE.MeshStandardMaterial).color.setHex(approvedNow ? COL.go : COL.ember);
    tokenLight.color.setHex(approvedNow ? COL.go : COL.ember);
    tokenTag.mesh.lookAt(camera.position);

    // chapter 1: Nexus and the stock streams
    brain.rotation.y = time * 0.25; brain.rotation.x = time * 0.11;
    ringA.rotation.z = time * 0.5; ringB.rotation.z = -time * 0.35;
    (brain.material as THREE.MeshStandardMaterial).emissiveIntensity = 0.9 + Math.sin(time * 2.2) * 0.25 + seg(p, 0.12, 0.2) * 0.8;
    const flow = seg(p, 0.12, 0.17) * (1 - seg(p, 0.25, 0.3));
    sMat.opacity = flow * 0.95;
    for (let i = 0; i < sCount; i++) {
      const c = streams[i % 2];
      const q = c.getPoint(((i / sCount) * 2 + time * 0.35) % 1);
      sPos.set([q.x, q.y, q.z], i * 3);
    }
    sGeo.attributes.position.needsUpdate = true;
    const sk = Math.round(seg(p, 0.13, 0.22) * 20) / 20;
    if (sk !== stockStep) { paintStock(sk); stockStep = sk; }
    stock.mat.opacity = seg(p, 0.12, 0.14) * (1 - seg(p, 0.25, 0.28));
    stock.mesh.visible = stock.mat.opacity > 0.01;
    stock.mesh.lookAt(camera.position);

    // chapter 2: approval gate
    const scan = seg(p, 0.27, 0.32);
    laser.position.y = 0.3 + ((time * 1.6) % 1) * 3.8;
    (laser.material as THREE.MeshBasicMaterial).color.setHex(approvedNow ? COL.go : COL.signal);
    gateLightMat.emissive.setHex(approvedNow ? COL.go : COL.signal); gateLightMat.color.setHex(approvedNow ? COL.go : COL.signal);
    const nChecks = Math.floor(scan * 4.999);
    const cs = nChecks * 2 + (approvedNow ? 1 : 0);
    if (cs !== checksStep) { paintChecks(nChecks, approvedNow && nChecks >= 4); checksStep = cs; }
    checks.mat.opacity = seg(p, 0.24, 0.26) * (1 - seg(p, 0.38, 0.41));
    checks.mesh.visible = checks.mat.opacity > 0.01;
    checks.mesh.lookAt(camera.position);

    // chapter 3: ship date. The B2B pallet waits; the calendar runs; then it is released
    const calK = seg(p, 0.395, 0.455);
    const ck = Math.min(4, Math.floor(calK * 5));
    if (ck !== calStep) { paintCal(calK); calStep = ck; }
    const release = ease(seg(p, 0.455, 0.475));
    pallet.position.set(lerp(SX + 3, SX + 12, release), 0, -4.2);
    palletTag.mesh.lookAt(camera.position);
    cal.mat.opacity = seg(p, 0.37, 0.39) * (1 - seg(p, 0.5, 0.52));
    cal.mesh.visible = cal.mat.opacity > 0.01;

    // chapter 4: the wave, built and assigned
    const wk = Math.round(seg(p, 0.475, 0.55) * 16) / 16;
    if (wk !== waveStep) { paintWave(wk); waveStep = wk; }
    wave.mat.opacity = seg(p, 0.47, 0.49) * (1 - seg(p, 0.6, 0.63));
    wave.mesh.visible = wave.mat.opacity > 0.01;
    wave.mesh.lookAt(camera.position);

    // chapter 5: the picker walks the route, picks A-03-2 then B-07-3, and heads to packing
    const walkT = seg(p, 0.56, 0.69);
    const ph = pause(walkT);
    const wp = pathAt(PICK_PATH, ph.t);
    pickerRig.position.copy(wp.pos);
    pickerRig.rotation.y = wp.heading - Math.PI / 2;
    const moving = walkT > 0 && walkT < 1 && ph.picking < 0;
    picker.walk(wp.dist * 2.2, moving ? 1 : 0);
    picker.reach(ph.picking >= 0 ? Math.sin(Math.PI * clamp(ph.picking)) : 0);
    pickerTag.mesh.lookAt(camera.position);
    const pickedA = walkT > A1 + HOLD * 0.5, pickedB = walkT > B1 + HOLD * 0.5;
    markA.ring.visible = markA.tag.mesh.visible = p > 0.5 && p < 0.7 && !pickedA;
    markB.ring.visible = markB.tag.mesh.visible = p > 0.5 && p < 0.7 && !pickedB;
    markA.ring.scale.setScalar(1 + Math.sin(time * 5) * 0.08); markB.ring.scale.setScalar(1 + Math.sin(time * 5 + 1) * 0.08);
    [markA.tag.mesh, markB.tag.mesh].forEach(m => m.lookAt(camera.position));
    const toteWorld = new THREE.Vector3(); tote.getWorldPosition(toteWorld);

    // items: shelf → tote → packing table → into the box
    const packIn = seg(p, 0.745, 0.775);
    const place = (item: THREE.Mesh, bin: THREE.Vector3, picked: boolean, pickT: number, slot: number) => {
      if (p < 0.7) {
        if (!picked) {
          const k = clamp(pickT);
          item.position.copy(bin).lerp(toteWorld.clone().add(v(0, 0.35 + slot * 0.3, 0)), k > 0 && ph.picking >= 0 ? ease(k) : 0);
        } else item.position.copy(toteWorld).add(v(slot * 0.2 - 0.1, 0.32 + slot * 0.32, 0));
      } else {
        const onTable = PACK_AT.clone().add(v(-1.6 + slot * 0.7, 0.22, 0.6));
        const inBox = PACK_AT.clone().add(v(slot * 0.3 - 0.15, 0.22 + slot * 0.35, 0));
        const arrive = ease(seg(p, 0.7, 0.72));
        if (packIn <= 0) item.position.copy(toteWorld.clone().add(v(0, 0.35 + slot * 0.3, 0))).lerp(onTable, arrive);
        else item.position.copy(onTable).lerp(inBox, ease(clamp(packIn * 2 - slot)));
      }
      item.visible = p < 0.79;
    };
    const pickTA = (walkT - A1) / HOLD, pickTB = (walkT - B1) / HOLD;
    place(itemA, BIN_A, pickedA, pickTA, 0);
    place(itemB, BIN_B, pickedB, pickTB, 1);
    if (walkT >= 1) { pickerRig.rotation.y = 0; picker.walk(0, 0); }   // parked by the packing table

    // chapter 6: predicted box, packed, taped, labelled
    const mk = Math.round(seg(p, 0.71, 0.8) * 10) / 10;
    if (mk !== monStep) { paintMonitor(mk); monStep = mk; }
    monitor.mat.opacity = seg(p, 0.69, 0.71);
    (ghost.material as THREE.LineBasicMaterial).opacity = seg(p, 0.715, 0.73) * (1 - seg(p, 0.74, 0.75));
    const boxIn = ease(seg(p, 0.725, 0.745));
    const close = ease(seg(p, 0.775, 0.79));
    pkg.close(close);
    const labelOn = ease(seg(p, 0.795, 0.81));
    // the parcel rides the conveyor to the truck once labelled
    const ride = ease(seg(p, 0.83, 0.9));
    const loadIn = ease(seg(p, 0.9, 0.925));
    const convEnd = v(DX - 0.6, 1.02, 0);
    const inTruck = TRUCK_DOCK.clone().add(v(-2.6, 0.62, 0.4));
    const pkgPos = PACK_AT.clone().add(v(0, (1 - boxIn) * 2.2, 0));
    if (p > 0.82) pkgPos.copy(PACK_AT).lerp(CONV_START, ease(seg(p, 0.82, 0.83))).lerp(convEnd, ride).lerp(inTruck, loadIn);
    pkg.g.position.copy(pkgPos);
    pkg.g.visible = p > 0.72;
    pkg.g.scale.setScalar(lerp(0.6, 1, boxIn));
    const labelTop = pkgPos.clone().add(v(0.15, pkg.h + 0.035, 0.05));
    label.mesh.position.copy(v(PX + 1.6, 1.58, -0.3)).lerp(labelTop, labelOn);
    label.mesh.visible = p > 0.79;
    label.mesh.scale.setScalar(pkg.g.scale.x);
    labelPaper.visible = p > 0.785 && p < 0.795;
    packer.reach(p > 0.74 && p < 0.81 ? Math.abs(Math.sin(time * 4)) * 0.6 : 0);

    // chapter 7: truck backs in, loads, door shuts, drives away
    const backIn = ease(seg(p, 0.83, 0.87));
    const doorUp = seg(p, 0.86, 0.875) * (1 - seg(p, 0.93, 0.945));
    const drive = seg(p, 0.95, 1);
    const truckX = lerp(DX + 26, TRUCK_DOCK.x + 0.15, backIn) + Math.pow(drive, 1.6) * 70;
    van.g.position.set(truckX, 0, 0);
    van.open(doorUp);
    van.roll(-truckX);
    const doorOpen = seg(p, 0.85, 0.87) * (1 - seg(p, 0.945, 0.96));
    dockDoor.scale.y = Math.max(0.04, 1 - doorOpen);           // the roller door rolls up into the header
    dockDoor.position.y = 4.1 - 2.0 * dockDoor.scale.y;
    extra.forEach((e, i) => {
      const k = ease(seg(p, 0.86 + i * 0.012, 0.9 + i * 0.012));
      e.position.copy(CONV_START.clone().add(v(1.2 + i * 1.4, 0.35, 0))).lerp(convEnd.clone().setY(1.37), k);
      if (p > 0.9 + i * 0.012) e.position.copy(convEnd).lerp(TRUCK_DOCK.clone().add(v(-3.4 + i * 0.6, 0.86, -0.6 + (i % 2) * 1.1)), ease(seg(p, 0.9 + i * 0.012, 0.92 + i * 0.012)));
      e.visible = p > 0.84 && p < 0.95;
    });
    if (p >= 0.95) pkg.g.visible = false;

    // camera and the light that follows it
    shotAt(p);
    camera.position.copy(camPos);
    camera.lookAt(camLook);
    sun.position.copy(camLook).add(v(-12, 24, 14));
    sun.target.position.copy(camLook);
  };

  // ------------------------------------------------------------------ sizing, loop

  const resize = () => {
    const w = host.clientWidth || 1, h = host.clientHeight || 1;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.fov = w / h < 0.8 ? 58 : w / h < 1.2 ? 46 : 38;
    // on tall screens the captions take the lower half, so the picture is framed higher
    if (w / h < 0.8) camera.setViewOffset(w, h, 0, h * 0.2, w, h); else camera.clearViewOffset();
    pullBack = w / h < 0.8 ? 1.45 : w / h < 1.2 ? 1.15 : 1;
    camera.updateProjectionMatrix();
  };
  resize();
  const ro = new ResizeObserver(resize);
  ro.observe(host);

  let target = 0, current = 0, running = false, raf = 0, visible = false;
  const t0 = performance.now();
  const frame = () => {
    current += (target - current) * (opts.reduced ? 1 : 0.14);
    if (Math.abs(target - current) < 0.0001) current = target;
    update(current, (performance.now() - t0) / 1000);
    renderer.render(scene, camera);
  };
  const loop = () => { if (!running) return; frame(); raf = requestAnimationFrame(loop); };
  const start = () => { if (running) return; running = true; raf = requestAnimationFrame(loop); };
  const stop = () => { running = false; cancelAnimationFrame(raf); };
  const io = new IntersectionObserver(([e]) => { visible = e.isIntersecting; visible && !document.hidden ? start() : stop(); }, { rootMargin: '100px' });
  io.observe(host);
  const onVis = () => (document.hidden ? stop() : visible && start());
  document.addEventListener('visibilitychange', onVis);
  frame();

  return {
    setProgress: p => { target = clamp(p); if (!running) frame(); },
    destroy: () => {
      stop(); io.disconnect(); ro.disconnect(); document.removeEventListener('visibilitychange', onVis);
      pmrem.dispose(); env.dispose(); renderer.dispose(); renderer.domElement.remove();
    },
  };
}
