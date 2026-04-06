/**
 * 13 territories — classic «سيف المعرفة»-style world board (original layout, not copied art).
 * Single source for names, adjacency, and SVG paths (viewBox 0 0 400 260).
 */

export const MAP_REGION_COUNT = 13;

/** Arabic territory names (one per id 0..12). */
export const TERRITORY_NAMES: readonly string[] = [
  'المغرب العربي',
  'مصر والنيل',
  'السودان والقرن',
  'بلاد الحبشة',
  'جنوب الجزيرة',
  'الساحل الأطلسي',
  'ليبيا والقفرة',
  'الشام والأردن',
  'العراق والفرات',
  'الخليج واليمن',
  'الأناضول',
  'القوقاز وما وراء النهر',
  'فارس وخراسان',
] as const;

/** Undirected adjacency — must match shared borders on the SVG roughly. */
export const REGION_NEIGHBORS: readonly number[][] = [
  [1, 5], // 0
  [0, 2, 5, 6], // 1
  [1, 3, 6, 7], // 2
  [2, 4, 7, 8], // 3
  [3, 8, 9], // 4
  [0, 1, 6], // 5
  [1, 2, 5, 7, 10], // 6
  [2, 3, 6, 8, 10, 11], // 7
  [3, 4, 7, 9, 11, 12], // 8
  [4, 8, 12], // 9
  [6, 7, 11], // 10
  [7, 8, 10, 12], // 11
  [8, 9, 11], // 12
];

export function getRegionNeighbors(id: number): number[] {
  if (id < 0 || id >= MAP_REGION_COUNT) return [];
  return [...(REGION_NEIGHBORS[id] ?? [])];
}

export function regionGraphDistance(a: number, b: number): number {
  if (a === b) return 0;
  const seen = new Set<number>([a]);
  let frontier = [a];
  let d = 0;
  while (frontier.length) {
    d++;
    const next: number[] = [];
    for (const id of frontier) {
      for (const n of getRegionNeighbors(id)) {
        if (n === b) return d;
        if (!seen.has(n)) {
          seen.add(n);
          next.push(n);
        }
      }
    }
    frontier = next;
  }
  return 999;
}

/** SVG path `d` per region — stylized world / MENA–centric board. */
export const REGION_SVG_PATHS: readonly string[] = [
  'M 18,168 L 92,158 L 98,228 L 24,238 Q 16,210 18,168 Z',
  'M 92,158 L 168,148 L 172,208 L 98,218 L 98,228 Z',
  'M 168,148 L 238,138 L 242,198 L 172,208 Z',
  'M 238,138 L 305,128 L 310,188 L 242,198 Z',
  'M 305,128 L 378,118 L 382,178 L 310,188 Z',
  'M 12,238 L 92,228 L 88,252 L 16,258 Z',
  'M 92,158 L 98,118 L 168,108 L 168,148 Z',
  'M 168,108 L 238,98 L 238,138 L 168,148 Z',
  'M 238,98 L 305,88 L 305,128 L 238,138 Z',
  'M 305,88 L 378,78 L 378,118 L 305,128 Z',
  'M 98,118 L 112,58 L 168,52 L 168,108 Z',
  'M 168,52 L 238,42 L 238,98 L 168,108 Z',
  'M 238,42 L 305,38 L 305,88 L 238,98 Z',
];

/** Label anchor (x, y) in viewBox coords, start/end for textPath optional — simple absolute position */
export const REGION_LABEL_POS: readonly { x: number; y: number }[] = [
  { x: 52, y: 200 },
  { x: 132, y: 188 },
  { x: 204, y: 178 },
  { x: 272, y: 168 },
  { x: 342, y: 158 },
  { x: 52, y: 244 },
  { x: 132, y: 132 },
  { x: 202, y: 122 },
  { x: 270, y: 112 },
  { x: 340, y: 102 },
  { x: 132, y: 84 },
  { x: 202, y: 72 },
  { x: 270, y: 64 },
];

export const WORLD_MAP_VIEWBOX = '0 0 400 260';

export function getAttackableRegionIds(
  mapState: { id: number; ownerUid: string | null }[],
  attackerUid: string
): Set<number> {
  const byId = new Map(mapState.map((h) => [h.id, h]));
  const out = new Set<number>();
  for (const t of mapState) {
    if (t.ownerUid === attackerUid) continue;
    const ok = getRegionNeighbors(t.id).some((nid) => byId.get(nid)?.ownerUid === attackerUid);
    if (ok) out.add(t.id);
  }
  return out;
}

export function getExpansionPickableRegionIds(
  mapState: { id: number; ownerUid: string | null }[],
  uid: string
): Set<number> {
  const byId = new Map(mapState.map((h) => [h.id, h]));
  const out = new Set<number>();
  for (const t of mapState) {
    if (t.ownerUid != null) continue;
    const ok = getRegionNeighbors(t.id).some((nid) => byId.get(nid)?.ownerUid === uid);
    if (ok) out.add(t.id);
  }
  return out;
}
