import { Tabs, TabList, TabSlot, TabTrigger, type TabTriggerSlotProps } from 'expo-router/ui';
import { StatusBar } from 'expo-status-bar';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useTheme } from '@/components/ui';
import { MinTouch } from '@/constants/theme';

/**
 * One tab implementation for iOS, Android and web (expo-router's headless tabs),
 * styled like the prototype's bottom bar.
 */
export default function RootLayout() {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.root, { backgroundColor: t.background }]}>
      <StatusBar style="auto" />
      <Tabs>
        <TabSlot style={styles.slot} />
        <TabList
          style={[
            styles.tabBar,
            { backgroundColor: t.tabBar, borderTopColor: t.border, paddingBottom: Math.max(insets.bottom, 6) },
          ]}>
          <TabTrigger name="home" href="/" asChild>
            <TabButton>Home</TabButton>
          </TabTrigger>
          <TabTrigger name="matches" href="/matches" asChild>
            <TabButton>Matches</TabButton>
          </TabTrigger>
          <TabTrigger name="stats" href="/stats" asChild>
            <TabButton>Stats</TabButton>
          </TabTrigger>
          <TabTrigger name="money" href="/money" asChild>
            <TabButton>Money</TabButton>
          </TabTrigger>
          <TabTrigger name="more" href="/more" asChild>
            <TabButton>More</TabButton>
          </TabTrigger>
        </TabList>
      </Tabs>
    </View>
  );
}

function TabButton({ children, isFocused, ...props }: TabTriggerSlotProps) {
  const t = useTheme();
  return (
    <Pressable
      {...props}
      accessibilityRole="tab"
      accessibilityState={{ selected: !!isFocused }}
      style={({ pressed }) => [styles.tab, pressed && styles.pressed]}>
      <View style={[styles.indicator, { backgroundColor: isFocused ? t.accent : 'transparent' }]} />
      <Text style={[styles.tabLabel, { color: isFocused ? t.text : t.textSecondary }]}>{children}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  slot: { flex: 1 },
  tabBar: { flexDirection: 'row', borderTopWidth: 1, paddingTop: 2 },
  tab: { flex: 1, minHeight: MinTouch, alignItems: 'center', justifyContent: 'center', gap: 4 },
  indicator: { width: 28, height: 3, borderRadius: 2 },
  tabLabel: { fontSize: 12, fontWeight: '700' },
  pressed: { opacity: 0.7 },
});
