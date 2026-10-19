import { describe, expect, it } from 'vitest';
import { JavaRandom, run, sample } from './replay';

describe('JavaRandom', () => {
  it('matches java.util.Random for seed 42 (values computed with the JDK)', () => {
    // Values printed by the JDK (jshell): new Random(42).nextInt(100) x5 and new Random(42).nextDouble()
    const r = new JavaRandom(42);
    expect([r.nextInt(100), r.nextInt(100), r.nextInt(100), r.nextInt(100), r.nextInt(100)]).toEqual([30, 63, 48, 84, 70]);
    const i = new JavaRandom(42);
    expect(i.nextDouble()).toBeCloseTo(0.7275636800328681, 12);   // new Random(42).nextDouble()
  });
});

describe('replay port', () => {
  it('reproduces the numbers the Java engine produced for the same sample', () => {
    const { nodes, stock, orders } = sample();
    const r = run(nodes, stock, orders, { perShipment: 6.5, perKm: 0, perFailedUnit: 6.5 });
    const [nearest, fewest, aware] = r.strategies;
    expect(orders.length).toBe(220);
    expect([nearest.shipments, nearest.splitOrders]).toEqual([265, 45]);
    expect([fewest.shipments, fewest.splitOrders]).toEqual([220, 0]);
    expect([aware.shipments, aware.splitOrders]).toEqual([220, 0]);
    expect(nearest.expectedFailedUnits).toBe(23.12);
    expect(fewest.expectedFailedUnits).toBe(20.32);
    expect(aware.expectedFailedUnits).toBe(13.18);
    expect(nearest.totalCost).toBe(1872.78);
    expect(fewest.totalCost).toBe(1562.08);
    expect(aware.totalCost).toBe(1515.67);
    expect(nearest.filled).toBe(216);
    expect(nearest.partial).toBe(4);
    expect(r.differencesTotal).toBe(45);
  });

  it('does not change the stock it is given', () => {
    const { nodes, stock, orders } = sample();
    const before = JSON.stringify([...stock].map(([k, v]) => [k, [...v]]));
    run(nodes, stock, orders, { perShipment: 6.5, perKm: 0, perFailedUnit: 6.5 });
    expect(JSON.stringify([...stock].map(([k, v]) => [k, [...v]]))).toBe(before);
  });

  it('a more reliable store lowers expected failed picks, never raises shipments', () => {
    const { nodes, stock, orders } = sample();
    const bad = nodes.map(n => (n.id === 'ST-NYC' ? { ...n, failureRate: 0.4 } : n));
    const good = nodes.map(n => (n.id === 'ST-NYC' ? { ...n, failureRate: 0.01 } : n));
    const c = { perShipment: 6.5, perKm: 0, perFailedUnit: 6.5 };
    const a = run(bad, stock, orders, c).strategies[2], b = run(good, stock, orders, c).strategies[2];
    expect(b.expectedFailedUnits).toBeLessThanOrEqual(a.expectedFailedUnits);
    expect(b.shipments).toBe(a.shipments);
  });
});
