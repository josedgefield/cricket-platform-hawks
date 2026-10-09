import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AdminOnly, BackLink, RoleChip, StatusChip } from '@/components/admin';
import {
  Body,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Loading,
  Notice,
  Screen,
  SectionTitle,
  Segmented,
  useTheme,
} from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api, type Member, type MemberStatus, type Role } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { relativeTime } from '@/lib/format';
import { looksLikeEmail } from '@/lib/members';
import { useAsync } from '@/lib/use-async';

type Filter = MemberStatus | 'all';

export default function UsersScreen() {
  return (
    <AdminOnly>
      <Users />
    </AdminOnly>
  );
}

function Users() {
  const [filter, setFilter] = useState<Filter>('all');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const members = useAsync(() => api.members(filter === 'all' ? undefined : filter, query), [filter, query]);

  return (
    <Screen title="Users" subtitle="Invite players and manage their accounts.">
      <BackLink href="/more" label="More" />
      <InviteForm onInvited={members.reload} />

      <SectionTitle>Members</SectionTitle>
      <Field
        label="Search"
        value={search}
        onChangeText={setSearch}
        onSubmitEditing={() => setQuery(search)}
        onBlur={() => setQuery(search)}
        placeholder="Name or email"
        autoCapitalize="none"
        returnKeyType="search"
      />
      <Segmented
        label="Status"
        value={filter}
        onChange={setFilter}
        options={[
          { value: 'all', label: 'All' },
          { value: 'active', label: 'Active' },
          { value: 'invited', label: 'Invited' },
          { value: 'deactivated', label: 'Off' },
        ]}
      />
      {members.status === 'loading' ? <Loading label="Loading members…" /> : null}
      {members.status === 'error' ? <ErrorState message={members.message} onRetry={members.reload} /> : null}
      {members.status === 'success' && members.data.length === 0 ? (
        <EmptyState
          title="No members here"
          message={query ? `Nobody matches “${query}”.` : 'Invite someone with the form above.'}
        />
      ) : null}
      {members.status === 'success' ? (
        <View style={styles.list}>
          {members.data.map((m) => (
            <MemberRow key={m.id} member={m} />
          ))}
        </View>
      ) : null}
    </Screen>
  );
}

function MemberRow({ member }: { member: Member }) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${member.displayName}, ${member.role}, ${member.status}`}
      onPress={() => router.push(`/admin/users/${member.id}`)}
      style={({ pressed }) => [pressed && { opacity: 0.7 }]}>
      <Card style={styles.row}>
        <View style={styles.rowHead}>
          <Text style={[styles.name, { color: t.text }]} numberOfLines={1}>
            {member.displayName}
          </Text>
          <RoleChip member={member} />
          <StatusChip member={member} />
        </View>
        <Body muted numberOfLines={1}>
          {member.email}
        </Body>
        {member.status === 'invited' && member.inviteEmailError ? (
          <Body style={{ color: t.danger }}>Invite email failed: {member.inviteEmailError}</Body>
        ) : null}
        {member.status === 'active' ? (
          <Body muted style={styles.small}>
            Last signed in {relativeTime(member.lastSignInAt).toLowerCase()}
          </Body>
        ) : null}
      </Card>
    </Pressable>
  );
}

function InviteForm({ onInvited }: { onInvited: () => void }) {
  const auth = useAuth();
  const isSuperuser = auth.status === 'signedIn' && auth.member.role === 'superuser';
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<Role>('player');
  const [result, setResult] = useState<{ tone: 'success' | 'error' | 'warning'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return (
      <View style={{ gap: Spacing.sm }}>
        {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
        <Button label="Invite a member" variant="gold" onPress={() => { setResult(null); setOpen(true); }} />
      </View>
    );
  }

  const submit = async () => {
    if (!looksLikeEmail(email) || !name.trim()) {
      setResult({ tone: 'error', text: 'Enter their name and a valid email address.' });
      return;
    }
    setBusy(true);
    setResult(null);
    try {
      const m = await api.invite({
        email: email.trim(),
        displayName: name.trim(),
        phone: phone.trim() || undefined,
        role: isSuperuser ? role : undefined,
      });
      setEmail('');
      setName('');
      setPhone('');
      setRole('player');
      setOpen(false);
      setResult(
        m.inviteEmailError
          ? { tone: 'warning', text: `${m.displayName} was added, but the email failed: ${m.inviteEmailError}. Open them to resend.` }
          : { tone: 'success', text: `Invite sent to ${m.email}. The link works for 7 days.` },
      );
      onInvited();
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card accent style={styles.form}>
      <SectionTitle>Invite a member</SectionTitle>
      <Field label="Name" value={name} onChangeText={setName} autoComplete="off" />
      <Field
        label="Email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        inputMode="email"
        autoComplete="off"
      />
      <Field label="Phone (optional)" value={phone} onChangeText={setPhone} keyboardType="phone-pad" inputMode="tel" />
      {isSuperuser ? (
        <Segmented
          label="Role"
          value={role}
          onChange={setRole}
          options={[
            { value: 'player', label: 'Player' },
            { value: 'admin', label: 'Admin' },
            { value: 'superuser', label: 'Support' },
          ]}
        />
      ) : (
        <Body muted>They'll join as a player. Support can make someone an admin.</Body>
      )}
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <View style={styles.actions}>
        <Button label="Send invite" onPress={submit} busy={busy} />
        <Button label="Cancel" variant="outline" onPress={() => setOpen(false)} />
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  list: { gap: Spacing.sm },
  row: { gap: 4, paddingVertical: 12 },
  rowHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' },
  name: { fontSize: 16, fontWeight: '700', flexShrink: 1 },
  small: { fontSize: 13 },
  form: { gap: Spacing.md },
  actions: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
});
