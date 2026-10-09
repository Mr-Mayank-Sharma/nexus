import { forwardRef, useImperativeHandle, useMemo, useRef, useState } from 'react'
import { Canvas, useFrame, type ThreeEvent } from '@react-three/fiber'
import { Html, OrbitControls } from '@react-three/drei'
import * as THREE from 'three'
import type { OrbitControls as OrbitControlsImpl } from 'three-stdlib'
import type { SceneLayout, TruckLayout, ForkliftLayout, DockLayout, HallLayout, Vec3 } from './layout'

export type Selection =
  | { kind: 'site' }
  | { kind: 'dock'; id: string }
  | { kind: 'truck'; id: string }
  | { kind: 'equipment'; id: string }
  | null

export interface SceneHandle {
  zoom: (factor: number) => void
  rotate: (radians: number) => void
  reset: () => void
}

interface Props {
  layout: SceneLayout
  siteCode: string
  selection: Selection
  onSelect: (s: Selection) => void
  reducedMotion: boolean
}

const PALETTE = {
  ground: '#e8edf5',
  lot: '#f1f4f9',
  road: '#d9e0eb',
  roadLine: '#ffffff',
  wall: '#f4f7fc',
  wallShade: '#e3e9f3',
  roof: '#2563eb',
  roofDark: '#1d4fd8',
  door: '#c7d2e3',
  doorActive: '#1e40af',
  paint: '#f5c542',
  tree: '#7cc59a',
  treeDark: '#5fae82',
  trunk: '#a3a8b3',
  cardboard: '#d8a565',
  cardboardDark: '#c48f4f',
  pallet: '#b98a5a',
  blueCrate: '#3b82f6',
  forklift: '#f5b301',
  selection: '#2563eb',
}

const CARRIER_COLORS = ['#2563eb', '#f97316', '#14b8a6', '#8b5cf6', '#ef4444', '#0ea5e9']

function carrierColor(carrier: string | null): string {
  if (!carrier) return CARRIER_COLORS[0]
  let h = 0
  for (const c of carrier) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return CARRIER_COLORS[h % CARRIER_COLORS.length]
}

// ------------------------------------------------------------------ static scenery

function Ground({ layout }: { layout: SceneLayout }) {
  const { minX, maxX, minZ, maxZ } = layout.bounds
  const width = maxX - minX + 140
  const depth = maxZ - minZ + 80
  const cx = (minX + maxX) / 2
  const dashes = useMemo(() => {
    const out: number[] = []
    for (let x = minX - 60; x < maxX + 70; x += 6) out.push(x)
    return out
  }, [minX, maxX])
  return (
    <group>
      <mesh rotation-x={-Math.PI / 2} position={[cx, -0.02, (minZ + maxZ) / 2]} receiveShadow>
        <planeGeometry args={[width, depth]} />
        <meshStandardMaterial color={PALETTE.ground} />
      </mesh>
      {/* Paved lot in front of the building */}
      <mesh rotation-x={-Math.PI / 2} position={[cx, 0, layout.front + 12]} receiveShadow>
        <planeGeometry args={[maxX - minX, 26]} />
        <meshStandardMaterial color={PALETTE.lot} />
      </mesh>
      {/* Approach road */}
      <mesh rotation-x={-Math.PI / 2} position={[cx + 30, 0.01, layout.roadZ]} receiveShadow>
        <planeGeometry args={[width + 60, 9]} />
        <meshStandardMaterial color={PALETTE.road} />
      </mesh>
      {dashes.map(x => (
        <mesh key={x} rotation-x={-Math.PI / 2} position={[x, 0.03, layout.roadZ]}>
          <planeGeometry args={[2.6, 0.25]} />
          <meshBasicMaterial color={PALETTE.roadLine} />
        </mesh>
      ))}
      {/* Driveway from road to gate */}
      <mesh rotation-x={-Math.PI / 2} position={[layout.gate[0], 0.015, (layout.front + layout.roadZ) / 2 + 2]}>
        <planeGeometry args={[8, layout.roadZ - layout.front - 4]} />
        <meshStandardMaterial color={PALETTE.road} />
      </mesh>
    </group>
  )
}

