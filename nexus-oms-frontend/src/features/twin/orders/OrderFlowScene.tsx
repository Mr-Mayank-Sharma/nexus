import { forwardRef, useImperativeHandle, useMemo, useRef, useState } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { Html, OrbitControls } from '@react-three/drei'
import * as THREE from 'three'
import type { OrbitControls as OrbitControlsImpl } from 'three-stdlib'
import { COLUMN_X, channelColor, regionLabel, type FlowLayout, type NetworkLayout, type Vec3 } from './layout'

export interface FlowSceneHandle {
  zoom: (factor: number) => void
  rotate: (radians: number) => void
  reset: () => void
}

interface Props {
  layout: NetworkLayout
  selectedKey: string | null
  onSelect: (key: string | null) => void
  reducedMotion: boolean
}

/** Steep, map-like view so stacked items read top-to-bottom like a Sankey diagram. */
function homeFor(depth: number): THREE.Vector3 {
  const d = Math.max(depth, 40)
  return new THREE.Vector3(FOCUS.x + 6, 96 + d * 1.05, FOCUS.z + 30 + d * 0.62)
}

/**
 * Orbit target, offset so the board centres in the space left free by the overlay panels
 * (inspector on the right, KPI tiles on top).
 */
const FOCUS = new THREE.Vector3(12, 0, -3)

function Label({ position, title, value, accent }: { position: Vec3; title: string; value: string; accent?: string }) {
  return (
    <Html position={position} center distanceFactor={64} zIndexRange={[10, 0]}>
      <div className="pointer-events-none whitespace-nowrap rounded-lg bg-white/90 px-2 py-1 text-center shadow-sm ring-1 ring-slate-200/70 backdrop-blur">
        <div className="text-[10px] font-semibold text-slate-700" style={accent ? { color: accent } : undefined}>{title}</div>
        <div className="text-[11px] font-bold tabular-nums text-slate-900">{value}</div>
      </div>
    </Html>
  )
}

function Board({ depth }: { depth: number }) {
  const lanes: { x: number; label: string }[] = [
    { x: COLUMN_X.channel, label: 'Sales channels' },
    { x: COLUMN_X.node, label: 'Fulfilment nodes' },
    { x: COLUMN_X.region, label: 'Ship-to regions' },
  ]
  return (
    <group>
      <mesh rotation-x={-Math.PI / 2} position={[0, -0.05, 0]} receiveShadow>
        <planeGeometry args={[400, 400]} />
        <meshStandardMaterial color="#e8edf5" />
      </mesh>
      <mesh position={[0, -0.02, 0]} receiveShadow>
        <boxGeometry args={[COLUMN_X.region - COLUMN_X.channel + 26, 0.1, depth + 18]} />
        <meshStandardMaterial color="#f4f7fb" />
      </mesh>
      {lanes.map(l => (
        <group key={l.label}>
          <mesh rotation-x={-Math.PI / 2} position={[l.x, 0.04, 0]}>
            <planeGeometry args={[14, depth + 10]} />
            <meshStandardMaterial color="#eaf0fa" />
          </mesh>
          <Html position={[l.x, 0.2, -(depth / 2) - 7]} center distanceFactor={80} zIndexRange={[10, 0]}>
            <div className="pointer-events-none whitespace-nowrap text-[12px] font-semibold uppercase tracking-[0.12em] text-slate-400">{l.label}</div>
          </Html>
        </group>
      ))}
    </group>
  )
}

function useHover() {
  const [hover, setHover] = useState(false)
  return {
    hover,
    bind: {
      onPointerOver: (e: { stopPropagation: () => void }) => { e.stopPropagation(); setHover(true); document.body.style.cursor = 'pointer' },
      onPointerOut: () => { setHover(false); document.body.style.cursor = '' },
    },
  }
}

function Ring({ radius }: { radius: number }) {
  return (
    <mesh rotation-x={-Math.PI / 2} position={[0, 0.06, 0]}>
      <ringGeometry args={[radius, radius + 0.35, 48]} />
      <meshBasicMaterial color="#2563eb" />
    </mesh>
  )
}

