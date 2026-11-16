import { Link } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Button, Card, Field, Notice, Screen, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api } from '@/lib/api';
import { looksLikeEmail } from '@/lib/members';

export default function ForgotPasswordScreen() {
  const t = useTheme();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!looksLikeEmail(email)) {
      setError('Enter the email address you were invited with.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.requestPasswordReset(email.trim());
      setSent(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="Reset password" subtitle="We'll email you a link to choose a new one.">
      <Card style={styles.card}>
        {sent ? (
          <Notice tone="success">
            If {email.trim()} belongs to a member, a reset link is on its way. It works once and expires in 1 hour.
            Check your spam folder if it doesn't arrive.
          </Notice>
        ) : (
          <>
            <Field
              label="Email"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              autoComplete="email"
              keyboardType="email-address"
              inputMode="email"
              onSubmitEditing={submit}
            />
            {error ? <Notice tone="error">{error}</Notice> : null}
            <Button label="Send reset link" onPress={submit} busy={busy} style={styles.wide} />
          </>
        )}
        <Link href="/sign-in" style={[styles.link, { color: t.link }]}>
          <Text>Back to sign in</Text>
        </Link>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  wide: { alignSelf: 'stretch' },
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