function Tree({ position, scale = 1 }: { position: Vec3; scale?: number }) {
  return (
    <group position={position} scale={scale}>
      <mesh position={[0, 0.9, 0]} castShadow>
        <cylinderGeometry args={[0.12, 0.16, 1.8, 6]} />
        <meshStandardMaterial color={PALETTE.trunk} />
      </mesh>
      <mesh position={[0, 2.4, 0]} castShadow>
        <icosahedronGeometry args={[1.1, 0]} />
        <meshStandardMaterial color={PALETTE.tree} flatShading />
      </mesh>
      <mesh position={[0.3, 3.2, 0.1]} castShadow>
        <icosahedronGeometry args={[0.7, 0]} />
        <meshStandardMaterial color={PALETTE.treeDark} flatShading />
      </mesh>
    </group>
  )
}

function Trees({ layout }: { layout: SceneLayout }) {
  const trees = useMemo(() => {
    const out: { p: Vec3; s: number }[] = []
    const { minX, maxX } = layout.bounds
    for (let x = minX; x < maxX; x += 7) {
      out.push({ p: [x, 0, layout.roadZ - 6.5], s: 0.85 + ((x * 13) % 5) / 20 })
      out.push({ p: [x + 3, 0, -layout.front - 6], s: 0.9 + ((x * 7) % 5) / 20 })
    }
    return out.filter(t => Math.abs(t.p[0] - layout.gate[0]) > 6)
  }, [layout])
  return <>{trees.map((t, i) => <Tree key={i} position={t.p} scale={t.s} />)}</>
}

function Fence({ layout }: { layout: SceneLayout }) {
  const posts = useMemo(() => {
    const out: number[] = []
    for (let x = layout.bounds.minX; x < layout.bounds.maxX; x += 3) {
      if (Math.abs(x - layout.gate[0]) > 5) out.push(x)
    }
    return out
  }, [layout])
  return (
    <group>
      {posts.map(x => (
        <mesh key={x} position={[x, 0.6, layout.roadZ - 5]}>
          <boxGeometry args={[0.08, 1.2, 0.08]} />
          <meshStandardMaterial color="#b8c2d3" />
        </mesh>
      ))}
      <mesh position={[(layout.bounds.minX + layout.bounds.maxX) / 2, 1.05, layout.roadZ - 5]}>
        <boxGeometry args={[layout.bounds.maxX - layout.bounds.minX, 0.06, 0.04]} />
        <meshStandardMaterial color="#b8c2d3" />
      </mesh>
    </group>
  )
}

// ------------------------------------------------------------------ building

function Hall({ hall, front, onClick }: { hall: HallLayout; front: number; onClick: (e: ThreeEvent<MouseEvent>) => void }) {
  const z = front - hall.depth / 2
  const ribs = useMemo(() => {
    const out: number[] = []
    for (let x = -hall.width / 2 + 0.6; x < hall.width / 2; x += 1.2) out.push(x)
    return out
  }, [hall.width])
  const roofTilt = 0.22
  const half = hall.depth / 2
  const roofLen = half / Math.cos(roofTilt) + 0.3
  return (
    <group position={[hall.x, 0, z]} onClick={onClick}>
      <mesh position={[0, hall.height / 2, 0]} castShadow receiveShadow>
        <boxGeometry args={[hall.width, hall.height, hall.depth]} />
        <meshStandardMaterial color={PALETTE.wall} />
      </mesh>
      {/* Blue base band on the front wall */}
      <mesh position={[0, 0.6, half + 0.02]}>
        <planeGeometry args={[hall.width, 1.2]} />
        <meshStandardMaterial color={PALETTE.roof} />
      </mesh>
      {/* Gabled roof: two tilted panels plus corrugation ribs */}
      {[1, -1].map(side => (
        <group key={side} position={[0, hall.height + Math.sin(roofTilt) * half / 2, (side * half) / 2]} rotation-x={side * roofTilt}>
          <mesh castShadow>
            <boxGeometry args={[hall.width + 0.4, 0.18, roofLen]} />
            <meshStandardMaterial color={PALETTE.roof} />
          </mesh>
          {ribs.map(x => (
            <mesh key={x} position={[x, 0.12, 0]}>
              <boxGeometry args={[0.12, 0.08, roofLen]} />
              <meshStandardMaterial color={PALETTE.roofDark} />
            </mesh>
          ))}
        </group>
      ))}
      <Html position={[0, hall.height + 2.4, 0]} center distanceFactor={60} zIndexRange={[10, 0]}>
        <div className="pointer-events-none whitespace-nowrap rounded-md bg-white/80 px-2 py-0.5 text-[10px] font-medium text-slate-500 shadow-sm backdrop-blur">
          {hall.label}
        </div>
      </Html>
    </group>
  )
}

