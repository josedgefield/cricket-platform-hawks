import { Link } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Body, Button, Card, Chip, Field, Notice, Screen, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { canManageMembers, passwordProblem, roleLabel } from '@/lib/members';

export default function MoreScreen() {
  const t = useTheme();
  const auth = useAuth();
  if (auth.status !== 'signedIn') return null;
  const me = auth.member;

  return (
    <Screen title="More">
      <Card accent style={styles.card}>
        <View style={styles.row}>
          <Text style={[styles.name, { color: t.text }]}>{me.displayName}</Text>
          <Chip label={roleLabel(me.role)} tone={me.role === 'player' ? 'muted' : 'gold'} />
        </View>
        <Body muted>{me.email}</Body>
        {canManageMembers(me.role) ? (
          <Link href="/admin/users" style={[styles.link, { color: t.link }]}>
            <Text>Manage users →</Text>
          </Link>
        ) : null}
      </Card>

      <ProfileForm />
      <PasswordForm />

      <Card style={styles.card}>
        <SectionTitle>Coming later</SectionTitle>
        <Body muted>• Club notices with replies and reactions</Body>
        <Body muted>• Notification settings</Body>
        <Body muted>• Privacy and account deletion</Body>
      </Card>

      <Button label="Sign out" variant="outline" onPress={() => void auth.signOut()} />
    </Screen>
  );
}

function ProfileForm() {
  const auth = useAuth();
  const me = auth.status === 'signedIn' ? auth.member : null;
  const [name, setName] = useState(me?.displayName ?? '');
  const [phone, setPhone] = useState(me?.phone ?? '');
  const [result, setResult] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!name.trim()) {
      setResult({ tone: 'error', text: 'Your name can’t be empty.' });
      return;
    }
    setBusy(true);
    setResult(null);
    try {
      const updated = await api.updateMe({ displayName: name.trim(), phone: phone.trim() });
      auth.setMember(updated);
      setResult({ tone: 'success', text: 'Saved.' });
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Your details</SectionTitle>
      <Field label="Name" value={name} onChangeText={setName} autoComplete="name" />
      <Field
        label="Phone (optional)"
        value={phone}
        onChangeText={setPhone}
        autoComplete="tel"
        keyboardType="phone-pad"
        inputMode="tel"
        hint="Only admins see this."
      />
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label="Save details" onPress={save} busy={busy} />
    </Card>
  );
}

function PasswordForm() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [result, setResult] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    const problem = passwordProblem(next, confirm);
    if (!current || problem) {
      setResult({ tone: 'error', text: current ? (problem as string) : 'Enter your current password.' });
      return;
    }
    setBusy(true);
    setResult(null);
    try {
      await api.changePassword(current, next);
      setCurrent('');
      setNext('');
      setConfirm('');
      setResult({ tone: 'success', text: 'Password changed. Your other devices have been signed out.' });
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Change password</SectionTitle>
      <Field label="Current password" value={current} onChangeText={setCurrent} secureTextEntry autoComplete="current-password" />
      <Field label="New password" value={next} onChangeText={setNext} secureTextEntry autoComplete="new-password" hint="At least 8 characters." />
      <Field label="New password again" value={confirm} onChangeText={setConfirm} secureTextEntry autoComplete="new-password" />
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label="Change password" onPress={save} busy={busy} />
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  row: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' },
  name: { fontSize: 20, fontWeight: '800' },
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
