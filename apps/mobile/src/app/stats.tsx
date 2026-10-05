import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import {
  Body,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  Loading,
  Screen,
  SectionTitle,
  Segmented,
  useTheme,
} from '@/components/ui';
import { Spacing } from '@/constants/theme';
import { api, type PlayerStats, type SourceCode } from '@/lib/api';
import { byDescNullsLast, coverageNote, num, rate, recoveredMark, relativeTime, type Rate } from '@/lib/format';
import { useAsync } from '@/lib/use-async';

type SourceFilter = SourceCode | 'all';
type Category = 'batting' | 'bowling' | 'fielding';

const SOURCE_LABEL: Record<SourceCode, string> = { sca: 'SCA', cricheroes: 'CricHeroes' };

export default function StatsScreen() {
  const [source, setSource] = useState<SourceFilter>('all');
  const [category, setCategory] = useState<Category>('batting');
  const players = useAsync(() => api.players(source), [source]);
  const sources = useAsync(() => api.sources(), []);

  return (
    <Screen title="Stats" subtitle="Hawks CC players across every source we import">
      <Segmented
        label="Source"
        value={source}
        onChange={setSource}
        options={[
          { value: 'all', label: 'All sources' },
          { value: 'sca', label: 'SCA' },
          { value: 'cricheroes', label: 'CricHeroes (BPL)' },
        ]}
      />

      {sources.status === 'success' ? <SourceStrip statuses={sources.data} /> : null}

      <Segmented
        label="Category"
        value={category}
        onChange={setCategory}
        options={[
          { value: 'batting', label: 'Batting' },
          { value: 'bowling', label: 'Bowling' },
          { value: 'fielding', label: 'Fielding' },
        ]}
      />

      {players.status === 'loading' ? <Loading label="Loading stats…" /> : null}
      {players.status === 'error' ? <ErrorState message={players.message} onRetry={players.reload} /> : null}
      {players.status === 'success' ? (
        <PlayerList players={players.data} category={category} source={source} />
      ) : null}

      <Body muted style={styles.footnote}>
        "All sources" adds each player's counts together (runs, balls, wickets…) and recalculates averages and
        strike rates from the totals. "—" means a source didn't publish that figure; we never estimate it. "†"
        marks a rate exactly as the source published it. "‡" marks a count worked out exactly from a published
        rate (only one whole number fits), or a rate calculated from such a count. A note such as "SCA only" means
        the other sources don't list the player in that category.
      </Body>

      <Standings />
    </Screen>
  );
}

function SourceStrip({ statuses }: { statuses: { source: SourceCode; lastSucceededAt: string | null }[] }) {
  return (
    <View style={styles.sourceStrip}>
      {statuses.map((s) => (
        <View key={s.source} style={styles.sourceItem}>
          <Chip label={SOURCE_LABEL[s.source]} tone={s.source === 'sca' ? 'navy' : 'gold'} />
          <Body muted style={styles.small}>
            {s.lastSucceededAt ? `Updated ${relativeTime(s.lastSucceededAt)}` : 'Not imported yet'}
          </Body>
        </View>
      ))}
    </View>
  );
}

function PlayerList({ players, category, source }: { players: PlayerStats[]; category: Category; source: SourceFilter }) {
  const rows = useMemo(() => {
    const has = (p: PlayerStats) => (category === 'batting' ? p.batting : category === 'bowling' ? p.bowling : p.fielding);
    const key = (p: PlayerStats) =>
      category === 'batting' ? p.batting?.runs : category === 'bowling' ? p.bowling?.wickets : p.fielding?.dismissals;
    return players.filter((p) => has(p) !== null).sort(byDescNullsLast(key, (p) => p.name));
  }, [players, category]);

  if (rows.length === 0) {
    return source === 'sca' ? (
      <EmptyState title="No SCA stats yet" message="SCA Club League figures haven't been imported. They'll appear here once they are." />
    ) : (
      <EmptyState title={`No ${category} figures`} message="None of the imported sources published these figures." />
    );
  }
  return (
    <View style={styles.list}>
      {rows.map((p, i) => (
        <PlayerRow key={p.playerId} rank={i + 1} player={p} category={category} />
      ))}
    </View>
  );
}

