import { Link } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Body, Card, Chip, EmptyState, ErrorState, Loading, Screen, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { API_URL, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { canManageMembers } from '@/lib/members';
import { relativeTime } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

function Notices() {
  const t = useTheme();
  const auth = useAuth();
  const notices = useAsync(() => api.notices(), []);
  const isAdmin = auth.status === 'signedIn' && canManageMembers(auth.member.role);
  return (
    <View style={{ gap: Spacing.sm }}>
      <SectionTitle
        right={
          isAdmin ? (
            <Link href="/admin/notices/new" style={{ color: t.link, fontWeight: '700' }}>
              <Text>New notice</Text>
            </Link>
          ) : null
        }>
        Notices
      </SectionTitle>
      {notices.status === 'loading' ? <Loading label="Loading notices…" /> : null}
      {notices.status === 'error' ? <ErrorState message={notices.message} onRetry={notices.reload} /> : null}
      {notices.status === 'success' && notices.data.length === 0 ? (
        <EmptyState title="No notices yet" message="Club news and announcements will appear here." />
      ) : null}
      {notices.status === 'success'
        ? notices.data.map((n) => (
            <Link key={n.id} href={`/notices/${n.id}`} asChild>
              <Pressable accessibilityRole="link" accessibilityLabel={`${n.unread ? 'New. ' : ''}${n.title}`}>
                <Card style={{ gap: 4 }}>
                  <View style={styles.noticeHead}>
                    {n.pinned ? <Chip label="Pinned" tone="gold" /> : null}
                    {n.unread ? <Chip label="New" tone="navy" /> : null}
                  </View>
                  <Text style={[styles.cardTitle, { color: t.text, marginBottom: 0 }]}>{n.title}</Text>
                  <Body muted numberOfLines={2}>
                    {n.body}
                  </Body>
                  <Body muted style={{ fontSize: 13 }}>
                    {n.authorName} · {relativeTime(n.createdAt)}
                    {n.replyCount > 0 ? ` · ${n.replyCount} ${n.replyCount === 1 ? 'reply' : 'replies'}` : ''}
                  </Body>
                </Card>
              </Pressable>
            </Link>
          ))
        : null}
    </View>
  );
}

export default function HomeScreen() {
  const t = useTheme();
  const server = useAsync(() => api.sources(), []);

  return (
    <Screen title="Hawks CC" subtitle="Fly high, play hard, together.">
      <Card accent>
        <SectionTitle>Club server</SectionTitle>
        {server.status === 'loading' ? <Body muted>Checking…</Body> : null}
        {server.status === 'error' ? (
          <View style={{ gap: Spacing.xs }}>
            <Chip label="Offline" tone="muted" />
            <Body muted>{server.message}</Body>
            <Pressable accessibilityRole="button" onPress={server.reload}>
              <Text style={{ color: t.link, fontWeight: '700' }}>Try again</Text>
            </Pressable>
          </View>
        ) : null}
        {server.status === 'success' ? (
          <View style={{ gap: Spacing.xs }}>
            <Chip label="Connected" tone="gold" />
            {server.data.map((s) => (
              <Body key={s.source} muted>
                {s.source === 'sca' ? 'SCA' : 'CricHeroes'}:{' '}
                {s.lastSucceededAt ? `updated ${relativeTime(s.lastSucceededAt)}` : 'not imported yet'}
              </Body>
            ))}
          </View>
        ) : null}
        <Body muted style={styles.small}>{API_URL}</Body>
      </Card>

      <Link href="/stats" asChild>
        <Pressable accessibilityRole="link">
          <Card>
            <Text style={[styles.cardTitle, { color: t.text }]}>Player stats →</Text>
            <Body muted>Batting, bowling and fielding across SCA and BPL, and the points table.</Body>
          </Card>
        </Pressable>
      </Link>

      <Notices />
    </Screen>
  );
}

const styles = StyleSheet.create({
  small: { fontSize: 12, marginTop: Spacing.sm },
  noticeHead: { flexDirection: 'row', gap: Spacing.sm },
  cardTitle: { fontSize: 17, fontWeight: '800', marginBottom: 4 },
});
