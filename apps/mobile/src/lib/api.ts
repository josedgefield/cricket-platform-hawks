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

// ---- Members and sign-in (backend identity module) ----

export type Role = 'player' | 'admin' | 'superuser';
export type MemberStatus = 'invited' | 'active' | 'deactivated';

export type Member = {
  id: string;
  email: string;
  displayName: string;
  phone: string | null;
  role: Role;
  status: MemberStatus;
  playerId: string | null;
  invitedAt: string | null;
  inviteSentAt: string | null;
  /** Why the last invite email couldn't be sent, if it failed. */
  inviteEmailError: string | null;
  lastSignInAt: string | null;
  /** Whether the signed-in member may change this member. */
  manageable: boolean;
};

export type SignedIn = { token: string; member: Member };
export type Invitation = { email: string; displayName: string; clubName: string };

// ---- Finance (backend finance module). Amounts are whole cents. ----

export type ChargeStatus = 'open' | 'part_paid' | 'paid' | 'waived' | 'void';
export type PaymentMethod = 'paynow' | 'bank' | 'cash' | 'other';
export type AdjustmentKind = 'void' | 'waiver' | 'credit' | 'refund' | 'reversal';

export type ChargeLine = {
  id: string;
  reference: string;
  description: string;
  amountCents: number;
  paidCents: number;
  writtenOffCents: number;
  outstandingCents: number;
  dueDate: string | null;
  status: ChargeStatus;
  overdue: boolean;
  createdAt: string;
};

export type PaymentLine = {
  id: string;
  reference: string;
  amountCents: number;
  method: PaymentMethod;
  receivedOn: string;
  externalRef: string | null;
  note: string | null;
  refundedCents: number;
  unallocatedCents: number;
  reversed: boolean;
  createdAt: string;
};

export type StatementEntry = {
  at: string;
  date: string;
  kind: 'charge' | 'payment' | AdjustmentKind;
  reference: string | null;
  description: string;
  /** Positive increases what the member owes; negative reduces it. */
  amountCents: number;
  balanceCents: number;
};

export type Statement = {
  memberId: string;
  displayName: string;
  /** Owed when positive, in credit when negative. Always calculated from the ledger. */
  balanceCents: number;
  owedCents: number;
  unusedCents: number;
  overdueCents: number;
  charges: ChargeLine[];
  payments: PaymentLine[];
  entries: StatementEntry[];
};

export type MemberBalance = {
  memberId: string;
  displayName: string;
  memberStatus: MemberStatus;
  balanceCents: number;
  owedCents: number;
  unusedCents: number;
  overdueCents: number;
  openCharges: number;
};

export type FinanceOverview = {
  owedCents: number;
  overdueCents: number;
  unusedCents: number;
  collectedCents: number;
  members: MemberBalance[];
};

// ---- Notices (backend comms module) ----

export type ReactionKey = 'like' | 'done' | 'cricket' | 'clap' | 'question';

export type Notice = {
  id: string;
  title: string;
  body: string;
  pinned: boolean;
  authorName: string;
  createdAt: string;
  editedAt: string | null;
  reactions: Partial<Record<ReactionKey, number>>;
  myReaction: ReactionKey | null;
  replyCount: number;
  unread: boolean;
  /** Admins only. */
  readCount: number | null;
  /** Admins only: how the email copy went, if one was asked for. */
  email: { status: 'sending' | 'sent' | 'failed'; sent: number | null; failed: number | null } | null;
};

export type NoticeReply = {
  id: string;
  authorName: string;
  mine: boolean;
  /** Null when hidden. */
  body: string | null;
  createdAt: string;
  hidden: boolean;
  hiddenReason: string | null;
};

export type NoticeWithReplies = { notice: Notice; replies: NoticeReply[] };

/**
 * A new random key for one submitted form. Sending the same key again (double tap, retry
 * after a timeout) makes the server return the first result instead of doing it twice.
 */
