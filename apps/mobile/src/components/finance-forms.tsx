import { useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Body, Button, Card, Field, Notice, SectionTitle, Segmented } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { type AdjustmentKind, api, newRequestKey, type PaymentMethod, type Statement } from '@/lib/api';
import { isIsoDate, money, parseCents } from '@/lib/format';

type Result = { tone: 'success' | 'error'; text: string } | null;

/**
 * One request key per form fill: a retry after an error or timeout reuses it, so the server
 * never records the same charge or payment twice. A new key is issued after a success.
 */
function useRequestKey() {
  const key = useRef(newRequestKey());
  return { current: () => key.current, renew: () => (key.current = newRequestKey()) };
}

function message(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** Raise one charge for the people given, or for everyone who isn't deactivated. */
export function ChargeForm({
  memberIds,
  everyone,
  who,
  onDone,
}: {
  memberIds?: string[];
  everyone?: boolean;
  /** Who will be charged, in words, for the button and the confirmation. */
  who: string;
  onDone: () => void;
}) {
  const key = useRequestKey();
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [due, setDue] = useState('');
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    const cents = parseCents(amount);
    if (!description.trim()) return setResult({ tone: 'error', text: 'Say what the charge is for.' });
    if (cents === null) return setResult({ tone: 'error', text: 'Enter an amount such as 25 or 25.50.' });
    if (due.trim() && !isIsoDate(due)) return setResult({ tone: 'error', text: 'Write the due date as YYYY-MM-DD, e.g. 2026-12-31.' });
    if (!everyone && (!memberIds || memberIds.length === 0)) return setResult({ tone: 'error', text: 'Choose at least one member.' });
    setBusy(true);
    setResult(null);
    try {
      const raised = await api.raiseCharges({
        memberIds: everyone ? undefined : memberIds,
        everyone: everyone || undefined,
        description: description.trim(),
        amountCents: cents,
        dueDate: due.trim() || undefined,
        requestKey: key.current(),
      });
      key.renew();
      setDescription('');
      setAmount('');
      setDue('');
      setResult({ tone: 'success', text: `Charged ${money(cents)} to ${raised.count} ${raised.count === 1 ? 'member' : 'members'}.` });
      onDone();
    } catch (e) {
      setResult({ tone: 'error', text: message(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Add a charge</SectionTitle>
      <Field label="What for" value={description} onChangeText={setDescription} placeholder="e.g. Season fee 2026" maxLength={140} />
      <Field label="Amount (S$)" value={amount} onChangeText={setAmount} keyboardType="decimal-pad" inputMode="decimal" placeholder="0.00" />
      <Field label="Due date (optional)" value={due} onChangeText={setDue} placeholder="YYYY-MM-DD" autoCapitalize="none" />
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label={`Charge ${who}`} onPress={submit} busy={busy} />
    </Card>
  );
}

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'paynow', label: 'PayNow' },
  { value: 'bank', label: 'Bank' },
  { value: 'cash', label: 'Cash' },
  { value: 'other', label: 'Other' },
];

/** Record money received from one member. */
export function PaymentForm({ memberId, onDone }: { memberId: string; onDone: (s: Statement) => void }) {
  const key = useRequestKey();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('paynow');
  const [received, setReceived] = useState('');
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    const cents = parseCents(amount);
    if (cents === null) return setResult({ tone: 'error', text: 'Enter the amount received, such as 25 or 25.50.' });
    if (received.trim() && !isIsoDate(received)) return setResult({ tone: 'error', text: 'Write the date as YYYY-MM-DD, or leave it empty for today.' });
    setBusy(true);
    setResult(null);
    try {
      const statement = await api.recordPayment({
        memberId,
        amountCents: cents,
        method,
        receivedOn: received.trim() || undefined,
        externalRef: reference.trim() || undefined,
        note: note.trim() || undefined,
        requestKey: key.current(),
      });
      key.renew();
      setAmount('');
      setReceived('');
      setReference('');
      setNote('');
      setResult({ tone: 'success', text: `Recorded ${money(cents)}.` });
      onDone(statement);
    } catch (e) {
      setResult({ tone: 'error', text: message(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <SectionTitle>Record a payment</SectionTitle>
      <Field label="Amount received (S$)" value={amount} onChangeText={setAmount} keyboardType="decimal-pad" inputMode="decimal" placeholder="0.00" />
      <Segmented label="Method" value={method} onChange={setMethod} options={METHODS} />
      <Field label="Date received (optional)" value={received} onChangeText={setReceived} placeholder="YYYY-MM-DD, empty for today" autoCapitalize="none" />
      <Field label="Bank reference (optional)" value={reference} onChangeText={setReference} autoCapitalize="none" maxLength={80} />
      <Field label="Note (optional)" value={note} onChangeText={setNote} hint="Only admins see this." maxLength={300} />
      <Body muted style={styles.small}>
        The money pays the charge that is due first. Anything left over is kept for the next charge.
      </Body>
      {result ? <Notice tone={result.tone}>{result.text}</Notice> : null}
      <Button label="Record payment" onPress={submit} busy={busy} />
    </Card>
  );
}

/**
 * A correction that needs a reason: the button opens a small form, and nothing is saved until
 * the reason is given and confirmed.
 */
export function AdjustAction({
  label,
  confirmLabel,
  explain,
  kind,
  target,
  withAmount,
  danger,
  onDone,
}: {
  label: string;
  confirmLabel: string;
  explain: string;
  kind: AdjustmentKind;
  target: { chargeId?: string; paymentId?: string; memberId?: string };
  /** 'optional': empty means all that is left. 'required': must be entered. */
  withAmount?: 'optional' | 'required';
  danger?: boolean;
  onDone: (s: Statement) => void;
}) {
  const key = useRequestKey();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return <Button label={label} variant="outline" onPress={() => setOpen(true)} />;
  }

  const submit = async () => {
    const cents = amount.trim() ? parseCents(amount) : undefined;
    if (!reason.trim()) return setError('Give a reason. It is kept with the record.');
    if (cents === null || (withAmount === 'required' && cents === undefined)) return setError('Enter an amount such as 25 or 25.50.');
    setBusy(true);
    setError(null);
    try {
      const statement = await api.adjust({ kind, ...target, amountCents: cents, reason: reason.trim(), requestKey: key.current() });
      key.renew();
      setOpen(false);
      setReason('');
      setAmount('');
      onDone(statement);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={styles.adjust}>
      <Body muted style={styles.small}>
        {explain}
      </Body>
      {withAmount ? (
        <Field
          label={withAmount === 'optional' ? 'Amount (S$, empty for all of it)' : 'Amount (S$)'}
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
          inputMode="decimal"
          placeholder="0.00"
        />
      ) : null}
      <Field label="Reason" value={reason} onChangeText={setReason} maxLength={300} />
      {error ? <Notice tone="error">{error}</Notice> : null}
      <View style={styles.actions}>
        <Button label={confirmLabel} variant={danger ? 'danger' : 'navy'} onPress={submit} busy={busy} />
        <Button label="Back" variant="outline" onPress={() => setOpen(false)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  small: { fontSize: 13 },
  adjust: { gap: Spacing.sm, marginTop: Spacing.xs },
  actions: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
});
