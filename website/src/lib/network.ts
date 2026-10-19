import * as THREE from 'three';
import LAND from '../data/land.json';

/**
 * The hero: a dotted sphere that is a network of warehouses and stores, with orders travelling between them.
 * The continents are real (Natural Earth); the warehouses, stores and routes on them are an illustration.
 * One draw loop, paused when the hero is off screen or the tab is hidden; a single still frame when the
 * visitor prefers reduced motion; nothing at all if WebGL is unavailable (the page keeps its CSS backdrop).
 */

type Node = { name: string; kind: 'warehouse' | 'store'; lat: number; lon: number; v: THREE.Vector3 };

const NODES: Omit<Node, 'v'>[] = [
  { name: 'Warehouse · East', kind: 'warehouse', lat: 40.7, lon: -74.0 },
  { name: 'Store · Boston', kind: 'store', lat: 42.4, lon: -71.1 },
  { name: 'Store · Philadelphia', kind: 'store', lat: 39.9, lon: -75.2 },
  { name: 'Store · Chicago', kind: 'store', lat: 41.9, lon: -87.6 },
  { name: 'Warehouse · West', kind: 'warehouse', lat: 34.0, lon: -118.2 },
  { name: 'Store · Houston', kind: 'store', lat: 29.8, lon: -95.4 },
  { name: 'Store · London', kind: 'store', lat: 51.5, lon: -0.1 },
  { name: 'Warehouse · Rotterdam', kind: 'warehouse', lat: 51.9, lon: 4.5 },
  { name: 'Store · Paris', kind: 'store', lat: 48.9, lon: 2.4 },
  { name: 'Store · Mumbai', kind: 'store', lat: 19.1, lon: 72.9 },
  { name: 'Warehouse · Singapore', kind: 'warehouse', lat: 1.35, lon: 103.8 },
  { name: 'Store · Tokyo', kind: 'store', lat: 35.7, lon: 139.7 },
  { name: 'Store · Sydney', kind: 'store', lat: -33.9, lon: 151.2 },
  { name: 'Store · São Paulo', kind: 'store', lat: -23.5, lon: -46.6 },
];

// Pairs that get a route. Each is an order that could ship from one place to the other's customers.
const ROUTES: [number, number][] = [
  [0, 1], [0, 2], [0, 3], [4, 3], [4, 5], [0, 6], [7, 6], [7, 8], [6, 8], [7, 9], [10, 9], [10, 11], [10, 12], [0, 13], [4, 11], [5, 13],
];

const ll = (lat: number, lon: number, r = 1) => {
  const phi = (90 - lat) * (Math.PI / 180), theta = (lon + 180) * (Math.PI / 180);
  return new THREE.Vector3(-r * Math.sin(phi) * Math.cos(theta), r * Math.cos(phi), r * Math.sin(phi) * Math.sin(theta));
};

function glowTexture(): THREE.Texture {
  const c = document.createElement('canvas');
  c.width = c.height = 64;
  const g = c.getContext('2d')!;
  const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, 'rgba(255,255,255,1)');
  grad.addColorStop(0.25, 'rgba(255,255,255,0.55)');
  grad.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 64, 64);
  return new THREE.CanvasTexture(c);
}

function arcPoints(a: THREE.Vector3, b: THREE.Vector3, steps = 72): THREE.Vector3[] {
  const angle = a.angleTo(b), lift = 0.12 + angle * 0.22;
  const out: THREE.Vector3[] = [];
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const p = new THREE.Vector3().copy(a).lerp(b, t).normalize().multiplyScalar(1 + lift * Math.sin(Math.PI * t));
    out.push(p);
  }
  return out;
}

export interface Network { destroy(): void; setProgress(p: number): void; }

