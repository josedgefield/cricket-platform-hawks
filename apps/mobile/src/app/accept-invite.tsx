import { Link, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Body, Button, Card, ErrorState, Field, Loading, Notice, Screen, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { passwordProblem } from '@/lib/members';
import { useAsync } from '@/lib/use-async';

/** Opened from the invite email: greets the member and lets them choose a password. */
export default function AcceptInviteScreen() {
  const t = useTheme();
  const auth = useAuth();
  const { token } = useLocalSearchParams<{ token?: string }>();
  const invite = useAsync(
    () => (token ? api.invitation(token) : Promise.reject(new Error('This link is missing its code. Open the whole link from the email.'))),
    [token],
  );
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    const problem = passwordProblem(password, confirm);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.acceptInvite(token as string, password);
      await auth.completeSignIn(result);
      router.replace('/');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="Join the club app">
      {invite.status === 'loading' ? <Loading label="Checking your invite…" /> : null}
      {invite.status === 'error' ? <ErrorState message={invite.message} onRetry={invite.reload} /> : null}
      {invite.status === 'success' ? (
        <Card style={styles.card}>
          <Body>
            Hi {invite.data.displayName}, welcome to the {invite.data.clubName} app. Choose a password for{' '}
            <Text style={{ fontWeight: '700' }}>{invite.data.email}</Text>.
          </Body>
          <Field
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            hint="At least 8 characters. A short phrase of a few words is easy to remember and hard to guess."
          />
          <Field
            label="Password again"
            value={confirm}
            onChangeText={setConfirm}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            onSubmitEditing={submit}
          />
          {error ? <Notice tone="error">{error}</Notice> : null}
          <Button label="Join" onPress={submit} busy={busy} style={styles.wide} />
        </Card>
      ) : null}
      <Link href="/sign-in" style={[styles.link, { color: t.link }]}>
        <Text>Already joined? Sign in</Text>
      </Link>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  wide: { alignSelf: 'stretch' },
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
