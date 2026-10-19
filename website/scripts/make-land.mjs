// Builds src/data/land.json: land dots on a lat/lon grid from Natural Earth (public domain, via world-atlas).
// Used by the preloader map and the hero globe. Run once: node scripts/make-land.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { feature } from 'topojson-client';
import { geoContains } from 'd3-geo';

const topo = JSON.parse(readFileSync(new URL('../node_modules/world-atlas/land-110m.json', import.meta.url)));
const land = feature(topo, topo.objects.land);
const STEP = 1.6; // degrees between dots
const dots = [];
for (let lat = -58; lat <= 78; lat += STEP) {
  // keep dots evenly spaced on the sphere: fewer per row towards the poles
  const stepLon = STEP / Math.max(0.25, Math.cos((lat * Math.PI) / 180));
  for (let lon = -180; lon < 180; lon += stepLon) {
    if (geoContains(land, [lon, lat])) dots.push([+lon.toFixed(2), +lat.toFixed(2)]);
  }
}
writeFileSync(new URL('../src/data/land.json', import.meta.url), JSON.stringify(dots));
console.log(`${dots.length} land dots`);
