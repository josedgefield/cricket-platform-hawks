-- Cricket stats (docs/06): raw source records are kept verbatim and hashed;
-- normalised per-source rows reference the record they came from.
-- Every count is nullable: NULL means the source didn't publish it. Never 0 by default.

create table competitions (
    id           uuid primary key default gen_random_uuid(),
    club_id      uuid not null references clubs (id),
    source       text not null check (source in ('sca', 'cricheroes', 'manual')),
    external_id  text,
    name         text not null,
    season       text,
    created_at   timestamptz not null default now(),
    unique (club_id, source, name)
);

-- A stats identity per person as sources name them. Linking a player to a club
-- member (and merging duplicates across sources) is done by the stats admin.
create table players (
    id            uuid primary key default gen_random_uuid(),
    club_id       uuid not null references clubs (id),
    display_name  text not null,
    created_at    timestamptz not null default now()
);
create index players_club_idx on players (club_id);

create table player_aliases (
    club_id      uuid not null references clubs (id),
    source       text not null check (source in ('sca', 'cricheroes', 'manual')),
    name_key     text not null,          -- normalised: lower case, letters and single spaces
    source_name  text not null,          -- as the source printed it
    player_id    uuid not null references players (id),
    created_at   timestamptz not null default now(),
    primary key (club_id, source, name_key)
);

-- Exactly what was received. Re-importing identical content is a no-op.
create table source_records (
    id              uuid primary key default gen_random_uuid(),
    club_id         uuid not null references clubs (id),
    source          text not null check (source in ('sca', 'cricheroes', 'manual')),
    competition_id  uuid not null references competitions (id),
    kind            text not null check (kind in ('batting', 'bowling', 'fielding', 'standings')),
    content_type    text not null default 'text/csv',
    raw_content     text not null,
    content_sha256  char(64) not null,
    origin          text,                -- e.g. the file name it came from
    captured_on     date,                -- when the source page was captured
    imported_by     text not null,
    imported_at     timestamptz not null default now(),
    unique (club_id, source, competition_id, kind, content_sha256)
);

create table player_competition_stats (
    id              uuid primary key default gen_random_uuid(),
    club_id         uuid not null references clubs (id),
    player_id       uuid not null references players (id),
    competition_id  uuid not null references competitions (id),
    source          text not null check (source in ('sca', 'cricheroes', 'manual')),
    matches         int check (matches >= 0),
    -- batting
    bat_inns        int check (bat_inns >= 0),
    bat_not_outs    int check (bat_not_outs >= 0),
    bat_runs        int check (bat_runs >= 0),
    bat_balls       int check (bat_balls >= 0),
    bat_high_score  int check (bat_high_score >= 0),
    bat_fours       int check (bat_fours >= 0),
    bat_sixes       int check (bat_sixes >= 0),
    -- bowling (overs are stored as balls)
    bowl_inns       int check (bowl_inns >= 0),
    bowl_balls      int check (bowl_balls >= 0),
    bowl_maidens    int check (bowl_maidens >= 0),
    bowl_runs       int check (bowl_runs >= 0),
    bowl_wickets    int check (bowl_wickets >= 0),
    -- fielding
    field_catches     int check (field_catches >= 0),
    field_stumpings   int check (field_stumpings >= 0),
    field_run_outs    int check (field_run_outs >= 0),
    field_dismissals  int check (field_dismissals >= 0),
    -- rates exactly as the source published them (used only when we can't calculate)
    reported_bat_avg   numeric(8, 2),
    reported_bat_sr    numeric(8, 2),
    reported_econ      numeric(8, 2),
    reported_bowl_avg  numeric(8, 2),
    batting_record_id   uuid references source_records (id),
    bowling_record_id   uuid references source_records (id),
    fielding_record_id  uuid references source_records (id),
    updated_at      timestamptz not null default now(),
    unique (club_id, player_id, competition_id, source)
);
create index pcs_competition_idx on player_competition_stats (club_id, competition_id, source);

create table standings (
    id               uuid primary key default gen_random_uuid(),
    club_id          uuid not null references clubs (id),
    competition_id   uuid not null references competitions (id),
    source_record_id uuid not null references source_records (id),
    group_name       text not null,
    position         int not null check (position > 0),
    team             text not null,
    is_club_team     boolean not null default false,
    matches          int,
    won              int,
    lost             int,
    drawn            int,
    tied             int,
    no_result        int,
    points           int,
    net_run_rate     text,   -- kept as printed, never recomputed
    runs_for         text,   -- e.g. 912/123.3, as printed
    runs_against     text,
    last_five        text,
    unique (club_id, competition_id, group_name, position)
);

-- One row per import attempt: drives "last updated" and failure alerts.
create table sync_runs (
    id              uuid primary key default gen_random_uuid(),
    club_id         uuid not null references clubs (id),
    source          text not null,
    competition_id  uuid references competitions (id),
    kind            text not null,
    status          text not null check (status in ('succeeded', 'unchanged', 'failed')),
    rows_read       int,
    players_created int,
    warnings        text[] not null default '{}',
    error           text,
    triggered_by    text not null,
    started_at      timestamptz not null,
    finished_at     timestamptz not null default now()
);
create index sync_runs_recent_idx on sync_runs (club_id, source, finished_at desc);
