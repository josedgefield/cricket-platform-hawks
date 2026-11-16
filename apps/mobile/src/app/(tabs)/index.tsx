import { Link } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Body, Card, Chip, Screen, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { API_URL, api } from '@/lib/api';
import { relativeTime } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

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
            <Body muted>BPL 2025 batting, bowling, fielding and the points table, live from the club server.</Body>
          </Card>
        </Pressable>
      </Link>

      <SectionTitle>Coming next</SectionTitle>
      <Card>
        <Body>Your balance, payments and fixtures arrive once member sign-in is built. Until then this app shows public club stats only.</Body>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  small: { fontSize: 12, marginTop: Spacing.sm },
  cardTitle: { fontSize: 17, fontWeight: '800', marginBottom: 4 },
});