function Dock({ dock, selected, onSelect }: { dock: DockLayout; selected: boolean; onSelect: () => void }) {
  const [hover, setHover] = useState(false)
  return (
    <group
      position={dock.position}
      onClick={e => { e.stopPropagation(); onSelect() }}
      onPointerOver={e => { e.stopPropagation(); setHover(true); document.body.style.cursor = 'pointer' }}
      onPointerOut={() => { setHover(false); document.body.style.cursor = '' }}
    >
      {/* Door frame + roll-up door */}
      <mesh position={[0, 2.1, 0.06]}>
        <boxGeometry args={[3.6, 4.2, 0.1]} />
        <meshStandardMaterial color={dock.occupied ? PALETTE.doorActive : PALETTE.door} />
      </mesh>
      <mesh position={[0, 4.35, 0.12]}>
        <boxGeometry args={[4, 0.18, 0.2]} />
        <meshStandardMaterial color={PALETTE.roofDark} />
      </mesh>
      {/* Status light */}
      <mesh position={[2.1, 3.8, 0.2]}>
        <sphereGeometry args={[0.16, 12, 12]} />
        <meshStandardMaterial
          color={dock.occupied ? '#22c55e' : '#94a3b8'}
          emissive={dock.occupied ? '#22c55e' : '#000000'}
          emissiveIntensity={dock.occupied ? 1.4 : 0}
        />
      </mesh>
      {/* Bay paint */}
      {[-2.2, 2.2].map(x => (
        <mesh key={x} rotation-x={-Math.PI / 2} position={[x, 0.03, 6]}>
          <planeGeometry args={[0.14, 11]} />
          <meshBasicMaterial color={PALETTE.paint} />
        </mesh>
      ))}
      {(selected || hover) && <SelectionBox size={[4.6, 4.8, 1.2]} offset={[0, 2.2, 0.3]} />}
      <Html position={[0, 5.4, 0.4]} center distanceFactor={55} zIndexRange={[10, 0]}>
        <div className="pointer-events-none rounded bg-white/90 px-1.5 py-0.5 text-[9px] font-semibold text-slate-600 shadow-sm">
          {dock.dock.doorNumber ?? 'Door'}
        </div>
      </Html>
    </group>
  )
}

// ------------------------------------------------------------------ vehicles + goods

function SelectionBox({ size, offset = [0, 0, 0] }: { size: Vec3; offset?: Vec3 }) {
  const geometry = useMemo(() => new THREE.EdgesGeometry(new THREE.BoxGeometry(...size)), [size])
  return (
    <lineSegments geometry={geometry} position={offset}>
      <lineBasicMaterial color={PALETTE.selection} linewidth={2} />
    </lineSegments>
  )
}

function Pin({ position, label, sub }: { position: Vec3; label: string; sub?: string }) {
  return (
    <group position={position}>
      <mesh position={[0, 1.4, 0]}>
        <sphereGeometry args={[0.55, 20, 20]} />
        <meshStandardMaterial color={PALETTE.selection} />
      </mesh>
      <mesh position={[0, 0.7, 0]} rotation-x={Math.PI}>
        <coneGeometry args={[0.45, 1, 20]} />
        <meshStandardMaterial color={PALETTE.selection} />
      </mesh>
      <Html position={[0, 2.6, 0]} center distanceFactor={50} zIndexRange={[20, 10]}>
        <div className="pointer-events-none whitespace-nowrap rounded-md bg-blue-600 px-2 py-0.5 text-[10px] font-semibold text-white shadow-lg">
          {label}{sub ? <span className="ml-1 font-normal text-blue-100">{sub}</span> : null}
        </div>
      </Html>
    </group>
  )
}

