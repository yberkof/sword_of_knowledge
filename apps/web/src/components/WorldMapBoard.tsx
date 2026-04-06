import React from 'react';
import type { HexData, MatchState } from '../types';
import {
  MAP_REGION_COUNT,
  REGION_LABEL_POS,
  REGION_SVG_PATHS,
  TERRITORY_NAMES,
  WORLD_MAP_VIEWBOX,
} from '../mapRegions';

type WorldMapBoardProps = {
  mapState: HexData[];
  players: MatchState['players'];
  attackableIds: Set<number>;
  expansionPickableIds: Set<number>;
  /** True during expansion pick phase for current picker */
  showExpansionPickHighlight: boolean;
  onRegionClick: (regionId: number) => void;
  isRegionInactive: (region: HexData) => boolean;
};

export default function WorldMapBoard({
  mapState,
  players,
  attackableIds,
  expansionPickableIds,
  showExpansionPickHighlight,
  onRegionClick,
  isRegionInactive,
}: WorldMapBoardProps) {
  const byId = new Map(mapState.map((t) => [t.id, t]));

  return (
    <svg
      viewBox={WORLD_MAP_VIEWBOX}
      className="h-auto w-full max-h-[min(78dvh,560px)] touch-manipulation select-none"
      preserveAspectRatio="xMidYMid meet"
      role="img"
      aria-label="خريطة العالم — ثلاث عشرة إقليمًا"
    >
      <defs>
        <linearGradient id="worldMapOcean" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#1e3a5f" />
          <stop offset="55%" stopColor="#0f1f33" />
          <stop offset="100%" stopColor="#0a1524" />
        </linearGradient>
        <filter id="worldMapTerritoryGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="0.8" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <rect width="400" height="260" fill="url(#worldMapOcean)" rx="10" ry="10" />

      {Array.from({ length: MAP_REGION_COUNT }, (_, i) => {
        const t = byId.get(i);
        const owner = t ? players.find((p) => p.uid === t.ownerUid) : null;
        const baseFill = owner ? owner.color : 'rgba(45, 70, 98, 0.88)';
        const ring =
          attackableIds.has(i) || (showExpansionPickHighlight && expansionPickableIds.has(i));
        const inactive = t ? isRegionInactive(t) : true;
        const shielded = Boolean(t?.isShielded);
        const d = REGION_SVG_PATHS[i] ?? '';
        const pos = REGION_LABEL_POS[i] ?? { x: 200, y: 130 };
        const name = TERRITORY_NAMES[i] ?? '';

        return (
          <g
            key={i}
            role="button"
            tabIndex={0}
            className="cursor-pointer outline-none"
            aria-label={`${name}${owner ? ` — ${owner.name}` : ''}`}
            onClick={() => onRegionClick(i)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onRegionClick(i);
              }
            }}
          >
            <path
              d={d}
              fill={baseFill}
              stroke={shielded ? 'rgba(56, 189, 248, 0.95)' : 'rgba(212, 175, 55, 0.55)'}
              strokeWidth={ring ? 3.2 : shielded ? 2.4 : 1.2}
              opacity={inactive ? 0.52 : 1}
              filter={ring ? 'url(#worldMapTerritoryGlow)' : undefined}
              style={{ transition: 'opacity 0.2s ease, stroke-width 0.2s ease' }}
            />
            {t?.isCapital ? (
              <text
                x={pos.x}
                y={pos.y - 10}
                textAnchor="middle"
                className="pointer-events-none"
                style={{ fontSize: '17px', filter: 'drop-shadow(0 2px 3px rgba(0,0,0,0.9))' }}
              >
                🏰
              </text>
            ) : null}
            <text
              x={pos.x}
              y={pos.y + (t?.isCapital ? 6 : 2)}
              textAnchor="middle"
              direction="rtl"
              className="pointer-events-none font-amiri"
              style={{
                fontSize: '8.5px',
                fontWeight: 700,
                fill: 'rgba(255, 248, 235, 0.92)',
                textShadow: '0 1px 3px rgba(0,0,0,0.95), 0 0 8px rgba(0,0,0,0.6)',
              }}
            >
              {name.length > 14 ? `${name.slice(0, 13)}…` : name}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
