import { Body, Card, Screen } from '@/components/ui';
import { Spacing } from '@/constants/theme';

/** Honest placeholder for a tab whose backend module doesn't exist yet. */
export function ComingSoon({ title, what, needs }: { title: string; what: string[]; needs: string }) {
  return (
    <Screen title={title}>
      <Card accent style={{ gap: Spacing.sm }}>
        <Body style={{ fontWeight: '700' }}>Not built yet</Body>
        {what.map((w) => (
          <Body key={w} muted>
            • {w}
          </Body>
        ))}
        <Body muted style={{ marginTop: Spacing.sm }}>
          Waiting on: {needs}
        </Body>
      </Card>
    </Screen>
  );
}
