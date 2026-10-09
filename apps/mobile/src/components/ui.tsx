import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  type TextProps,
  useColorScheme,
  View,
  type ViewProps,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Colors, MaxContentWidth, MinTouch, Radius, Spacing, type ThemeColors } from '@/constants/theme';

export function useTheme(): ThemeColors {
  const scheme = useColorScheme();
  return scheme === 'dark' ? Colors.dark : Colors.light;
}

/** Page shell: safe area, scroll, centred column on wide screens. */
export function Screen({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const t = useTheme();
  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={[styles.flex, { backgroundColor: t.background }]}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.column}>
          <Text accessibilityRole="header" style={[styles.title, { color: t.text }]}>
            {title}
          </Text>
          {subtitle ? <Text style={[styles.subtitle, { color: t.textSecondary }]}>{subtitle}</Text> : null}
          {children}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

export function Card({ style, accent, ...rest }: ViewProps & { accent?: boolean }) {
  const t = useTheme();
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: t.card, borderColor: t.border },
        accent && { borderTopWidth: 4, borderTopColor: t.accent },
        style,
      ]}
      {...rest}
    />
  );
}

export function Body({ style, muted, ...rest }: TextProps & { muted?: boolean }) {
  const t = useTheme();
  return <Text style={[styles.body, { color: muted ? t.textSecondary : t.text }, style]} {...rest} />;
}

export function SectionTitle({ children, right }: { children: ReactNode; right?: ReactNode }) {
  const t = useTheme();
  return (
    <View style={styles.sectionRow}>
      <Text accessibilityRole="header" style={[styles.section, { color: t.text }]}>
        {children}
      </Text>
      {right}
    </View>
  );
}

/** Segmented control (single choice). Exposed to screen readers as radio-like buttons. */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
  label: string;
}) {
  const t = useTheme();
  return (
    <View accessibilityLabel={label} style={[styles.segmented, { backgroundColor: t.muted }]}>
      {options.map((o) => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            onPress={() => onChange(o.value)}
            style={({ pressed }) => [
              styles.segment,
              selected && { backgroundColor: t.card },
              pressed && styles.pressed,
            ]}>
            <Text style={[styles.segmentText, { color: selected ? t.text : t.textSecondary }]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Chip({ label, tone = 'navy' }: { label: string; tone?: 'navy' | 'gold' | 'muted' }) {
  const t = useTheme();
  const bg = tone === 'navy' ? t.primary : tone === 'gold' ? t.accent : t.muted;
  const fg = tone === 'navy' ? t.onPrimary : tone === 'gold' ? t.onAccent : t.text;
  return (
    <View style={[styles.chip, { backgroundColor: bg }]}>
      <Text style={[styles.chipText, { color: fg }]}>{label}</Text>
    </View>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  const t = useTheme();
  return (
    <View style={styles.state} accessibilityLiveRegion="polite">
      <ActivityIndicator color={t.primary} />
      <Body muted>{label}</Body>
    </View>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  const t = useTheme();
  return (
    <Card style={{ backgroundColor: t.dangerBg, borderColor: t.danger }}>
      <Body style={{ fontWeight: '700' }}>Couldn't load this</Body>
      <Body muted style={{ marginTop: Spacing.xs }}>
        {message}
      </Body>
      <Button label="Try again" onPress={onRetry} style={{ marginTop: Spacing.md }} />
    </Card>
  );
}

export function EmptyState({ title, message }: { title: string; message: string }) {
  return (
    <Card>
      <Body style={{ fontWeight: '700' }}>{title}</Body>
      <Body muted style={{ marginTop: Spacing.xs }}>
        {message}
      </Body>
    </Card>
  );
}

export function Button({
  label,
  onPress,
  variant = 'navy',
  style,
}: {
  label: string;
  onPress: () => void;
  variant?: 'navy' | 'gold';
  style?: ViewProps['style'];
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: variant === 'gold' ? t.accent : t.primary },
        pressed && styles.pressed,
        style,
      ]}>
      <Text style={[styles.buttonText, { color: variant === 'gold' ? t.onAccent : t.onPrimary }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scrollContent: { paddingHorizontal: Spacing.md, paddingBottom: Spacing.xl, alignItems: 'center' },
  column: { width: '100%', maxWidth: MaxContentWidth, gap: Spacing.md, paddingTop: Spacing.md },
  title: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  subtitle: { fontSize: 15, marginTop: -Spacing.sm },
  card: { borderWidth: 1, borderRadius: Radius.lg, padding: Spacing.md },
  body: { fontSize: 15, lineHeight: 22 },
  sectionRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.sm },
  section: { fontSize: 18, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
  segmented: { flexDirection: 'row', padding: 4, borderRadius: 12, gap: 4 },
  segment: { flex: 1, minHeight: 40, borderRadius: 9, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
  segmentText: { fontSize: 14, fontWeight: '700' },
  chip: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: Radius.full, alignSelf: 'flex-start' },
  chipText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.3 },
  state: { alignItems: 'center', gap: Spacing.sm, paddingVertical: Spacing.xl },
  button: { minHeight: MinTouch, borderRadius: Radius.md, paddingHorizontal: Spacing.lg, alignItems: 'center', justifyContent: 'center', alignSelf: 'flex-start' },
  buttonText: { fontSize: 15, fontWeight: '700' },
  pressed: { opacity: 0.7 },
});
