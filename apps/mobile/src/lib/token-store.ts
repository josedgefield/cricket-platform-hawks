import * as SecureStore from 'expo-secure-store';

// iOS Keychain / Android Keystore. The web build uses token-store.web.ts instead.
const KEY = 'hawks.session';

export async function loadSession(): Promise<string | null> {
  return SecureStore.getItemAsync(KEY);
}

export async function saveSession(value: string): Promise<void> {
  await SecureStore.setItemAsync(KEY, value);
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY);
}