function ChannelPortal({ p, selected, onSelect }: { p: NetworkLayout['channels'][number]; selected: boolean; onSelect: () => void }) {
  const { hover, bind } = useHover()
  const color = channelColor(p.item.name)
  const h = 1 + p.size * 3.5
  return (
    <group position={p.position} onClick={e => { e.stopPropagation(); onSelect() }} {...bind}>
      <mesh position={[0, 0.25, 0]} castShadow>
        <cylinderGeometry args={[2.6, 2.8, 0.5, 32]} />
        <meshStandardMaterial color="#ffffff" />
      </mesh>
      <mesh position={[0, 0.5 + h / 2, 0]} castShadow>
        <boxGeometry args={[2.6, h, 2.6]} />
        <meshStandardMaterial color={color} />
      </mesh>
      <mesh position={[0, 0.5 + h + 0.15, 0]}>
        <boxGeometry args={[2.8, 0.3, 2.8]} />
        <meshStandardMaterial color="#ffffff" />
      </mesh>
      {(selected || hover) && <Ring radius={3.3} />}
      <Label position={[0, h + 2.2, 0]} title={p.item.name} value={`${p.item.orders} orders`} accent={color} />
    </group>
  )
}

function NodeBuilding({ p, selected, onSelect }: { p: NetworkLayout['nodes'][number]; selected: boolean; onSelect: () => void }) {
  const { hover, bind } = useHover()
  const type = (p.item.type ?? '').toUpperCase()
  const isStore = type.includes('STORE') && !type.includes('COLD')
  const s = 0.6 + p.size * 0.5
  return (
    <group position={p.position} scale={s} onClick={e => { e.stopPropagation(); onSelect() }} {...bind}>
      {isStore ? (
        <>
          <mesh position={[0, 1.6, 0]} castShadow>
            <boxGeometry args={[5, 3.2, 4]} />
            <meshStandardMaterial color="#ffffff" />
          </mesh>
          {/* Striped awning */}
          {[-2, -1, 0, 1, 2].map((x, i) => (
            <mesh key={x} position={[x * 1, 2.9, 2.3]} rotation-x={0.5}>
              <boxGeometry args={[1, 0.12, 1.4]} />
              <meshStandardMaterial color={i % 2 === 0 ? '#2563eb' : '#ffffff'} />
            </mesh>
          ))}
          <mesh position={[0, 1.1, 2.02]}>
            <planeGeometry args={[3.6, 1.6]} />
            <meshStandardMaterial color="#bfdbfe" />
          </mesh>
          <mesh position={[0, 3.35, 0]}>
            <boxGeometry args={[5.2, 0.3, 4.2]} />
            <meshStandardMaterial color="#1d4ed8" />
          </mesh>
        </>
      ) : (
        <>
          <mesh position={[0, 2, 0]} castShadow>
            <boxGeometry args={[7, 4, 5]} />
            <meshStandardMaterial color="#f4f7fc" />
          </mesh>
          {[1, -1].map(side => (
            <mesh key={side} position={[0, 4.35, side * 1.25]} rotation-x={side * 0.25} castShadow>
              <boxGeometry args={[7.3, 0.18, 2.7]} />
              <meshStandardMaterial color={type.includes('COLD') ? '#0ea5e9' : '#2563eb'} />
            </mesh>
          ))}
          {[-2.2, 0, 2.2].map(x => (
            <mesh key={x} position={[x, 1.1, 2.52]}>
              <planeGeometry args={[1.5, 2.2]} />
              <meshStandardMaterial color="#1e40af" />
            </mesh>
          ))}
        </>
      )}
      {(selected || hover) && <Ring radius={4.8} />}
      <Label position={[0, 6, 0]} title={p.item.code ?? 'Node'} value={`${p.item.allocations} allocations`} />
    </group>
  )
}

function RegionTile({ p, selected, onSelect }: { p: NetworkLayout['regions'][number]; selected: boolean; onSelect: () => void }) {
  const { hover, bind } = useHover()
  const h = 0.4 + p.size * 3
  return (
    <group position={p.position} onClick={e => { e.stopPropagation(); onSelect() }} {...bind}>
      <mesh position={[0, h / 2, 0]} castShadow>
        <cylinderGeometry args={[2.2, 2.2, h, 6]} />
        <meshStandardMaterial color={selected || hover ? '#2563eb' : '#93c5fd'} flatShading />
      </mesh>
      <mesh position={[0, h + 0.05, 0]}>
        <cylinderGeometry args={[2.25, 2.25, 0.1, 6]} />
        <meshStandardMaterial color="#ffffff" />
      </mesh>
      <Label position={[0, h + 2.6, 0]} title={regionLabel(p.key)} value={`${p.item.orders}`} />
    </group>
  )
}

