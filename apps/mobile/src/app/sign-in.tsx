import { Link, Redirect } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Body, Button, Card, Field, Notice, Screen, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/lib/auth';
import { looksLikeEmail } from '@/lib/members';

export default function SignInScreen() {
  const t = useTheme();
  const auth = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (auth.status === 'signedIn') {
    return <Redirect href="/" />;
  }

  const submit = async () => {
    if (!looksLikeEmail(email) || password.length === 0) {
      setError('Enter your email and password.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await auth.signIn(email, password);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title="Sign in" subtitle="Hawks CC members">
      <Card style={styles.card}>
        <Field
          label="Email"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          textContentType="username"
          inputMode="email"
        />
        <Field
          label="Password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete="current-password"
          textContentType="password"
          onSubmitEditing={submit}
        />
        {error ? <Notice tone="error">{error}</Notice> : null}
        <Button label="Sign in" onPress={submit} busy={busy} style={styles.wide} />
        <Link href="/forgot-password" style={[styles.link, { color: t.link }]}>
          <Text>Forgot your password?</Text>
        </Link>
      </Card>
      <Body muted>
        New to the club app? There's no sign-up: a club admin sends you an email invite. Open the link in it to
        choose your password.
      </Body>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  wide: { alignSelf: 'stretch' },
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
