import { Link, router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Button, Card, Field, Notice, Screen, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { passwordProblem } from '@/lib/members';

/** Opened from the reset email. Choosing a new password signs out every other device. */
export default function ResetPasswordScreen() {
  const t = useTheme();
  const auth = useAuth();
  const { token } = useLocalSearchParams<{ token?: string }>();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!token) {
      setError('This link is missing its code. Open the whole link from the email.');
      return;
    }
    const problem = passwordProblem(password, confirm);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.resetPassword(token, password);
      await auth.completeSignIn(result);
      router.replace('/');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="Choose a new password" subtitle="You'll be signed out on your other devices.">
      <Card style={styles.card}>
        <Field
          label="New password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete="new-password"
          textContentType="newPassword"
          hint="At least 8 characters."
        />
        <Field
          label="New password again"
          value={confirm}
          onChangeText={setConfirm}
          secureTextEntry
          autoComplete="new-password"
          textContentType="newPassword"
          onSubmitEditing={submit}
        />
        {error ? <Notice tone="error">{error}</Notice> : null}
        <Button label="Save password" onPress={submit} busy={busy} style={styles.wide} />
      </Card>
      <Link href="/forgot-password" style={[styles.link, { color: t.link }]}>
        <Text>Link expired? Send a new one</Text>
      </Link>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  wide: { alignSelf: 'stretch' },
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