export function mountNetwork(host: HTMLElement, labelsHost: HTMLElement, opts: { reduced: boolean }): Network | null {
  let renderer: THREE.WebGLRenderer;
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
  } catch {
    return null;
  }
  const small = window.innerWidth < 760;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, small ? 1.5 : 2));
  renderer.setClearColor(0x000000, 0);
  host.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-hidden', 'true');

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 100);
  camera.position.set(0, 0, 6.2);
  const world = new THREE.Group();
  scene.add(world);

  // dotted shell: real continents (Natural Earth land, as dots) over a faint even grid for the oceans
  const landDots = (small ? LAND.filter((_, i) => i % 2 === 0) : LAND) as [number, number][];
  const ocean = small ? 1400 : 3200;
  const count = landDots.length + ocean;
  const pos = new Float32Array(count * 3), seed = new Float32Array(count), isLand = new Float32Array(count);
  landDots.forEach(([lon, lat], i) => { pos.set(ll(lat, lon).toArray(), i * 3); seed[i] = Math.random(); isLand[i] = 1; });
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let k = 0; k < ocean; k++) {
    const i = landDots.length + k, y = 1 - (k / (ocean - 1)) * 2, r = Math.sqrt(1 - y * y), th = golden * k;
    pos.set([Math.cos(th) * r, y, Math.sin(th) * r], i * 3);
    seed[i] = Math.random();
  }
  const dotGeo = new THREE.BufferGeometry();
  dotGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  dotGeo.setAttribute('aSeed', new THREE.BufferAttribute(seed, 1));
  dotGeo.setAttribute('aLand', new THREE.BufferAttribute(isLand, 1));
  const dotMat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false,
    uniforms: {
      uTime: { value: 0 }, uSize: { value: small ? 2.4 : 2.2 }, uPixelRatio: { value: renderer.getPixelRatio() },
      uA: { value: new THREE.Color('#5b8cff') }, uB: { value: new THREE.Color('#ffffff') },
    },
    vertexShader: /* glsl */ `
      uniform float uTime; uniform float uSize; uniform float uPixelRatio;
      attribute float aSeed; attribute float aLand; varying float vFresnel; varying float vFront; varying float vLand;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position * 1.0, 1.0);
        vec3 n = normalize(normalMatrix * position);
        vec3 viewDir = normalize(-mv.xyz);
        vFront = dot(n, viewDir);
        vFresnel = pow(1.0 - max(vFront, 0.0), 2.0);
        vLand = aLand;
        float twinkle = 0.75 + 0.25 * sin(uTime * 1.4 + aSeed * 6.2831);
        float size = mix(0.55, 1.15, aLand);
        gl_PointSize = uSize * size * uPixelRatio * twinkle * (0.5 + 0.5 * max(vFront, 0.0)) * (6.0 / -mv.z) * 3.2;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: /* glsl */ `
      uniform vec3 uA; uniform vec3 uB; varying float vFresnel; varying float vFront; varying float vLand;
      void main() {
        vec2 c = gl_PointCoord - 0.5; float d = length(c); if (d > 0.5) discard;
        float a = smoothstep(0.5, 0.05, d);
        float facing = smoothstep(-0.25, 0.25, vFront);
        vec3 col = mix(uA, uB, vFresnel * 0.8 + 0.15 + vLand * 0.35);
        float strength = mix(0.28, 1.0, vLand);
        gl_FragColor = vec4(col, strength * a * (0.18 + 0.82 * facing) * (0.4 + 0.6 * vFresnel + 0.18));
      }`,
  });
  world.add(new THREE.Points(dotGeo, dotMat));

  // a dark core hides everything behind the globe, so only the near side shows (and the glow is only a rim)
  world.add(new THREE.Mesh(new THREE.SphereGeometry(0.985, 64, 64), new THREE.MeshBasicMaterial({ color: 0x05070d })));

  // atmosphere: a thin glow between the globe's edge and the shell's edge, blue on one side and amber on the other
  const atmos = new THREE.Mesh(
    new THREE.SphereGeometry(1.16, 64, 64),
    new THREE.ShaderMaterial({
      transparent: true, side: THREE.BackSide, depthWrite: false, blending: THREE.AdditiveBlending,
      vertexShader: `varying vec3 vN; varying vec3 vW; void main(){ vN = normalize(normalMatrix * normal); vW = (modelMatrix * vec4(position,1.0)).xyz; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
      fragmentShader: `varying vec3 vN; varying vec3 vW; void main(){
        float t = clamp(-vN.z / 0.52, 0.0, 1.0);
        float i = pow(t, 2.2);
        vec3 blue = vec3(0.26, 0.42, 1.0); vec3 amber = vec3(1.0, 0.48, 0.2);
        vec3 c = mix(blue, amber, smoothstep(-0.4, 1.2, vW.x * 0.6 + vW.y * 0.5));
        gl_FragColor = vec4(c * i * 0.85, i);
      }`,
    }),
  );
  world.add(atmos);

  // nodes
  const nodes: Node[] = NODES.map(n => ({ ...n, v: ll(n.lat, n.lon, 1.012) }));
  const tex = glowTexture();
  const nodeGeo = new THREE.BufferGeometry();
  nodeGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(nodes.flatMap(n => n.v.toArray())), 3));
  const nodeMat = new THREE.PointsMaterial({ size: 0.2, map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, color: 0xffffff, sizeAttenuation: true });
  world.add(new THREE.Points(nodeGeo, nodeMat));
  const warehouseIdx = nodes.map((n, i) => (n.kind === 'warehouse' ? i : -1)).filter(i => i >= 0);
  const whGeo = new THREE.BufferGeometry();
  whGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(warehouseIdx.flatMap(i => nodes[i].v.toArray())), 3));
  const whMat = new THREE.PointsMaterial({ size: 0.42, map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, color: 0xff8a4c, sizeAttenuation: true });
  world.add(new THREE.Points(whGeo, whMat));

  // routes and the orders that travel them
  const curves = ROUTES.map(([a, b]) => arcPoints(nodes[a].v, nodes[b].v));
  curves.forEach((pts, i) => {
    const g = new THREE.BufferGeometry().setFromPoints(pts);
    const m = new THREE.LineBasicMaterial({ color: i % 3 === 0 ? 0xff8a4c : 0x5b8cff, transparent: true, opacity: 0.55 });
    world.add(new THREE.Line(g, m));
  });
  const pulseN = ROUTES.length * 2;
  const pulses = Array.from({ length: pulseN }, (_, i) => ({ route: i % ROUTES.length, t: Math.random(), speed: 0.07 + Math.random() * 0.09 }));
  const pPos = new Float32Array(pulseN * 3);
  const pGeo = new THREE.BufferGeometry();
  pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
  const pMat = new THREE.PointsMaterial({ size: 0.22, map: tex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, color: 0xffb27d, sizeAttenuation: true });
  world.add(new THREE.Points(pGeo, pMat));
  const stepPulses = (dt: number) => {
    pulses.forEach((p, i) => {
      p.t = Math.max(0, p.t + dt * p.speed);
      if (p.t > 1) { p.t = 0; p.route = Math.floor(Math.random() * ROUTES.length); }
      const pts = curves[p.route], f = p.t * (pts.length - 1), i0 = Math.floor(f), i1 = Math.min(pts.length - 1, i0 + 1);
      const q = new THREE.Vector3().copy(pts[i0]).lerp(pts[i1], f - i0);
      pPos.set([q.x, q.y, q.z], i * 3);
    });
    pGeo.attributes.position.needsUpdate = true;
  };

  // labels on a few nodes
  const labelled = [0, 1, 6, 10, 12, 4];
  const labels = labelled.map(i => {
    const el = document.createElement('div');
    el.className = 'net-label';
    el.textContent = nodes[i].name;
    labelsHost.appendChild(el);
    return { el, i };
  });
  const tmp = new THREE.Vector3(), nrm = new THREE.Vector3();
  const placeLabels = () => {
    const w = host.clientWidth, h = host.clientHeight;
    labels.forEach(({ el, i }) => {
      tmp.copy(nodes[i].v).applyMatrix4(world.matrixWorld);
      nrm.copy(tmp).sub(world.position).normalize();
      const facing = nrm.z > 0.3;
      tmp.project(camera);
      el.style.opacity = facing ? '1' : '0';
      el.style.transform = `translate3d(${(tmp.x * 0.5 + 0.5) * w + 14}px, ${(-tmp.y * 0.5 + 0.5) * h - 10}px, 0)`;
    });
  };

  // layout
  const place = () => {
    const w = host.clientWidth || 1, h = host.clientHeight || 1, aspect = w / h;
    renderer.setSize(w, h, false);
    camera.aspect = aspect;
    camera.updateProjectionMatrix();
    const wide = aspect > 1.15;
    world.position.set(wide ? 2.7 : 0, wide ? -0.55 : 1.3, 0);
    world.scale.setScalar(wide ? 1.85 : 1.2);
  };
  place();
  const ro = new ResizeObserver(place);
  ro.observe(host);

  // interaction and loop
  const pointer = { x: 0, y: 0, sx: 0, sy: 0 };
  const onMove = (e: PointerEvent) => { pointer.x = (e.clientX / window.innerWidth) * 2 - 1; pointer.y = (e.clientY / window.innerHeight) * 2 - 1; };
  window.addEventListener('pointermove', onMove);
  let progress = 0, running = false, raf = 0, last = performance.now(), visible = true;
  const draw = (now: number) => {
    const dt = Math.max(0, Math.min(0.05, (now - last) / 1000));   // the first frame's clock can be slightly behind
    last = now;
    pointer.sx += (pointer.x - pointer.sx) * 0.04; pointer.sy += (pointer.y - pointer.sy) * 0.04;
    world.rotation.y += dt * 0.07;
    world.rotation.x = 0.35 + pointer.sy * 0.12 - progress * 0.2;
    world.rotation.z = 0.12 + pointer.sx * 0.05;
    world.position.x += ((window.innerWidth / window.innerHeight > 1.15 ? 2.7 - progress * 1.4 : 0) - world.position.x) * 0.1;
    dotMat.uniforms.uTime.value = now / 1000;
    stepPulses(dt);
    world.updateMatrixWorld();
    placeLabels();
    renderer.render(scene, camera);
  };
  const loop = (now: number) => { if (!running) return; draw(now); raf = requestAnimationFrame(loop); };
  const start = () => { if (running || opts.reduced) return; running = true; last = performance.now(); raf = requestAnimationFrame(loop); };
  const stop = () => { running = false; cancelAnimationFrame(raf); };
  const io = new IntersectionObserver(([e]) => { visible = e.isIntersecting; visible && !document.hidden ? start() : stop(); }, { threshold: 0 });
  io.observe(host);
  const onVis = () => (document.hidden ? stop() : visible && start());
  document.addEventListener('visibilitychange', onVis);

  stepPulses(0.5);
  if (opts.reduced) { world.rotation.y = 0.9; draw(performance.now()); } else start();

  return {
    setProgress: p => { progress = Math.max(0, Math.min(1, p)); },
    destroy: () => {
      stop(); io.disconnect(); ro.disconnect();
      window.removeEventListener('pointermove', onMove); document.removeEventListener('visibilitychange', onVis);
      renderer.dispose(); renderer.domElement.remove(); labels.forEach(l => l.el.remove());
    },
  };
}
