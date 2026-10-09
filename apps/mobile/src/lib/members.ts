// Pure helpers (no React, no imports) so they can be unit-tested with `node --test`.

export type RoleCode = 'player' | 'admin' | 'superuser';
export type StatusCode = 'invited' | 'active' | 'deactivated';

const ROLE_LABEL: Record<RoleCode, string> = { player: 'Player', admin: 'Admin', superuser: 'Support' };
const STATUS_LABEL: Record<StatusCode, string> = { invited: 'Invited', active: 'Active', deactivated: 'Deactivated' };

export function roleLabel(role: RoleCode): string {
  return ROLE_LABEL[role] ?? role;
}

export function statusLabel(status: StatusCode): string {
  return STATUS_LABEL[status] ?? status;
}

/** Admins and superusers see the Users screen (admins can only change players there). */
export function canManageMembers(role: RoleCode | undefined): boolean {
  return role === 'admin' || role === 'superuser';
}

/**
 * The same first checks the server makes, so people get instant feedback. The server still
 * decides (it also refuses well-known passwords).
 */
export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < 8) return 'Use at least 8 characters.';
  if (password.length > 64) return 'Use at most 64 characters.';
  if (password !== confirm) return "The two passwords don't match.";
  return null;
}

/** A light check before sending; the server validates properly. */
export function looksLikeEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}