function PlayerRow({ rank, player, category }: { rank: number; player: PlayerStats; category: Category }) {
  const t = useTheme();
  const rec = (...fields: string[]) => fields.some((f) => player.recovered?.includes(f));
  // A calculated rate inherits ‡ from a recovered input; a published one (†) doesn't need it.
  const derived = (r: Rate | undefined, ...inputs: string[]) => recoveredMark(rate(r), !r?.reported && rec(...inputs));
  const note = coverageNote(player.sources, player.coverage?.[category], (s) => SOURCE_LABEL[s as SourceCode] ?? s);
  const cells: [string, string][] =
    category === 'batting'
      ? [
          ['Runs', num(player.batting?.runs)],
          ['Inns', num(player.batting?.inns)],
          ['Avg', derived(player.batting?.average, 'batting.notOuts')],
          ['SR', derived(player.batting?.strikeRate, 'batting.balls')],
        ]
      : category === 'bowling'
        ? [
            ['Wkts', num(player.bowling?.wickets)],
            ['Inns', num(player.bowling?.inns)],
            ['Econ', derived(player.bowling?.economy, 'bowling.runs', 'bowling.balls')],
            ['Avg', derived(player.bowling?.average, 'bowling.runs')],
          ]
        : [
            ['Dis', num(player.fielding?.dismissals)],
            ['Ct', num(player.fielding?.catches)],
            ['St', num(player.fielding?.stumpings)],
            ['RO', num(player.fielding?.runOuts)],
          ];
  return (
    <Card
      accessible
      accessibilityLabel={`${rank}. ${player.name}. ${cells.map(([k, v]) => `${k} ${v}`).join(', ')}${note ? `. ${note}` : ''}`}
      style={styles.row}>
      <View style={styles.rowHead}>
        <Text style={[styles.rank, { color: t.textSecondary }]}>{rank}</Text>
        <Text style={[styles.name, { color: t.text }]} numberOfLines={2}>
          {player.name}
        </Text>
        <View style={styles.chips}>
          {player.sources.map((s) => (
            <Chip key={s} label={SOURCE_LABEL[s]} tone={s === 'sca' ? 'navy' : 'gold'} />
          ))}
        </View>
      </View>
      <View style={styles.cells}>
        {cells.map(([k, v], i) => (
          <View key={k} style={styles.cell}>
            <Text style={[styles.cellValue, { color: t.text }, i === 0 && styles.cellLead]}>{v}</Text>
            <Text style={[styles.cellLabel, { color: t.textSecondary }]}>{k}</Text>
          </View>
        ))}
      </View>
      {note ? <Body muted style={[styles.small, styles.note]}>{note}</Body> : null}
    </Card>
  );
}

function Standings() {
  const t = useTheme();
  const table = useAsync(async () => {
    const comps = await api.competitions();
    const bpl = comps.find((c) => c.source === 'cricheroes');
    if (!bpl) return null;
    return { competition: bpl, rows: await api.standings(bpl.id) };
  }, []);

  return (
    <View style={{ gap: Spacing.sm }}>
      <SectionTitle
        right={
          table.status === 'error' ? (
            <Pressable accessibilityRole="button" onPress={table.reload}>
              <Text style={{ color: t.link, fontWeight: '700' }}>Retry</Text>
            </Pressable>
          ) : null
        }>
        {table.status === 'success' && table.data ? `${table.data.competition.name} table` : 'Points table'}
      </SectionTitle>
      {table.status === 'loading' ? <Loading /> : null}
      {table.status === 'error' ? <Body muted>{table.message}</Body> : null}
      {table.status === 'success' && (!table.data || table.data.rows.length === 0) ? (
        <EmptyState title="No table yet" message="No competition table has been imported." />
      ) : null}
      {table.status === 'success' && table.data && table.data.rows.length > 0 ? (
        <Card style={{ padding: 0, overflow: 'hidden' }}>
          <View style={[styles.tRow, { borderBottomColor: t.border }]}>
            {['#', 'Team', 'M', 'W', 'L', 'NR', 'Pts', 'NRR'].map((h, i) => (
              <Text
                key={h}
                style={[styles.tHead, i === 1 ? styles.tTeam : i === 7 ? styles.tNrr : styles.tNum, { color: t.textSecondary }]}>
                {h}
              </Text>
            ))}
          </View>
          {table.data.rows.map((r) => (
            <View
              key={`${r.group}-${r.position}`}
              style={[styles.tRow, { borderBottomColor: t.border }, r.clubTeam && { backgroundColor: t.muted }]}>
              <Text style={[styles.tCell, styles.tNum, { color: t.text }]}>{r.position}</Text>
              <Text style={[styles.tCell, styles.tTeam, { color: t.text }, r.clubTeam && styles.bold]} numberOfLines={1}>
                {r.team}
              </Text>
              {[r.matches, r.won, r.lost, r.noResult, r.points].map((v, i) => (
                <Text key={i} style={[styles.tCell, styles.tNum, { color: t.text }, r.clubTeam && styles.bold]}>
                  {num(v)}
                </Text>
              ))}
              <Text style={[styles.tCell, styles.tNrr, { color: t.text }]}>{r.netRunRate ?? '—'}</Text>
            </View>
          ))}
        </Card>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  footnote: { fontSize: 13, lineHeight: 18 },
  small: { fontSize: 13 },
  note: { paddingLeft: 30 },
  sourceStrip: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.md },
  sourceItem: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  list: { gap: Spacing.sm },
  row: { paddingVertical: 12, gap: 10 },
  rowHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  rank: { width: 22, fontSize: 13, fontWeight: '700' },
  name: { flex: 1, fontSize: 16, fontWeight: '700' },
  chips: { flexDirection: 'row', gap: 4 },
  cells: { flexDirection: 'row', paddingLeft: 30 },
  cell: { flex: 1 },
  cellValue: { fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  cellLead: { fontWeight: '800' },
  cellLabel: { fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5 },
  tRow: { flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, paddingHorizontal: 10, paddingVertical: 9 },
  tHead: { fontSize: 11, fontWeight: '700', textTransform: 'uppercase' },
  tCell: { fontSize: 13, fontVariant: ['tabular-nums'] },
  tTeam: { flex: 1, paddingRight: 6 },
  tNum: { width: 30, textAlign: 'right' },
  tNrr: { width: 54, textAlign: 'right' },
  bold: { fontWeight: '800' },
});
