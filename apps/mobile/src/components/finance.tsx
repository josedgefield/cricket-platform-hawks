import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Body, Card, Chip, EmptyState, Notice, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import type { ChargeLine, ChargeStatus, Statement, StatementEntry } from '@/lib/api';
import { money, shortDate } from '@/lib/format';

const STATUS_LABEL: Record<ChargeStatus, string> = {
  open: 'Unpaid',
  part_paid: 'Part paid',
  paid: 'Paid',
  waived: 'Waived',
  void: 'Cancelled',
};

/** The headline: what is owed, or that nothing is. Never colour alone: the words say it too. */
export function BalanceCard({ statement, who }: { statement: Statement; who: 'you' | 'member' }) {
  const t = useTheme();
  const owes = statement.balanceCents > 0;
  const credit = statement.balanceCents < 0;
  const subject = who === 'you' ? 'You' : statement.displayName;
  return (
    <Card accent style={styles.card}>
      <Text style={[styles.caption, { color: t.textSecondary }]}>
        {owes ? `${subject} ${who === 'you' ? 'owe' : 'owes'}` : credit ? `${subject} ${who === 'you' ? 'are' : 'is'} in credit by` : 'Nothing to pay'}
      </Text>
      <Text style={[styles.balance, { color: t.text }]}>{money(Math.abs(statement.balanceCents))}</Text>
      {statement.overdueCents > 0 ? (
        <Notice tone="warning">{money(statement.overdueCents)} is past its due date.</Notice>
      ) : null}
      {statement.unusedCents > 0 ? (
        <Body muted>{money(statement.unusedCents)} paid in advance will go towards the next charge.</Body>
      ) : null}
    </Card>
  );
}

export function ChargeRow({ charge, actions }: { charge: ChargeLine; actions?: ReactNode }) {
  const t = useTheme();
  const settled = charge.outstandingCents === 0;
  return (
    <Card style={styles.row}>
      <View style={styles.rowHead}>
        <Text style={[styles.title, { color: t.text }]}>{charge.description}</Text>
        <Chip label={charge.overdue ? 'Overdue' : STATUS_LABEL[charge.status]} tone={charge.overdue ? 'gold' : settled ? 'muted' : 'navy'} />
      </View>
      <View style={styles.amounts}>
        <Text style={[styles.amount, { color: t.text }]}>{money(settled ? charge.amountCents : charge.outstandingCents)}</Text>
        {!settled && charge.outstandingCents !== charge.amountCents ? (
          <Body muted style={styles.small}>
            left of {money(charge.amountCents)}
          </Body>
        ) : null}
      </View>
      <Body muted style={styles.small}>
        {charge.reference}
        {charge.dueDate ? ` · due ${shortDate(charge.dueDate)}` : ''}
      </Body>
      {actions}
    </Card>
  );
}

/** Charges that still need paying, earliest due first. */
export function OpenCharges({ statement, renderActions }: { statement: Statement; renderActions?: (c: ChargeLine) => ReactNode }) {
  const open = statement.charges
    .filter((c) => c.outstandingCents > 0)
    .sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') || a.createdAt.localeCompare(b.createdAt));
  return (
    <View style={styles.list}>
      <SectionTitle>To pay</SectionTitle>
      {open.length === 0 ? (
        <EmptyState title="All paid up" message="There are no unpaid charges." />
      ) : (
        open.map((c) => <ChargeRow key={c.id} charge={c} actions={renderActions?.(c)} />)
      )}
    </View>
  );
}

function EntryRow({ entry }: { entry: StatementEntry }) {
  const t = useTheme();
  return (
    <View style={[styles.entry, { borderBottomColor: t.border }]}>
      <View style={styles.entryText}>
        <Text style={[styles.entryTitle, { color: t.text }]}>{entry.description}</Text>
        <Body muted style={styles.small}>
          {shortDate(entry.date)}
          {entry.reference ? ` · ${entry.reference}` : ''}
        </Body>
      </View>
      <View style={styles.entryNumbers}>
        <Text style={[styles.entryAmount, { color: t.text }]}>
          {entry.amountCents > 0 ? '+' : '−'}
          {money(Math.abs(entry.amountCents))}
        </Text>
        <Body muted style={styles.small}>
          {entry.balanceCents < 0 ? `${money(-entry.balanceCents)} credit` : `${money(entry.balanceCents)} owed`}
        </Body>
      </View>
    </View>
  );
}

/** Every ledger row, newest first, with the balance after each. */
export function History({ statement }: { statement: Statement }) {
  const entries = [...statement.entries].reverse();
  return (
    <View style={styles.list}>
      <SectionTitle>Statement</SectionTitle>
      {entries.length === 0 ? (
        <EmptyState title="Nothing yet" message="Charges and payments will be listed here." />
      ) : (
        <Card style={{ paddingVertical: 4 }}>
          {entries.map((e, i) => (
            <EntryRow key={`${e.at}-${i}`} entry={e} />
          ))}
        </Card>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.sm },
  caption: { fontSize: 13, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5 },
  balance: { fontSize: 36, fontWeight: '800', fontVariant: ['tabular-nums'] },
  list: { gap: Spacing.sm },
  row: { gap: 6, paddingVertical: 12 },
  rowHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm },
  title: { fontSize: 16, fontWeight: '700', flexShrink: 1 },
  amounts: { flexDirection: 'row', alignItems: 'baseline', gap: Spacing.sm },
  amount: { fontSize: 20, fontWeight: '800', fontVariant: ['tabular-nums'] },
  small: { fontSize: 13 },
  entry: { flexDirection: 'row', justifyContent: 'space-between', gap: Spacing.md, paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth },
  entryText: { flex: 1, gap: 2 },
  entryTitle: { fontSize: 15, fontWeight: '600' },
  entryNumbers: { alignItems: 'flex-end', gap: 2 },
  entryAmount: { fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'] },
});
