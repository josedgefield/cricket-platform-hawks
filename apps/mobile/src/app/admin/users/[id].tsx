import { useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { AdminOnly, BackLink, RoleChip, StatusChip } from '@/components/admin';
import {
  Body,
  Button,
  Card,
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
import { api, type Member, type Role } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { relativeTime } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

type Result = { tone: 'success' | 'error' | 'warning'; text: string } | null;

export default function MemberScreen() {
  return (
    <AdminOnly>
      <MemberDetail />
    </AdminOnly>
  );
}

function MemberDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const loaded = useAsync(() => api.member(id), [id]);
  const [member, setMember] = useState<Member | null>(null);
  const fetched = loaded.status === 'success' ? loaded.data : null;
  // Take each fresh load; later edits replace it locally with the server's answer.
  useEffect(() => {
    if (fetched) setMember(fetched);
  }, [fetched]);

  return (
    <Screen title={member?.displayName ?? 'Member'}>
      <BackLink href="/admin/users" label="Users" />
      {loaded.status === 'loading' && !member ? <Loading /> : null}
      {loaded.status === 'error' ? <ErrorState message={loaded.message} onRetry={loaded.reload} /> : null}
      {member ? <Details member={member} onChange={setMember} /> : null}
    </Screen>
  );
}

function Details({ member, onChange }: { member: Member; onChange: (m: Member) => void }) {
  const t = useTheme();
  const auth = useAuth();
  const me = auth.status === 'signedIn' ? auth.member : null;
  const isSelf = me?.id === member.id;
  const canChangeRole = me?.role === 'superuser' && !isSelf;

  return (
    <View style={{ gap: Spacing.md }}>
      <Card accent style={styles.card}>
        <View style={styles.row}>
          <RoleChip member={member} />
          <StatusChip member={member} />
        </View>
        <Body>{member.email}</Body>
        {member.phone ? <Body muted>{member.phone}</Body> : null}
        <Body muted style={styles.small}>
          {member.status === 'invited'
            ? member.inviteSentAt
              ? `Invite sent ${relativeTime(member.inviteSentAt).toLowerCase()}; not accepted yet.`
              : 'Invite not sent yet.'
            : `Last signed in ${relativeTime(member.lastSignInAt).toLowerCase()}.`}
        </Body>
        {member.status === 'invited' && member.inviteEmailError ? (
          <Notice tone="warning">The last invite email failed: {member.inviteEmailError}</Notice>
        ) : null}
      </Card>

      {!member.manageable ? (
        <Notice tone="info">
          {isSelf
            ? 'This is you. Change your own details on the More tab.'
            : 'Only support (superusers) can change admins and superusers.'}
        </Notice>
      ) : (
        <>
          {isSelf ? (
            <Notice tone="info">This is you. You can edit your details here, but not your own role or status.</Notice>
          ) : null}
          <EditForm member={member} onChange={onChange} />
          {!isSelf ? <StatusActions member={member} onChange={onChange} /> : null}
          {canChangeRole ? <RoleForm member={member} onChange={onChange} /> : null}
        </>
      )}
      <Text style={{ color: t.textSecondary, fontSize: 13 }}>
        Deactivating keeps the member's history (payments, stats) but stops them signing in. You can reactivate them
        later.
      </Text>
    </View>
  );
}

function EditForm({ member, onChange }: { member: Member; onChange: (m: Member) => void }) {
  const [name, setName] = useState(member.displayName);
  const [email, setEmail] = useState(member.email);
  const [phone, setPhone] = useState(member.phone ?? '');
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);
  const emailEditable = member.status === 'invited';

  const save = async () => {
    setBusy(true);
    setResult(null);
    try {
      const updated = await api.updateMember(member.id, {
        displayName: name.trim(),
        phone: phone.trim(),
        email: emailEditable ? email.trim() : undefined,
      });
      onChange(updated);
      setResult(
        emailEditable && updated.email !== member.email
          ? { tone: 'success', text: `Saved. A new invite went to ${updated.email}; the old link no longer works.` }
          : { tone: 'success', text: 'Saved.' },
      );
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Details</SectionTitle>
      <Field label="Name" value={name} onChangeText={setName} />
      <Field
        label="Email"
        value={email}
        onChangeText={setEmail}
        editable={emailEditable}
        autoCapitalize="none"
        keyboardType="email-address"
        hint={emailEditable ? 'Fix a typo before they join.' : 'This is their sign-in. Ask support if it must change.'}
      />
      <Field label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" inputMode="tel" />
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label="Save details" onPress={save} busy={busy} />
    </Card>
  );
}

function StatusActions({ member, onChange }: { member: Member; onChange: (m: Member) => void }) {
  const [confirming, setConfirming] = useState(false);
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);

  const run = async (action: () => Promise<Member>, done: (m: Member) => string) => {
    setBusy(true);
    setResult(null);
    try {
      const updated = await action();
      onChange(updated);
      setResult(
        updated.inviteEmailError && updated.status === 'invited'
          ? { tone: 'warning', text: `The email failed: ${updated.inviteEmailError}` }
          : { tone: 'success', text: done(updated) },
      );
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Account</SectionTitle>
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <View style={styles.actions}>
        {member.status === 'invited' ? (
          <Button
            label="Resend invite"
            variant="gold"
            busy={busy}
            onPress={() => run(() => api.resendInvite(member.id), (m) => `New invite sent to ${m.email}.`)}
          />
        ) : null}
        {member.status === 'deactivated' ? (
          <Button
            label="Reactivate"
            busy={busy}
            onPress={() =>
              run(() => api.reactivate(member.id), (m) =>
                m.status === 'invited' ? 'Reactivated. They never joined, so send a new invite.' : 'Reactivated. They can sign in again.',
              )
            }
          />
        ) : confirming ? (
          <>
            <Button
              label="Yes, deactivate"
              variant="danger"
              busy={busy}
              onPress={() => run(() => api.deactivate(member.id), () => 'Deactivated. They have been signed out everywhere.')}
            />
            <Button label="Keep" variant="outline" onPress={() => setConfirming(false)} />
          </>
        ) : (
          <Button label="Deactivate" variant="outline" onPress={() => setConfirming(true)} />
        )}
      </View>
    </Card>
  );
}

function RoleForm({ member, onChange }: { member: Member; onChange: (m: Member) => void }) {
  const [role, setRole] = useState<Role>(member.role);
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setBusy(true);
    setResult(null);
    try {
      const updated = await api.changeRole(member.id, role);
      onChange(updated);
      setResult({ tone: 'success', text: 'Role changed. They will need to sign in again.' });
    } catch (e) {
      setResult({ tone: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Role (support only)</SectionTitle>
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
      <Body muted style={styles.small}>
        Admins see finance and manage players. Support can also manage admins and change roles.
      </Body>
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label="Change role" onPress={save} busy={busy} disabled={role === member.role} />
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  row: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
  small: { fontSize: 13 },
  actions: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
});
