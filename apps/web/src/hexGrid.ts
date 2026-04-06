/** Re-exports region map (13 territories) — legacy filename kept for imports. */
export {
  MAP_REGION_COUNT as MAP_HEX_COUNT,
  getRegionNeighbors,
  getAttackableRegionIds as getAttackableHexIds,
  getExpansionPickableRegionIds as getExpansionPickableHexIds,
  TERRITORY_NAMES,
  REGION_SVG_PATHS,
  REGION_LABEL_POS,
  WORLD_MAP_VIEWBOX,
} from './mapRegions';
