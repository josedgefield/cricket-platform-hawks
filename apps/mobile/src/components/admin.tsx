import { Link, Redirect } from 'expo-router';
import type { ReactNode } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Chip, Loading, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import type { Member } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { canManageMembers, roleLabel, statusLabel } from '@/lib/members';

/** Admin screens: signed-out visitors go to sign-in, players back home. The server enforces this too. */
export function AdminOnly({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.status === 'loading') return <Loading />;
  if (auth.status === 'signedOut') return <Redirect href="/sign-in" />;
  if (!canManageMembers(auth.member.role)) return <Redirect href="/" />;
  return <>{children}</>;
}

export function BackLink({ href, label }: { href: '/more' | '/admin/users'; label: string }) {
  const t = useTheme();
  return (
    <Link href={href} style={[styles.back, { color: t.link }]}>
      <Text>← {label}</Text>
    </Link>
  );
}

export function RoleChip({ member }: { member: Pick<Member, 'role'> }) {
  return <Chip label={roleLabel(member.role)} tone={member.role === 'player' ? 'muted' : 'gold'} />;
}

export function StatusChip({ member }: { member: Pick<Member, 'status'> }) {
  return <Chip label={statusLabel(member.status)} tone={member.status === 'active' ? 'navy' : 'muted'} />;
}

const styles = StyleSheet.create({
  back: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
