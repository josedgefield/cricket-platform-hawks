import { Slot } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { useTheme } from '@/components/ui';
import { AuthProvider } from '@/lib/auth';

/**
 * Root: the session provider around every screen. The tabs live in (tabs) and need a
 * signed-in member; sign-in, invite and password screens sit outside them.
 */
export default function RootLayout() {
  const t = useTheme();
  return (
    <AuthProvider>
      <View style={{ flex: 1, backgroundColor: t.background }}>
        <StatusBar style="auto" />
        <Slot />
      </View>
    </AuthProvider>
  );
}