function Truck({ t, selected, onSelect, reducedMotion }: { t: TruckLayout; selected: boolean; onSelect: () => void; reducedMotion: boolean }) {
  const ref = useRef<THREE.Group>(null)
  const [hover, setHover] = useState(false)
  const color = carrierColor(t.truck.carrier)
  const target = useMemo(() => new THREE.Vector3(...t.position), [t.position])

  // Ease toward the server-derived position whenever a refresh moves the truck.
  useFrame((_, delta) => {
    if (!ref.current) return
    if (reducedMotion) { ref.current.position.copy(target); return }
    ref.current.position.lerp(target, Math.min(1, delta * 1.5))
  })

  return (
    <group
      ref={ref}
      position={t.position}
      rotation-y={t.rotationY}
      onClick={e => { e.stopPropagation(); onSelect() }}
      onPointerOver={e => { e.stopPropagation(); setHover(true); document.body.style.cursor = 'pointer' }}
      onPointerOut={() => { setHover(false); document.body.style.cursor = '' }}
    >
      {/* Trailer (rear toward -z, i.e. into the door when docked) */}
      <mesh position={[0, 2, -1.2]} castShadow>
        <boxGeometry args={[2.5, 2.8, 7.2]} />
        <meshStandardMaterial color="#ffffff" />
      </mesh>
      <mesh position={[1.26, 2.2, -1.2]}>
        <planeGeometry args={[7, 0.5]} />
        <meshStandardMaterial color={color} side={THREE.DoubleSide} />
      </mesh>
      <mesh position={[-1.26, 2.2, -1.2]} rotation-y={Math.PI}>
        <planeGeometry args={[7, 0.5]} />
        <meshStandardMaterial color={color} side={THREE.DoubleSide} />
      </mesh>
      {/* Cab */}
      <mesh position={[0, 1.55, 3.4]} castShadow>
        <boxGeometry args={[2.4, 2.1, 1.9]} />
        <meshStandardMaterial color={color} />
      </mesh>
      <mesh position={[0, 2.05, 4.36]}>
        <planeGeometry args={[2, 0.8]} />
        <meshStandardMaterial color="#1e293b" />
      </mesh>
      {/* Wheels */}
      {[-3.6, -2.4, 0.6, 3.4].map(z => [-1.15, 1.15].map(x => (
        <mesh key={`${x}${z}`} position={[x, 0.45, z]} rotation-z={Math.PI / 2}>
          <cylinderGeometry args={[0.45, 0.45, 0.35, 14]} />
          <meshStandardMaterial color="#1f2937" />
        </mesh>
      )))}
      {(selected || hover) && <SelectionBox size={[3.1, 3.6, 9.8]} offset={[0, 1.8, 0]} />}
      {selected && <Pin position={[0, 3.6, -1]} label={t.truck.label ?? 'Truck'} sub={t.truck.status ?? undefined} />}
    </group>
  )
}

