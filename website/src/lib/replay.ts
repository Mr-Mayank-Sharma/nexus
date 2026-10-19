/**
 * A faithful TypeScript port of Nexus's routing replay (the Java ReplayEngine and its sample data), so the website's
 * demo runs the same logic in the visitor's browser with no server. The data is invented. The test in replay.test.ts
 * pins this port to the numbers the Java engine produced for the same sample, so the two cannot drift apart unnoticed.
 */

// ---- Java's java.util.Random, so the sample is byte-for-byte the one the product uses --------------------------
export class JavaRandom {
  private seed: bigint;
  private static readonly MASK = (1n << 48n) - 1n;
  constructor(seed: number) { this.seed = (BigInt(seed) ^ 0x5deece66dn) & JavaRandom.MASK; }
  private next(bits: number): number {
    this.seed = (this.seed * 0x5deece66dn + 0xbn) & JavaRandom.MASK;
    const v = Number(this.seed >> BigInt(48 - bits));
    return v | 0;   // to signed 32-bit, as Java's int
  }
  nextInt(bound: number): number {
    if ((bound & -bound) === bound) return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    let bits: number, val: number;
    do { bits = this.next(31); val = bits % bound; } while (bits - val + (bound - 1) > 0x7fffffff);
    return val;
  }
  nextDouble(): number {
    return (Number(BigInt(this.next(26)) << 27n) + this.next(27)) * (1 / Number(1n << 53n));
  }
}

export interface Node { id: string; name: string; lat: number | null; lon: number | null; failureRate: number | null }
export interface Line { sku: string; qty: number }
export interface Order { id: string; lines: Line[]; lat: number | null; lon: number | null }
export interface Costs { perShipment: number; perKm: number; perFailedUnit: number }
export type StrategyKey = 'NEAREST_FIRST' | 'FEWEST_SHIPMENTS' | 'FAILURE_AWARE';
export interface Metrics {
  strategy: StrategyKey; label: string; orders: number; filled: number; partial: number; unfillable: number; shipments: number;
  splitOrders: number; splitRate: number; unfilledUnits: number; expectedFailedUnits: number; totalCost: number;
}
export interface Result { strategies: Metrics[]; differencesTotal: number }

export const PRIOR_FAILURE_RATE = 0.05;
export const LABELS: Record<StrategyKey, string> = {
  NEAREST_FIRST: 'Nearest location that has it',
  FEWEST_SHIPMENTS: 'Fewest shipments',
  FAILURE_AWARE: 'Fewest shipments, avoiding stores that fail picks',
};

type Stock = Map<string, Map<string, number>>;

export function sample(): { nodes: Node[]; stock: Stock; orders: Order[] } {
  const rnd = new JavaRandom(42);
  const nodeRows: [string, string, number, number, number][] = [
    ['DC-EAST', 'East distribution centre', 40.7, -74.0, 0.02],
    ['ST-NYC', 'New York store', 40.75, -73.99, 0.22],
    ['ST-BOS', 'Boston store', 42.36, -71.06, 0.04],
    ['ST-PHL', 'Philadelphia store', 39.95, -75.17, 0.06],
  ];
  const nodes: Node[] = nodeRows.map(([id, name, lat, lon, failureRate]) => ({ id, name, lat, lon, failureRate }));
  const skus = Array.from({ length: 24 }, (_, i) => `SKU-${String(i + 1).padStart(3, '0')}`);
  const stock: Stock = new Map();
  for (const sku of skus) {
    for (const n of nodeRows) {
      const dc = n[0].startsWith('DC');
      const qty = dc ? 30 + rnd.nextInt(30) : (rnd.nextInt(10) < 6 ? 1 + rnd.nextInt(6) : 0);
      if (qty > 0) { if (!stock.has(sku)) stock.set(sku, new Map()); stock.get(sku)!.set(n[0], qty); }
    }
  }
  const orders: Order[] = [];
  for (let o = 1; o <= 220; o++) {
    const lines = 1 + rnd.nextInt(3);
    const lat = 39.5 + rnd.nextDouble() * 3.5, lon = -75.5 + rnd.nextDouble() * 4.5;
    const used = new Set<string>();
    const ls: Line[] = [];
    for (let l = 0; l < lines; l++) {
      const sku = skus[rnd.nextInt(skus.length)];
      if (used.has(sku)) continue;
      used.add(sku);
      ls.push({ sku, qty: 1 + rnd.nextInt(2) });
    }
    // the Java sample formats lat/lon to 3 decimals before reading them back; do the same
    orders.push({ id: `ORD-${String(o).padStart(4, '0')}`, lines: ls, lat: Number(lat.toFixed(3)), lon: Number(lon.toFixed(3)) });
  }
  return { nodes, stock, orders };
}

const rateOf = (n: Node | undefined) => (n && n.failureRate !== null ? n.failureRate : PRIOR_FAILURE_RATE);

