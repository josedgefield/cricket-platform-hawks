import { Link } from 'expo-router';
import { StyleSheet, Text } from 'react-native';

import { BalanceCard, History, OpenCharges } from '@/components/finance';
import { Body, Card, ErrorState, Loading, Screen, SectionTitle, useTheme } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { canManageMembers } from '@/lib/members';
import { useAsync } from '@/lib/use-async';

export default function MoneyScreen() {
  const t = useTheme();
  const auth = useAuth();
  const statement = useAsync(() => api.myStatement(), []);
  const isAdmin = auth.status === 'signedIn' && canManageMembers(auth.member.role);

  return (
    <Screen title="Money" subtitle="What you owe the club, item by item.">
      {isAdmin ? (
        <Link href="/admin/finance" style={[styles.link, { color: t.link }]}>
          <Text>Club finances →</Text>
        </Link>
      ) : null}
      {statement.status === 'loading' ? <Loading label="Loading your statement…" /> : null}
      {statement.status === 'error' ? <ErrorState message={statement.message} onRetry={statement.reload} /> : null}
      {statement.status === 'success' ? (
        <>
          <BalanceCard statement={statement.data} who="you" />
          <OpenCharges statement={statement.data} />
          {statement.data.owedCents > 0 ? (
            <Card style={{ gap: Spacing.xs }}>
              <SectionTitle>How to pay</SectionTitle>
              <Body muted>
                Pay the club as you do today (PayNow or cash) and quote the charge reference. A club admin records
                it here, and your balance updates. Paying from inside the app is coming later.
              </Body>
            </Card>
          ) : null}
          <History statement={statement.data} />
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  link: { fontWeight: '700', fontSize: 15, paddingVertical: Spacing.xs },
});
