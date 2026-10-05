-- Counts a source didn't print but that exactly one whole number reproduces from its published
-- rates (CountRecovery). Listed by column name so the API can mark them as recovered.
alter table player_competition_stats
    add column recovered_columns text[] not null default '{}';

-- Linking source names to players looks players up by name.
create index players_club_name_idx on players (club_id, lower(display_name));
