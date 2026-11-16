import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet } from 'react-native';

import { AdminOnly, BackLink } from '@/components/admin';
import { Body, Button, Card, Field, Notice, Screen, Toggle } from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api, newRequestKey } from '@/lib/api';

export default function NewNoticeScreen() {
  return (
    <AdminOnly>
      <NewNotice />
    </AdminOnly>
  );
}

function NewNotice() {
  // One key per form fill, so a retry after a timeout can't publish (or email) twice.
  const key = useRef(newRequestKey());
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [pinned, setPinned] = useState(false);
  const [emailMembers, setEmailMembers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const publish = async () => {
    if (!title.trim() || !body.trim()) {
      setError('Enter a title and a message.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const notice = await api.publishNotice({
        title: title.trim(),
        body: body.trim(),
        pinned,
        emailMembers,
        requestKey: key.current,
      });
      router.replace(`/notices/${notice.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  };

  return (
    <Screen title="New notice" subtitle="Every member sees it on their Home tab.">
      <BackLink href="/" label="Home" />
      <Card style={styles.card}>
        <Field label="Title" value={title} onChangeText={setTitle} maxLength={120} />
        <Field label="Message" value={body} onChangeText={setBody} multiline numberOfLines={6} maxLength={5000} />
        <Toggle label="Pin to the top of Home" value={pinned} onChange={setPinned} />
        <Toggle label="Also email it to active members" value={emailMembers} onChange={setEmailMembers} />
        {emailMembers ? <Body muted>The email can't be taken back once it is sent. Check the wording first.</Body> : null}
        {error ? <Notice tone="error">{error}</Notice> : null}
        <Button label="Publish" onPress={publish} busy={busy} />
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
});
