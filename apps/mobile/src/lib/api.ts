import type { Rate } from './format';

/**
 * Backend base URL. Set EXPO_PUBLIC_API_URL in .env.local (see .env.example):
 * localhost for web, 10.0.2.2 for the Android emulator, your computer's IP for a phone.
 */
export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

const TIMEOUT_MS = 10_000;

// ---- Types mirroring the backend's StatsViews (null always means "unknown") ----

export type SourceCode = 'sca' | 'cricheroes';

export type Batting = {
  inns: number | null;
  notOuts: number | null;
  runs: number | null;
  balls: number | null;
  highScore: number | null;
  fours: number | null;
  sixes: number | null;
  average: Rate;
  strikeRate: Rate;
};

export type Bowling = {
  inns: number | null;
  overs: string | null;
  balls: number | null;
  maidens: number | null;
  runs: number | null;
  wickets: number | null;
  average: Rate;
  economy: Rate;
  strikeRate: Rate;
};

export type Fielding = {
  catches: number | null;
  stumpings: number | null;
  runOuts: number | null;
  dismissals: number | null;
};

export type PlayerStats = {
  playerId: string;
  name: string;
  sources: SourceCode[];
  matches: number | null;
  batting: Batting | null;
  bowling: Bowling | null;
  fielding: Fielding | null;
  /** Counts recovered exactly from a published rate, as "batting.balls", "bowling.runs" etc. */
  recovered: string[];
  /** Which sources each category total adds up (a source may not list the player in every category). */
  coverage: { batting: SourceCode[]; bowling: SourceCode[]; fielding: SourceCode[] };
};

export type SourceStatus = {
  source: SourceCode;
  lastSucceededAt: string | null;
  lastRunAt: string | null;
  lastRunStatus: string | null;
  lastError: string | null;
};

export type Competition = { id: string; source: SourceCode; name: string; season: string | null };

export type Standing = {
  group: string;
  position: number;
  team: string;
  clubTeam: boolean;
  matches: number | null;
  won: number | null;
  lost: number | null;
  noResult: number | null;
  points: number | null;
  netRunRate: string | null;
};

/** An error the UI can show as-is. */
export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

async function get<T>(path: string): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(API_URL + path, { headers: { Accept: 'application/json' }, signal: controller.signal });
    if (!res.ok) {
      throw new ApiError(`The server answered ${res.status}.`, res.status);
    }
    return (await res.json()) as T;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    if (e instanceof Error && e.name === 'AbortError') {
      throw new ApiError('The server took too long to answer.');
    }
    throw new ApiError(`Can't reach the club server at ${API_URL}.`);
  } finally {
    clearTimeout(timer);
  }
}

export const api = {
  players: (source: SourceCode | 'all') => get<PlayerStats[]>(`/api/stats/players?source=${source}`),
  sources: () => get<SourceStatus[]>('/api/stats/sources'),
  competitions: () => get<Competition[]>('/api/stats/competitions'),
  standings: (competitionId: string) => get<Standing[]>(`/api/stats/competitions/${competitionId}/standings`),
};
