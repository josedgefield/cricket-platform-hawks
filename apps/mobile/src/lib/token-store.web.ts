// Browser storage for the web build. Fine for a club app; a cookie-based session would be
// harder to steal through a script injection, and can replace this if the web app grows.
const KEY = 'hawks.session';

export async function loadSession(): Promise<string | null> {
  try {
    return globalThis.localStorage?.getItem(KEY) ?? null;
  } catch {
    return null;
  }
}

export async function saveSession(value: string): Promise<void> {
  try {
    globalThis.localStorage?.setItem(KEY, value);
  } catch {
    // private mode or storage disabled: stay signed in for this tab only
  }
}

export async function clearSession(): Promise<void> {
  try {
    globalThis.localStorage?.removeItem(KEY);
  } catch {
    // nothing stored
  }
}
