/**
 * Region graph for 13-territory world map — data lives in `src/mapRegions.ts`.
 */
import {
  MAP_REGION_COUNT,
  getRegionNeighbors,
  regionGraphDistance,
} from "../src/mapRegions.js";

export { MAP_REGION_COUNT, getRegionNeighbors, regionGraphDistance };

/** @deprecated use MAP_REGION_COUNT */
export const MAP_HEX_COUNT = MAP_REGION_COUNT;

/** Same as getRegionNeighbors — kept for call sites still named “hex”. */
export const getHexNeighbors = getRegionNeighbors;

export function pickCapitalRegionIds(playerCount: number): number[] {
  const n = Math.max(1, Math.min(4, Math.floor(playerCount)));
  if (n === 1) return [6];
  const picked: number[] = [0];
  while (picked.length < n) {
    let best = -1;
    let bestMin = -1;
    for (let h = 0; h < MAP_REGION_COUNT; h++) {
      if (picked.includes(h)) continue;
      const minD = Math.min(...picked.map((p) => regionGraphDistance(p, h)));
      if (minD > bestMin) {
        bestMin = minD;
        best = h;
      }
    }
    if (best >= 0) picked.push(best);
    else {
      for (let h = 0; h < MAP_REGION_COUNT; h++) {
        if (!picked.includes(h)) {
          picked.push(h);
          break;
        }
      }
    }
  }
  return picked;
}

export const pickCapitalHexIds = pickCapitalRegionIds;