function FlowTube({ f, dim, reducedMotion }: { f: FlowLayout; dim: boolean; reducedMotion: boolean }) {
  const curve = useMemo(() => {
    const a = new THREE.Vector3(f.from[0] + 3, 1.2, f.from[2])
    const b = new THREE.Vector3(f.to[0] - 3, 1.2, f.to[2])
    const mid = a.clone().lerp(b, 0.5)
    mid.y += 4 + Math.abs(a.z - b.z) * 0.08
    return new THREE.QuadraticBezierCurve3(a, mid, b)
  }, [f.from, f.to])
  const geometry = useMemo(() => new THREE.TubeGeometry(curve, 48, f.width, 8, false), [curve, f.width])
  const particles = Math.max(1, Math.min(8, Math.round(Math.log2(f.orders + 1) * 2)))
  const refs = useRef<(THREE.Mesh | null)[]>([])

  useFrame(({ clock }) => {
    if (reducedMotion) return
    const speed = 0.12
    refs.current.forEach((m, i) => {
      if (!m) return
      const t = (clock.elapsedTime * speed + i / particles) % 1
      m.position.copy(curve.getPoint(t))
    })
  })

  return (
    <group>
      <mesh geometry={geometry}>
        <meshStandardMaterial color={f.color} transparent opacity={dim ? 0.08 : 0.45} depthWrite={false} />
      </mesh>
      {!dim && Array.from({ length: particles }, (_, i) => (
        <mesh key={i} ref={el => { refs.current[i] = el }} position={curve.getPoint(i / particles)}>
          <boxGeometry args={[0.55, 0.4, 0.55]} />
          <meshStandardMaterial color="#d8a565" />
        </mesh>
      ))}
    </group>
  )
}

export const OrderFlowScene = forwardRef<FlowSceneHandle, Props>(function OrderFlowScene(
  { layout, selectedKey, onSelect, reducedMotion }, ref,
) {
  const controls = useRef<OrbitControlsImpl>(null)

  useImperativeHandle(ref, () => ({
    zoom(factor) {
      const c = controls.current
      if (!c) return
      c.object.position.copy(c.target.clone().add(c.object.position.clone().sub(c.target).multiplyScalar(factor)))
      c.update()
    },
    rotate(radians) {
      const c = controls.current
      if (!c) return
      const offset = c.object.position.clone().sub(c.target).applyAxisAngle(new THREE.Vector3(0, 1, 0), radians)
      c.object.position.copy(c.target.clone().add(offset))
      c.update()
    },
    reset() {
      const c = controls.current
      if (!c) return
      c.target.copy(FOCUS)
      c.object.position.copy(homeFor(layout.depth))
      c.update()
    },
  }), [layout.depth])

  // A selected/hovered entity keeps its own flows bright and dims the rest.
  const connected = useMemo(() => {
    if (!selectedKey) return null
    const keys = new Set<string>()
    for (const f of layout.flows) if (f.fromKey === selectedKey || f.toKey === selectedKey) keys.add(f.key)
    return keys
  }, [selectedKey, layout.flows])

  return (
    <Canvas shadows dpr={[1, 2]} camera={{ position: homeFor(layout.depth).toArray() as Vec3, fov: 32, near: 1, far: 900 }}
            onPointerMissed={() => onSelect(null)}>
      <color attach="background" args={['#eef2f8']} />
      <fog attach="fog" args={['#eef2f8', 220, 420]} />
      <hemisphereLight args={['#ffffff', '#cbd5e1', 0.95]} />
      <directionalLight position={[30, 80, 40]} intensity={1.3} castShadow shadow-mapSize={[2048, 2048]}
                        shadow-camera-left={-80} shadow-camera-right={80} shadow-camera-top={80} shadow-camera-bottom={-80} />
      <Board depth={layout.depth} />

      {layout.flows.map(f => (
        <FlowTube key={f.key} f={f} reducedMotion={reducedMotion} dim={connected !== null && !connected.has(f.key)} />
      ))}
      {layout.channels.map(p => (
        <ChannelPortal key={p.key} p={p} selected={selectedKey === p.key} onSelect={() => onSelect(p.key)} />
      ))}
      {layout.nodes.map(p => (
        <NodeBuilding key={p.key} p={p} selected={selectedKey === p.key} onSelect={() => onSelect(p.key)} />
      ))}
      {layout.regions.map(p => (
        <RegionTile key={p.key} p={p} selected={selectedKey === p.key} onSelect={() => onSelect(p.key)} />
      ))}

      <OrbitControls ref={controls} makeDefault target={FOCUS.toArray() as Vec3} enableDamping dampingFactor={0.08}
                     minDistance={30} maxDistance={260} minPolarAngle={0.15} maxPolarAngle={1.2} screenSpacePanning={false} />
    </Canvas>
  )
})