export function newRequestKey(): string {
  const c = globalThis.crypto as { randomUUID?: () => string } | undefined;
  if (c?.randomUUID) {
    return c.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

/** An error the UI can show as-is: the server's own message when it sent one. */
export class ApiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

/** Set by the auth provider; every request then carries the session token. */
export function setAuthToken(token: string | null) {
  authToken = token;
}

/** Called when the server says the session is no longer valid (expired, revoked, deactivated). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (authToken) headers.Authorization = `Bearer ${authToken}`;
  try {
    const res = await fetch(API_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    if (!res.ok) {
      let detail: string | undefined;
      try {
        const problem = (await res.json()) as { detail?: string };
        detail = problem.detail;
      } catch {
        // no JSON body
      }
      if (res.status === 401 && authToken) {
        onUnauthorized?.();
        throw new ApiError('Your session has ended. Please sign in again.', 401);
      }
      throw new ApiError(detail ?? `The server answered ${res.status}.`, res.status);
    }
    if (res.status === 202 || res.status === 204) {
      return undefined as T;
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

const get = <T>(path: string) => request<T>('GET', path);
const enc = encodeURIComponent;

export const api = {
  players: (source: SourceCode | 'all') => get<PlayerStats[]>(`/api/stats/players?source=${source}`),
  sources: () => get<SourceStatus[]>('/api/stats/sources'),
  competitions: () => get<Competition[]>('/api/stats/competitions'),
  standings: (competitionId: string) => get<Standing[]>(`/api/stats/competitions/${competitionId}/standings`),

  signIn: (email: string, password: string) => request<SignedIn>('POST', '/api/auth/sign-in', { email, password }),
  signOut: () => request<void>('POST', '/api/auth/sign-out'),
  invitation: (token: string) => get<Invitation>(`/api/auth/invitations/${enc(token)}`),
  acceptInvite: (token: string, password: string) =>
    request<SignedIn>('POST', `/api/auth/invitations/${enc(token)}/accept`, { password }),
  requestPasswordReset: (email: string) => request<void>('POST', '/api/auth/password-reset', { email }),
  resetPassword: (token: string, password: string) =>
    request<SignedIn>('POST', `/api/auth/password-reset/${enc(token)}`, { password }),

  me: () => get<Member>('/api/me'),
  updateMe: (changes: { displayName?: string; phone?: string }) => request<Member>('PATCH', '/api/me', changes),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/api/me/password', { currentPassword, newPassword }),

  members: (status?: MemberStatus, q?: string) => {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (q && q.trim()) params.set('q', q.trim());
    const query = params.toString();
    return get<Member[]>(`/api/admin/members${query ? `?${query}` : ''}`);
  },
  member: (id: string) => get<Member>(`/api/admin/members/${enc(id)}`),
  invite: (invite: { email: string; displayName: string; phone?: string; role?: Role }) =>
    request<Member>('POST', '/api/admin/members', invite),
  updateMember: (id: string, changes: { email?: string; displayName?: string; phone?: string }) =>
    request<Member>('PATCH', `/api/admin/members/${enc(id)}`, changes),
  deactivate: (id: string) => request<Member>('POST', `/api/admin/members/${enc(id)}/deactivate`),
  reactivate: (id: string) => request<Member>('POST', `/api/admin/members/${enc(id)}/reactivate`),
  resendInvite: (id: string) => request<Member>('POST', `/api/admin/members/${enc(id)}/resend-invite`),
  changeRole: (id: string, role: Role) => request<Member>('PUT', `/api/admin/members/${enc(id)}/role`, { role }),

  notices: () => get<Notice[]>('/api/notices'),
  notice: (id: string) => get<NoticeWithReplies>(`/api/notices/${enc(id)}`),
  react: (id: string, reaction: ReactionKey | null) =>
    request<NoticeWithReplies>('PUT', `/api/notices/${enc(id)}/reaction`, { reaction }),
  reply: (id: string, body: string) => request<NoticeWithReplies>('POST', `/api/notices/${enc(id)}/replies`, { body }),
  hideReply: (id: string, replyId: string, reason?: string) =>
    request<NoticeWithReplies>('POST', `/api/notices/${enc(id)}/replies/${enc(replyId)}/hide`, { reason }),
  publishNotice: (notice: { title: string; body: string; pinned: boolean; emailMembers: boolean; requestKey: string }) =>
    request<Notice>('POST', '/api/admin/notices', notice),
  editNotice: (id: string, changes: { title?: string; body?: string; pinned?: boolean }) =>
    request<Notice>('PATCH', `/api/admin/notices/${enc(id)}`, changes),
  withdrawNotice: (id: string) => request<void>('POST', `/api/admin/notices/${enc(id)}/withdraw`),

  myStatement: () => get<Statement>('/api/me/statement'),
  financeOverview: () => get<FinanceOverview>('/api/admin/finance/overview'),
  memberStatement: (memberId: string) => get<Statement>(`/api/admin/finance/members/${enc(memberId)}/statement`),
  raiseCharges: (charges: {
    memberIds?: string[];
    everyone?: boolean;
    description: string;
    amountCents: number;
    dueDate?: string;
    requestKey: string;
  }) => request<{ batchId: string; count: number }>('POST', '/api/admin/finance/charges', charges),
  recordPayment: (payment: {
    memberId: string;
    amountCents: number;
    method: PaymentMethod;
    receivedOn?: string;
    externalRef?: string;
    note?: string;
    chargeId?: string;
    requestKey: string;
  }) => request<Statement>('POST', '/api/admin/finance/payments', payment),
  adjust: (adjustment: {
    kind: AdjustmentKind;
    chargeId?: string;
    paymentId?: string;
    memberId?: string;
    amountCents?: number;
    reason: string;
    requestKey: string;
  }) => request<Statement>('POST', '/api/admin/finance/adjustments', adjustment),
};