function Forklift({ f, selected, onSelect, reducedMotion }: { f: ForkliftLayout; selected: boolean; onSelect: () => void; reducedMotion: boolean }) {
  const ref = useRef<THREE.Group>(null)
  const [hover, setHover] = useState(false)
  const phase = useMemo(() => (f.equipment.id.charCodeAt(0) % 10) / 3, [f.equipment.id])
  const home = useMemo(() => new THREE.Vector3(...f.home), [f.home])
  const target = useMemo(() => (f.target ? new THREE.Vector3(...f.target) : null), [f.target])

  // Busy lifts shuttle between staging and the door they are working; idle lifts stay parked.
  useFrame(({ clock }) => {
    if (!ref.current) return
    if (!target || reducedMotion) { ref.current.position.copy(home); return }
    const k = (Math.sin(clock.elapsedTime * 0.6 + phase) + 1) / 2
    ref.current.position.lerpVectors(home, target, k)
  })

  return (
    <group
      ref={ref}
      position={f.home}
      rotation-y={Math.PI}
      onClick={e => { e.stopPropagation(); onSelect() }}
      onPointerOver={e => { e.stopPropagation(); setHover(true); document.body.style.cursor = 'pointer' }}
      onPointerOut={() => { setHover(false); document.body.style.cursor = '' }}
    >
      <mesh position={[0, 0.55, 0]} castShadow>
        <boxGeometry args={[1.1, 0.8, 1.6]} />
        <meshStandardMaterial color={PALETTE.forklift} />
      </mesh>
      <mesh position={[0, 1.35, -0.2]} castShadow>
        <boxGeometry args={[1, 0.08, 1]} />
        <meshStandardMaterial color="#1f2937" />
      </mesh>
      {[-0.45, 0.45].map(x => (
        <mesh key={x} position={[x, 1.0, -0.6]}>
          <boxGeometry args={[0.07, 0.9, 0.07]} />
          <meshStandardMaterial color="#1f2937" />
        </mesh>
      ))}
      <mesh position={[0, 1.1, 0.95]}>
        <boxGeometry args={[0.9, 1.8, 0.12]} />
        <meshStandardMaterial color="#334155" />
      </mesh>
      {[-0.25, 0.25].map(x => (
        <mesh key={x} position={[x, 0.18, 1.45]}>
          <boxGeometry args={[0.12, 0.06, 1]} />
          <meshStandardMaterial color="#475569" />
        </mesh>
      ))}
      {(selected || hover) && <SelectionBox size={[1.6, 2.2, 3]} offset={[0, 1, 0.3]} />}
      {selected && <Pin position={[0, 2.2, 0]} label={f.equipment.code ?? 'Forklift'} sub={f.equipment.status ?? undefined} />}
    </group>
  )
}

function Pallet({ position, tint }: { position: Vec3; tint: 'cardboard' | 'blue' }) {
  const box = tint === 'blue' ? PALETTE.blueCrate : PALETTE.cardboard
  return (
    <group position={position}>
      <mesh position={[0, 0.08, 0]} castShadow>
        <boxGeometry args={[1.2, 0.16, 1.2]} />
        <meshStandardMaterial color={PALETTE.pallet} />
      </mesh>
      {[0, 1].map(level => [-0.28, 0.28].map(x => (
        <mesh key={`${level}${x}`} position={[x, 0.42 + level * 0.52, 0]} castShadow>
          <boxGeometry args={[0.54, 0.5, 1.08]} />
          <meshStandardMaterial color={level === 1 && x > 0 && tint !== 'blue' ? PALETTE.cardboardDark : box} />
        </mesh>
      )))}
    </group>
  )
}

// ------------------------------------------------------------------ camera rig

const HOME_OFFSET = new THREE.Vector3(46, 44, 62)

function CameraRig({ focus }: { focus: THREE.Vector3 | null }) {
  // Only nudges the orbit target toward a newly selected object; user input stays in control.
  const last = useRef<string>('')
  useFrame(({ controls }) => {
    const c = controls as unknown as OrbitControlsImpl | undefined
    if (!c || !focus) return
    const key = focus.toArray().join(',')
    if (last.current !== key && c.target.distanceTo(focus) < 0.05) last.current = key
    if (last.current === key) return
    c.target.lerp(focus, 0.08)
    c.update()
  })
  return null
}

// ------------------------------------------------------------------ scene

