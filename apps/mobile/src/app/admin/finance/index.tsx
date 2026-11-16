import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AdminOnly, BackLink } from '@/components/admin';
import { ChargeForm } from '@/components/finance-forms';
import { Body, Button, Card, Chip, EmptyState, ErrorState, Loading, Screen, SectionTitle, Segmented, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api, type FinanceOverview, type MemberBalance } from '@/lib/api';
import { money } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

export default function FinanceScreen() {
  return (
    <AdminOnly>
      <Finance />
    </AdminOnly>
  );
}

type Show = 'owing' | 'all';

function Finance() {
  const overview = useAsync(() => api.financeOverview(), []);
  const [show, setShow] = useState<Show>('owing');
  const [charging, setCharging] = useState(false);

  return (
    <Screen title="Club finances" subtitle="Balances are worked out from every charge and payment.">
      <BackLink href="/more" label="More" />
      {overview.status === 'loading' ? <Loading label="Adding up the ledger…" /> : null}
      {overview.status === 'error' ? <ErrorState message={overview.message} onRetry={overview.reload} /> : null}
      {overview.status === 'success' ? (
        <>
          <Totals overview={overview.data} />
          {charging ? (
            <>
              <ChargeForm everyone who="everyone" onDone={overview.reload} />
              <Body muted style={styles.small}>
                Everyone means all members who aren't deactivated, including those who haven't accepted their invite.
                To charge one person, open them below.
              </Body>
              <Button label="Close" variant="outline" onPress={() => setCharging(false)} />
            </>
          ) : (
            <Button label="Charge everyone" variant="gold" onPress={() => setCharging(true)} />
          )}

          <SectionTitle>Members</SectionTitle>
          <Segmented
            label="Show"
            value={show}
            onChange={setShow}
            options={[
              { value: 'owing', label: 'Owing' },
              { value: 'all', label: 'Everyone' },
            ]}
          />
          <Members members={overview.data.members} show={show} />
        </>
      ) : null}
    </Screen>
  );
}

function Totals({ overview }: { overview: FinanceOverview }) {
  const t = useTheme();
  const cells: [string, number][] = [
    ['Owed to the club', overview.owedCents],
    ['Overdue', overview.overdueCents],
    ['Collected', overview.collectedCents],
    ['Paid in advance', overview.unusedCents],
  ];
  return (
    <Card accent style={styles.totals}>
      {cells.map(([label, cents]) => (
        <View key={label} style={styles.total}>
          <Text style={[styles.totalValue, { color: t.text }]}>{money(cents)}</Text>
          <Text style={[styles.totalLabel, { color: t.textSecondary }]}>{label}</Text>
        </View>
      ))}
    </Card>
  );
}

function Members({ members, show }: { members: MemberBalance[]; show: Show }) {
  const t = useTheme();
  const rows = members
    .filter((m) => show === 'all' || m.owedCents > 0)
    .sort((a, b) => b.overdueCents - a.overdueCents || b.balanceCents - a.balanceCents || a.displayName.localeCompare(b.displayName));
  if (rows.length === 0) {
    return show === 'owing' ? (
      <EmptyState title="Nobody owes anything" message="Every charge has been paid, waived or cancelled." />
    ) : (
      <EmptyState title="No members yet" message="Invite members from More → Manage users." />
    );
  }
  return (
    <View style={styles.list}>
      {rows.map((m) => (
        <Pressable
          key={m.memberId}
          accessibilityRole="button"
          accessibilityLabel={`${m.displayName}, ${m.balanceCents >= 0 ? 'owes' : 'in credit'} ${money(Math.abs(m.balanceCents))}`}
          onPress={() => router.push(`/admin/finance/${m.memberId}`)}
          style={({ pressed }) => [pressed && { opacity: 0.7 }]}>
          <Card style={styles.row}>
            <View style={styles.rowText}>
              <Text style={[styles.name, { color: t.text }]} numberOfLines={1}>
                {m.displayName}
              </Text>
              <View style={styles.chips}>
                {m.overdueCents > 0 ? <Chip label={`${money(m.overdueCents)} overdue`} tone="gold" /> : null}
                {m.memberStatus === 'deactivated' ? <Chip label="Deactivated" tone="muted" /> : null}
                {m.openCharges > 0 ? (
                  <Body muted style={styles.small}>
                    {m.openCharges} unpaid {m.openCharges === 1 ? 'charge' : 'charges'}
                  </Body>
                ) : null}
              </View>
            </View>
            <View style={styles.rowAmount}>
              <Text style={[styles.amount, { color: t.text }]}>{money(Math.abs(m.balanceCents))}</Text>
              <Body muted style={styles.small}>
                {m.balanceCents > 0 ? 'owed' : m.balanceCents < 0 ? 'credit' : 'settled'}
              </Body>
            </View>
          </Card>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  totals: { flexDirection: 'row', flexWrap: 'wrap', rowGap: Spacing.md },
  total: { width: '50%', gap: 2 },
  totalValue: { fontSize: 20, fontWeight: '800', fontVariant: ['tabular-nums'] },
  totalLabel: { fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5 },
  list: { gap: Spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: Spacing.md, paddingVertical: 12 },
  rowText: { flex: 1, gap: 4 },
  rowAmount: { alignItems: 'flex-end' },
  name: { fontSize: 16, fontWeight: '700' },
  chips: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' },
  amount: { fontSize: 17, fontWeight: '800', fontVariant: ['tabular-nums'] },
  small: { fontSize: 13 },
});
