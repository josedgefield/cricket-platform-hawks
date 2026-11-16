import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { BackLink } from '@/components/admin';
import { Body, Button, Card, Chip, EmptyState, ErrorState, Field, Loading, Notice as Message, Screen, SectionTitle, useTheme } from '@/components/ui';
import { MinTouch, Radius, Spacing } from '@/constants/theme';
import { api, type Notice, type NoticeReply, type NoticeWithReplies, type ReactionKey } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { relativeTime } from '@/lib/format';
import { canManageMembers } from '@/lib/members';
import { useAsync } from '@/lib/use-async';

// Labelled, never icon-only (docs/07).
const REACTIONS: { key: ReactionKey; icon: string; label: string }[] = [
  { key: 'like', icon: '👍', label: 'Like' },
  { key: 'done', icon: '✅', label: 'Done' },
  { key: 'cricket', icon: '🏏', label: 'Howzat' },
  { key: 'clap', icon: '👏', label: 'Well done' },
  { key: 'question', icon: '❓', label: 'Question' },
];

function message(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function NoticeScreen() {
  const auth = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  const loaded = useAsync(() => api.notice(id), [id]);
  const [data, setData] = useState<NoticeWithReplies | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fetched = loaded.status === 'success' ? loaded.data : null;
  useEffect(() => {
    if (fetched) setData(fetched);
  }, [fetched]);

  if (auth.status === 'loading') return <Loading />;
  if (auth.status === 'signedOut') return <Redirect href="/sign-in" />;
  const isAdmin = canManageMembers(auth.member.role);

  /** Runs an action that returns the updated notice, showing the server's message if it fails. */
  const run = async (action: () => Promise<NoticeWithReplies>) => {
    setError(null);
    try {
      setData(await action());
      return true;
    } catch (e) {
      setError(message(e));
      return false;
    }
  };

  return (
    <Screen title={data?.notice.title ?? 'Notice'}>
      <BackLink href="/" label="Home" />
      {loaded.status === 'loading' && !data ? <Loading label="Opening the notice…" /> : null}
      {loaded.status === 'error' ? <ErrorState message={loaded.message} onRetry={loaded.reload} /> : null}
      {data ? (
        <>
          <NoticeBody notice={data.notice} />
          {error ? <Message tone="error">{error}</Message> : null}
          <Reactions notice={data.notice} onReact={(r) => run(() => api.react(id, r))} />
          {isAdmin ? <AdminTools notice={data.notice} onChange={(n) => setData({ ...data, notice: n })} /> : null}
          <Replies
            replies={data.replies}
            isAdmin={isAdmin}
            onReply={(text) => run(() => api.reply(id, text))}
            onHide={(replyId, reason) => run(() => api.hideReply(id, replyId, reason))}
          />
        </>
      ) : null}
    </Screen>
  );
}

function NoticeBody({ notice }: { notice: Notice }) {
  return (
    <Card accent style={styles.card}>
      {notice.pinned ? <Chip label="Pinned" tone="gold" /> : null}
      <Body>{notice.body}</Body>
      <Body muted style={styles.small}>
        {notice.authorName} · {relativeTime(notice.createdAt)}
        {notice.editedAt ? ` · edited ${relativeTime(notice.editedAt)}` : ''}
      </Body>
    </Card>
  );
}

function Reactions({ notice, onReact }: { notice: Notice; onReact: (r: ReactionKey | null) => void }) {
  const t = useTheme();
  return (
    <View style={styles.reactions} accessibilityLabel="Reactions">
      {REACTIONS.map((r) => {
        const mine = notice.myReaction === r.key;
        const count = notice.reactions[r.key] ?? 0;
        return (
          <Pressable
            key={r.key}
            accessibilityRole="button"
            accessibilityState={{ selected: mine }}
            accessibilityLabel={`${r.label}, ${count}${mine ? ', your reaction' : ''}`}
            onPress={() => onReact(mine ? null : r.key)}
            style={({ pressed }) => [
              styles.reaction,
              { borderColor: mine ? t.primary : t.border, backgroundColor: mine ? t.muted : t.card },
              pressed && { opacity: 0.7 },
            ]}>
            <Text style={[styles.reactionText, { color: t.text }]}>
              {r.icon} {r.label}
              {count > 0 ? ` ${count}` : ''}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function AdminTools({ notice, onChange }: { notice: Notice; onChange: (n: Notice) => void }) {
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [title, setTitle] = useState(notice.title);
  const [body, setBody] = useState(notice.body);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (action: () => Promise<Notice | void>) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await action();
      if (updated) {
        onChange(updated);
        setEditing(false);
      } else {
        router.replace('/');
      }
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  const email = notice.email;
  return (
    <Card style={styles.card}>
      <SectionTitle>Admin</SectionTitle>
      <Body muted>
        Opened by {notice.readCount ?? 0} {notice.readCount === 1 ? 'member' : 'members'}.
        {email
          ? email.status === 'sending'
            ? ' The email copy is being sent.'
            : ` Emailed to ${email.sent ?? 0}${email.failed ? `, ${email.failed} failed` : ''}.`
          : ''}
      </Body>
      {error ? <Message tone="error">{error}</Message> : null}
      {editing ? (
        <>
          <Field label="Title" value={title} onChangeText={setTitle} maxLength={120} />
          <Field label="Message" value={body} onChangeText={setBody} multiline numberOfLines={6} maxLength={5000} />
          <View style={styles.actions}>
            <Button label="Save changes" busy={busy} onPress={() => act(() => api.editNotice(notice.id, { title: title.trim(), body: body.trim() }))} />
            <Button label="Back" variant="outline" onPress={() => setEditing(false)} />
          </View>
        </>
      ) : confirming ? (
        <>
          <Body>Withdraw this notice? Members will no longer see it. It is kept in the club's records.</Body>
          <View style={styles.actions}>
            <Button label="Yes, withdraw" variant="danger" busy={busy} onPress={() => act(() => api.withdrawNotice(notice.id))} />
            <Button label="Keep" variant="outline" onPress={() => setConfirming(false)} />
          </View>
        </>
      ) : (
        <View style={styles.actions}>
          <Button label="Edit" variant="outline" onPress={() => setEditing(true)} />
          <Button
            label={notice.pinned ? 'Unpin' : 'Pin to top'}
            variant="outline"
            busy={busy}
            onPress={() => act(() => api.editNotice(notice.id, { pinned: !notice.pinned }))}
          />
          <Button label="Withdraw" variant="outline" onPress={() => setConfirming(true)} />
        </View>
      )}
    </Card>
  );
}

function Replies({
  replies,
  isAdmin,
  onReply,
  onHide,
}: {
  replies: NoticeReply[];
  isAdmin: boolean;
  onReply: (text: string) => Promise<boolean>;
  onHide: (replyId: string, reason?: string) => Promise<boolean>;
}) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  const send = async () => {
    if (!text.trim()) return;
    setBusy(true);
    if (await onReply(text.trim())) setText('');
    setBusy(false);
  };

  return (
    <View style={styles.list}>
      <SectionTitle>Replies</SectionTitle>
      {replies.length === 0 ? <EmptyState title="No replies yet" message="Be the first to reply." /> : null}
      {replies.map((r) => (
        <ReplyRow key={r.id} reply={r} isAdmin={isAdmin} onHide={onHide} />
      ))}
      <Card style={styles.card}>
        <Field label="Your reply" value={text} onChangeText={setText} multiline maxLength={1000} hint="Everyone in the club can read replies." />
        <Button label="Reply" onPress={send} busy={busy} disabled={!text.trim()} />
      </Card>
    </View>
  );
}

function ReplyRow({ reply, isAdmin, onHide }: { reply: NoticeReply; isAdmin: boolean; onHide: (replyId: string, reason?: string) => Promise<boolean> }) {
  const t = useTheme();
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const hide = async (why?: string) => {
    setBusy(true);
    await onHide(reply.id, why);
    setBusy(false);
    setAsking(false);
  };

  return (
    <Card style={styles.reply}>
      <Body muted style={styles.small}>
        <Text style={{ fontWeight: '700', color: t.text }}>{reply.mine ? 'You' : reply.authorName}</Text> · {relativeTime(reply.createdAt)}
      </Body>
      {reply.hidden ? (
        <Body muted>Removed{reply.hiddenReason ? `: ${reply.hiddenReason}` : ''}</Body>
      ) : (
        <Body>{reply.body}</Body>
      )}
      {!reply.hidden && reply.mine && !asking ? <Button label="Remove" variant="outline" busy={busy} onPress={() => hide()} /> : null}
      {!reply.hidden && !reply.mine && isAdmin && !asking ? <Button label="Hide" variant="outline" onPress={() => setAsking(true)} /> : null}
      {asking ? (
        <>
          <Field label="Why is this reply being hidden?" value={reason} onChangeText={setReason} maxLength={300} />
          <View style={styles.actions}>
            <Button label="Hide reply" variant="danger" busy={busy} disabled={!reason.trim()} onPress={() => hide(reason.trim())} />
            <Button label="Back" variant="outline" onPress={() => setAsking(false)} />
          </View>
        </>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: Spacing.md },
  list: { gap: Spacing.sm },
  small: { fontSize: 13 },
  reactions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm },
  reaction: { minHeight: MinTouch, paddingHorizontal: 12, borderWidth: 1, borderRadius: Radius.full, justifyContent: 'center' },
  reactionText: { fontSize: 14, fontWeight: '600' },
  actions: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
  reply: { gap: 6, paddingVertical: 12 },
});
