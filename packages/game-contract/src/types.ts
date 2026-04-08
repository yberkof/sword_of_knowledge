/** Types shared by the game server and clients (authoritative room shape is server-driven). */

export interface UserProfile {
  uid: string;
  name: string;
  username?: string;
  countryFlag?: string;
  title: string;
  level: number;
  xp: number;
  gold: number;
  gems: number;
  avatar: string;
  rank: string;
  trophies: number;
  inventory: string[];
}

export interface Question {
  id: string;
  text: string;
  options: string[];
  correctIndex?: number;
  category: string;
  difficulty: string;
}

export interface HexData {
  id: number;
  ownerUid: string | null;
  isCapital: boolean;
  isShielded?: boolean;
  type: "neutral" | "player";
}

export interface MatchState {
  id: string;
  players: {
    uid: string;
    name: string;
    hp: number;
    color: string;
    isCapitalLost: boolean;
    trophies?: number;
    eliminatedAt?: number;
  }[];
  mapState: HexData[];
  phase: "waiting" | "expansion" | "conquest" | "duel" | "tiebreaker" | "resolution" | "ended";
  currentTurnIndex: number;
  hostUid?: string;
  inviteCode?: string | null;
  matchScore?: Record<string, number>;
  battleRound?: number;
  attacksRemainingThisRound?: number;
  activeDuel?: {
    attackerUid: string;
    defenderUid: string;
    targetHexId: number;
    questionId: string;
    startTime: string;
    answers: Record<string, { answerIndex: number; timeTaken: number }>;
  };
  tieBreaker?: Record<string, unknown>;
  expansion?: {
    round: number;
    phase?: string;
    pickQueue?: string[];
    correctAnswer?: number;
  };
}

export type Screen = "WAR_ROOM" | "MATCH_SETUP" | "RECORDS" | "ARMORY";

export interface GameMode {
  id: string;
  title: string;
  description: string;
  image: string;
  duration: string;
  difficulty: string;
}

export interface InventoryItem {
  id: string;
  name: string;
  description: string;
  price: number;
  icon: string;
}

export interface LeaderboardEntry {
  rank: number;
  name: string;
  title: string;
  points: number;
  avatar: string;
  icon: unknown;
}

export enum ClanRole {
  MEMBER = "member",
  ELDER = "elder",
  CO_LEADER = "co-leader",
  LEADER = "leader",
}

export interface Clan {
  id: string;
  name: string;
  description: string;
  leaderId: string;
  createdAt: string;
  memberCount: number;
}

export interface ClanMember {
  uid: string;
  clanId: string;
  role: ClanRole;
  joinedAt: string;
  lastKickAt?: string;
  lastBroadcastAt?: string;
  name?: string;
  avatar?: string;
  level?: number;
}

export const CLAN_CREATE_MIN_LEVEL = 10;
export const CLAN_CREATE_GOLD_COST = 10000;
export const CLAN_ELDER_KICK_COOLDOWN_MS = 20 * 60 * 1000;
export const CLAN_BROADCAST_COOLDOWN_MS = 1 * 60 * 60 * 1000;
export const CLAN_LEADER_INACTIVITY_LIMIT_MS = 60 * 24 * 60 * 60 * 1000; // 60 days
