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
