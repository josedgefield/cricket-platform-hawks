import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { api, ApiError, type Member, type SignedIn, setAuthToken, setUnauthorizedHandler } from '@/lib/api';
import { clearSession, loadSession, saveSession } from '@/lib/token-store';

type Stored = { token: string; member: Member };

export type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; token: string; member: Member };

type AuthContextValue = AuthState & {
  signIn: (email: string, password: string) => Promise<void>;
  /** For flows that already returned a session (accepting an invite, resetting a password). */
  completeSignIn: (result: SignedIn) => Promise<void>;
  signOut: () => Promise<void>;
  /** Re-reads the member after they change their profile. */
  setMember: (member: Member) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the session token (Keychain/Keystore on phones, localStorage on web) and the signed-in
 * member. On start it checks the stored session with the server; if the server can't be
 * reached it keeps the stored member so the app still opens.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const forget = useCallback(async () => {
    setAuthToken(null);
    await clearSession();
    setState({ status: 'signedOut' });
  }, []);

  const remember = useCallback(async ({ token, member }: Stored) => {
    setAuthToken(token);
    await saveSession(JSON.stringify({ token, member }));
    setState({ status: 'signedIn', token, member });
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      void forget();
    });
    (async () => {
      const raw = await loadSession();
      let stored: Stored | null = null;
      try {
        stored = raw ? (JSON.parse(raw) as Stored) : null;
      } catch {
        stored = null;
      }
      if (!stored?.token) {
        setState({ status: 'signedOut' });
        return;
      }
      setAuthToken(stored.token);
      try {
        const member = await api.me();
        await remember({ token: stored.token, member });
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          await forget();
        } else {
          setState({ status: 'signedIn', token: stored.token, member: stored.member });
        }
      }
    })();
    return () => setUnauthorizedHandler(null);
  }, [forget, remember]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      signIn: async (email, password) => {
        const result = await api.signIn(email.trim(), password);
        await remember(result);
      },
      completeSignIn: remember,
      signOut: async () => {
        try {
          await api.signOut();
        } catch {
          // already signed out on the server, or offline: forget locally either way
        }
        await forget();
      },
      setMember: (member) => {
        if (state.status === 'signedIn') {
          void remember({ token: state.token, member });
        }
      },
    }),
    [state, remember, forget],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
