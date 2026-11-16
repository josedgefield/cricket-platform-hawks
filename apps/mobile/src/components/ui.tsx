import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  type TextInputProps,
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
  disabled,
  busy,
}: {
  label: string;
  onPress: () => void;
  variant?: 'navy' | 'gold' | 'outline' | 'danger';
  style?: ViewProps['style'];
  disabled?: boolean;
  /** Shows a spinner and ignores presses while an action runs. */
  busy?: boolean;
}) {
  const t = useTheme();
  const bg =
    variant === 'gold' ? t.accent : variant === 'outline' ? 'transparent' : variant === 'danger' ? t.danger : t.primary;
  const fg = variant === 'gold' ? t.onAccent : variant === 'outline' ? t.text : variant === 'danger' ? '#FFFFFF' : t.onPrimary;
  const inactive = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!busy }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg },
        variant === 'outline' && { borderWidth: 1, borderColor: t.border },
        (pressed || inactive) && styles.pressed,
        style,
      ]}>
      {busy ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{label}</Text>}
    </Pressable>
  );
}

/** A labelled text input with an optional hint or error underneath. */
export function Field({
  label,
  hint,
  error,
  style,
  ...input
}: Omit<TextInputProps, 'style'> & { label: string; hint?: string; error?: string | null; style?: ViewProps['style'] }) {
  const t = useTheme();
  return (
    <View style={[styles.field, style]}>
      <Text style={[styles.fieldLabel, { color: t.text }]}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={t.textSecondary}
        style={[
          styles.input,
          { color: t.text, backgroundColor: t.card, borderColor: error ? t.danger : t.border },
        ]}
        {...input}
      />
      {error ? (
        <Text style={[styles.fieldNote, { color: t.danger }]}>{error}</Text>
      ) : hint ? (
        <Text style={[styles.fieldNote, { color: t.textSecondary }]}>{hint}</Text>
      ) : null}
    </View>
  );
}

/** A coloured message box for results of an action. */
export function Notice({ tone, children }: { tone: 'success' | 'error' | 'warning' | 'info'; children: ReactNode }) {
  const t = useTheme();
  const colors = {
    success: [t.successBg, t.success],
    error: [t.dangerBg, t.danger],
    warning: [t.warningBg, t.warning],
    info: [t.muted, t.border],
  }[tone];
  return (
    <View
      accessibilityLiveRegion="polite"
      accessibilityRole={tone === 'error' ? 'alert' : undefined}
      style={[styles.notice, { backgroundColor: colors[0], borderColor: colors[1] }]}>
      <Body>{children}</Body>
    </View>
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
  field: { gap: 6 },
  fieldLabel: { fontSize: 14, fontWeight: '700' },
  input: { minHeight: MinTouch, borderWidth: 1, borderRadius: Radius.md, paddingHorizontal: 12, fontSize: 16 },
  fieldNote: { fontSize: 13 },
  notice: { borderWidth: 1, borderRadius: Radius.md, padding: Spacing.md },
});
