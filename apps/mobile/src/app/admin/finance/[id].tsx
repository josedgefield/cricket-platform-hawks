import { useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { AdminOnly, BackLink } from '@/components/admin';
import { BalanceCard, History, OpenCharges } from '@/components/finance';
import { AdjustAction, ChargeForm, PaymentForm } from '@/components/finance-forms';
import { Body, Card, Chip, ErrorState, Loading, Notice, Screen, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api, type PaymentLine, type Statement } from '@/lib/api';
import { money, shortDate } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

export default function MemberFinanceScreen() {
  return (
    <AdminOnly>
      <MemberFinance />
    </AdminOnly>
  );
}

function MemberFinance() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const loaded = useAsync(() => api.memberStatement(id), [id]);
  const [statement, setStatement] = useState<Statement | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const fetched = loaded.status === 'success' ? loaded.data : null;
  // Take each fresh load; after a change the server's updated statement replaces it.
  useEffect(() => {
    if (fetched) setStatement(fetched);
  }, [fetched]);

  const changed = (what: string) => (s: Statement) => {
    setStatement(s);
    setSaved(what);
  };

  return (
    <Screen title={statement?.displayName ?? 'Member'} subtitle="Charges, payments and corrections.">
      <BackLink href="/admin/finance" label="Club finances" />
      {loaded.status === 'loading' && !statement ? <Loading label="Loading the statement…" /> : null}
      {loaded.status === 'error' ? <ErrorState message={loaded.message} onRetry={loaded.reload} /> : null}
      {statement ? (
        <>
          <BalanceCard statement={statement} who="member" />
          {saved ? <Notice tone="success">{saved}</Notice> : null}

          <OpenCharges
            statement={statement}
            renderActions={(c) => (
              <View style={styles.actions}>
                <AdjustAction
                  label="Waive"
                  confirmLabel="Waive"
                  explain={`Forgive part or all of the ${money(c.outstandingCents)} still unpaid.`}
                  kind="waiver"
                  target={{ chargeId: c.id }}
                  withAmount="optional"
                  onDone={changed('Waiver saved.')}
                />
                <AdjustAction
                  label="Cancel charge"
                  confirmLabel="Cancel this charge"
                  explain="For a charge raised by mistake. Anything already paid towards it moves to their other charges."
                  kind="void"
                  target={{ chargeId: c.id }}
                  danger
                  onDone={changed('Charge cancelled.')}
                />
              </View>
            )}
          />

          <PaymentForm memberId={statement.memberId} onDone={changed('Payment recorded.')} />
          <ChargeForm memberIds={[statement.memberId]} who={statement.displayName} onDone={loaded.reload} />

          <Card style={styles.card}>
            <SectionTitle>Give credit</SectionTitle>
            <Body muted>
              Credit counts like a payment without money changing hands, e.g. for umpiring or buying kit for the club.
            </Body>
            <AdjustAction
              label="Give credit"
              confirmLabel="Give credit"
              explain="The credit pays their oldest unpaid charge first."
              kind="credit"
              target={{ memberId: statement.memberId }}
              withAmount="required"
              onDone={changed('Credit saved.')}
            />
          </Card>

          <Payments statement={statement} onChange={changed} />
          <History statement={statement} />
          <Body muted style={styles.small}>
            Nothing here is ever edited or deleted. A correction is a new line with its reason, so the history always
            adds up.
          </Body>
        </>
      ) : null}
    </Screen>
  );
}

function Payments({ statement, onChange }: { statement: Statement; onChange: (what: string) => (s: Statement) => void }) {
  if (statement.payments.length === 0) return null;
  return (
    <View style={styles.list}>
      <SectionTitle>Payments received</SectionTitle>
      {[...statement.payments].reverse().map((p) => (
        <PaymentRow key={p.id} payment={p} onChange={onChange} />
      ))}
    </View>
  );
}

function PaymentRow({ payment, onChange }: { payment: PaymentLine; onChange: (what: string) => (s: Statement) => void }) {
  const t = useTheme();
  return (
    <Card style={styles.row}>
      <View style={styles.rowHead}>
        <Text style={[styles.amount, { color: t.text }]}>{money(payment.amountCents)}</Text>
        {payment.reversed ? <Chip label="Cancelled" tone="muted" /> : null}
        {!payment.reversed && payment.refundedCents > 0 ? <Chip label={`${money(payment.refundedCents)} refunded`} tone="muted" /> : null}
      </View>
      <Body muted style={styles.small}>
        {payment.reference} · {payment.method} · {shortDate(payment.receivedOn)}
        {payment.externalRef ? ` · ${payment.externalRef}` : ''}
      </Body>
      {payment.note ? <Body muted style={styles.small}>Note: {payment.note}</Body> : null}
      {!payment.reversed ? (
        <View style={styles.actions}>
          {payment.unallocatedCents > 0 ? (
            <AdjustAction
              label="Refund unused"
              confirmLabel="Record refund"
              explain={`${money(payment.unallocatedCents)} of this payment isn't used. Record a refund after you have sent the money back.`}
              kind="refund"
              target={{ paymentId: payment.id }}
              withAmount="optional"
              onDone={onChange('Refund recorded.')}
            />
          ) : null}
          <AdjustAction
            label="Cancel payment"
            confirmLabel="Cancel this payment"
            explain="For a payment recorded by mistake (wrong member or amount). The charges it paid become unpaid again."
            kind="reversal"
            target={{ paymentId: payment.id }}
            danger
            onDone={onChange('Payment cancelled.')}
          />
        </View>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  list: { gap: Spacing.sm },
  row: { gap: 6, paddingVertical: 12 },
  rowHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' },
  amount: { fontSize: 18, fontWeight: '800', fontVariant: ['tabular-nums'] },
  small: { fontSize: 13 },
  actions: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap', marginTop: Spacing.xs },
});