export const WarehouseScene = forwardRef<SceneHandle, Props>(function WarehouseScene(
  { layout, siteCode, selection, onSelect, reducedMotion }, ref,
) {
  const controls = useRef<OrbitControlsImpl>(null)

  useImperativeHandle(ref, () => ({
    zoom(factor) {
      const c = controls.current
      if (!c) return
      const offset = c.object.position.clone().sub(c.target).multiplyScalar(factor)
      c.object.position.copy(c.target.clone().add(offset))
      c.update()
    },
    rotate(radians) {
      const c = controls.current
      if (!c) return
      const offset = c.object.position.clone().sub(c.target)
      offset.applyAxisAngle(new THREE.Vector3(0, 1, 0), radians)
      c.object.position.copy(c.target.clone().add(offset))
      c.update()
    },
    reset() {
      const c = controls.current
      if (!c) return
      c.target.set(0, 0, layout.front)
      c.object.position.copy(new THREE.Vector3(0, 0, layout.front).add(HOME_OFFSET))
      c.update()
    },
  }), [layout.front])

  const focus = useMemo(() => {
    if (!selection) return null
    if (selection.kind === 'truck') {
      const t = layout.trucks.find(x => x.truck.id === selection.id)
      return t ? new THREE.Vector3(...t.position) : null
    }
    if (selection.kind === 'dock') {
      const d = layout.docks.find(x => x.dock.id === selection.id)
      return d ? new THREE.Vector3(...d.position) : null
    }
    if (selection.kind === 'equipment') {
      const f = layout.forklifts.find(x => x.equipment.id === selection.id)
      return f ? new THREE.Vector3(...f.home) : null
    }
    return new THREE.Vector3(0, 0, layout.front)
  }, [selection, layout])

  return (
    <Canvas
      shadows
      dpr={[1, 2]}
      camera={{ position: [HOME_OFFSET.x, HOME_OFFSET.y, layout.front + HOME_OFFSET.z], fov: 30, near: 1, far: 800 }}
      onPointerMissed={() => onSelect(null)}
      gl={{ antialias: true, preserveDrawingBuffer: false }}
    >
      <color attach="background" args={['#eef2f8']} />
      <fog attach="fog" args={['#eef2f8', 140, 320]} />
      <hemisphereLight args={['#ffffff', '#cbd5e1', 0.9]} />
      <directionalLight
        position={[40, 70, 30]}
        intensity={1.35}
        castShadow
        shadow-mapSize={[2048, 2048]}
        shadow-camera-left={-90}
        shadow-camera-right={90}
        shadow-camera-top={90}
        shadow-camera-bottom={-90}
      />

      <Ground layout={layout} />
      <Trees layout={layout} />
      <Fence layout={layout} />

      {layout.halls.map((hall, i) => (
        <Hall key={i} hall={hall} front={layout.front} onClick={e => { e.stopPropagation(); onSelect({ kind: 'site' }) }} />
      ))}
      <Html position={[layout.halls[0]?.x ?? 0, 3.5, layout.front + 0.2]} center distanceFactor={45} zIndexRange={[10, 0]}>
        <div className="pointer-events-none rounded-full bg-blue-600 px-2.5 py-1 text-[10px] font-bold tracking-wide text-white shadow">
          {siteCode}
        </div>
      </Html>

      {layout.docks.map(d => (
        <Dock key={d.dock.id} dock={d} selected={selection?.kind === 'dock' && selection.id === d.dock.id}
              onSelect={() => onSelect({ kind: 'dock', id: d.dock.id })} />
      ))}

      {layout.yardSlots.map((y, i) => (
        <group key={i} position={y.position}>
          {[-1.6, 1.6].map(x => (
            <mesh key={x} rotation-x={-Math.PI / 2} position={[x, 0.03, 0]}>
              <planeGeometry args={[0.12, 10]} />
              <meshBasicMaterial color={PALETTE.paint} />
            </mesh>
          ))}
          <Html position={[0, 0.2, 5.6]} center distanceFactor={60} zIndexRange={[10, 0]}>
            <div className="pointer-events-none text-[9px] font-semibold text-slate-400">{y.label}</div>
          </Html>
        </group>
      ))}

      {layout.pallets.map(p => <Pallet key={p.key} position={p.position} tint={p.tint} />)}

      {layout.trucks.map(t => (
        <Truck key={t.truck.id} t={t} reducedMotion={reducedMotion}
               selected={selection?.kind === 'truck' && selection.id === t.truck.id}
               onSelect={() => onSelect({ kind: 'truck', id: t.truck.id })} />
      ))}

      {layout.forklifts.map(f => (
        <Forklift key={f.equipment.id} f={f} reducedMotion={reducedMotion}
                  selected={selection?.kind === 'equipment' && selection.id === f.equipment.id}
                  onSelect={() => onSelect({ kind: 'equipment', id: f.equipment.id })} />
      ))}

      <OrbitControls
        ref={controls}
        makeDefault
        target={[0, 0, layout.front]}
        enableDamping
        dampingFactor={0.08}
        minDistance={25}
        maxDistance={220}
        minPolarAngle={0.35}
        maxPolarAngle={1.2}
        screenSpacePanning={false}
      />
      <CameraRig focus={focus} />
    </Canvas>
  )
})