function distanceKm(n: Node, o: Order): number {
  if (n.lat === null || n.lon === null || o.lat === null || o.lon === null) return 0;
  const r = 6371.0, rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(o.lat - n.lat), dLon = rad(o.lon - n.lon);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(n.lat)) * Math.cos(rad(o.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
}

const avail = (stock: Stock, sku: string, node: string) => stock.get(sku)?.get(node) ?? 0;

function simulate(strategy: StrategyKey, nodes: Node[], stock: Stock, orders: Order[], c: Costs, haveDistance: boolean): { m: Metrics; byOrder: Map<string, string[]> } {
  const byId = new Map(nodes.map(n => [n.id, n]));
  const byOrder = new Map<string, string[]>();
  let filled = 0, partial = 0, unfillable = 0, shipments = 0, splitOrders = 0, unfilledUnits = 0, failed = 0, km = 0;

  for (const order of orders) {
    const dist = (n: Node) => (haveDistance && order.lat !== null && order.lon !== null && n.lat !== null && n.lon !== null ? distanceKm(n, order) : 0);
    const useDist = haveDistance && order.lat !== null && order.lon !== null;
    const ranked = [...nodes].sort((a, b) => {
      if (strategy === 'FAILURE_AWARE') {
        const d = rateOf(a) - rateOf(b);
        if (d !== 0) return d;
      }
      return useDist ? dist(a) - dist(b) : 0;
    });
    const taken = new Map<string, Map<string, number>>();
    const remaining = new Map<string, number>();
    for (const l of order.lines) remaining.set(l.sku, (remaining.get(l.sku) ?? 0) + l.qty);
    const take = (sku: string, node: string, units: number) => {
      stock.get(sku)!.set(node, (stock.get(sku)!.get(node) ?? 0) - units);
      if (!taken.has(node)) taken.set(node, new Map());
      taken.get(node)!.set(sku, (taken.get(node)!.get(sku) ?? 0) + units);
    };

    if (strategy === 'NEAREST_FIRST') {
      for (const [sku, qty] of [...remaining.entries()]) {
        let need = qty;
        for (const n of ranked) {
          if (need === 0) break;
          const use = Math.min(need, avail(stock, sku, n.id));
          if (use > 0) { take(sku, n.id, use); need -= use; }
        }
        remaining.set(sku, need);
      }
    } else {
      const whole = ranked.find(n => [...remaining.entries()].every(([sku, q]) => avail(stock, sku, n.id) >= q));
      if (whole) {
        for (const [sku, q] of remaining) take(sku, whole.id, q);
        for (const k of remaining.keys()) remaining.set(k, 0);
      } else {
        while ([...remaining.values()].some(v => v > 0)) {
          let pick: Node | null = null, bestCover = 0;
          for (const n of ranked) {
            let cover = 0;
            for (const [sku, q] of remaining) cover += Math.min(q, avail(stock, sku, n.id));
            if (cover > bestCover) { bestCover = cover; pick = n; }
          }
          if (!pick) break;
          for (const [sku, q] of remaining) {
            const use = Math.min(q, avail(stock, sku, pick.id));
            if (use > 0) { take(sku, pick.id, use); remaining.set(sku, q - use); }
          }
        }
      }
    }
    const unfilled = [...remaining.values()].reduce((a, b) => a + b, 0);
    unfilledUnits += unfilled;
    const names: string[] = [];
    for (const [nodeId, skus] of taken) {
      const n = byId.get(nodeId);
      names.push(n?.name?.trim() ? n.name : nodeId);
      failed += [...skus.values()].reduce((a, b) => a + b, 0) * rateOf(n);
      if (haveDistance && n) km += distanceKm(n, order);
    }
    byOrder.set(order.id, names);
    shipments += taken.size;
    if (taken.size > 1) splitOrders++;
    if (taken.size === 0) unfillable++; else if (unfilled > 0) partial++; else filled++;
  }
  const n = orders.length;
  const ship = shipments * c.perShipment + km * c.perKm;
  const rework = failed * c.perFailedUnit;
  const r2 = (v: number) => Math.round(v * 100) / 100;
  return {
    byOrder,
    m: { strategy, label: LABELS[strategy], orders: n, filled, partial, unfillable, shipments, splitOrders, splitRate: n ? splitOrders / n : 0, unfilledUnits, expectedFailedUnits: r2(failed), totalCost: r2(ship + rework) },
  };
}

export function run(nodes: Node[], stock: Stock, orders: Order[], costs: Costs): Result {
  const copy = (): Stock => new Map([...stock].map(([k, v]) => [k, new Map(v)]));
  const haveDistance = orders.some(o => o.lat !== null && o.lon !== null) && nodes.some(n => n.lat !== null && n.lon !== null);
  const plans = (['NEAREST_FIRST', 'FEWEST_SHIPMENTS', 'FAILURE_AWARE'] as StrategyKey[]).map(s => simulate(s, nodes, copy(), orders, costs, haveDistance));
  let diffs = 0;
  for (const o of orders) if ((plans[0].byOrder.get(o.id)?.length ?? 0) !== (plans[2].byOrder.get(o.id)?.length ?? 0)) diffs++;
  return { strategies: plans.map(p => p.m), differencesTotal: diffs };
}
