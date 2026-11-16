-- Club notices (docs/07): written by admins, read by every member, with reactions and replies.
create table announcements (
    id              uuid primary key default gen_random_uuid(),
    club_id         uuid not null references clubs (id),
    author_id       uuid not null references members (id),
    title           text not null check (length(btrim(title)) > 0),
    body            text not null check (length(btrim(body)) > 0),   -- plain text, shown as written
    pinned          boolean not null default false,
    -- Email copy to members: null = not asked for; otherwise how it went.
    email_status    text check (email_status in ('sending', 'sent', 'failed')),
    emailed_count   integer,
    email_failures  integer,
    request_key     text not null,
    created_at      timestamptz not null default now(),
    edited_at       timestamptz,
    archived_at     timestamptz,           -- withdrawn: hidden from members, kept for the record
    archived_by     uuid references members (id),
    unique (club_id, request_key)
);
create index announcements_club_idx on announcements (club_id, created_at desc) where archived_at is null;

-- One reaction per member per notice; choosing another replaces it.
create table announcement_reactions (
    announcement_id  uuid not null references announcements (id),
    member_id        uuid not null references members (id),
    reaction         text not null check (reaction in ('like', 'done', 'cricket', 'clap', 'question')),
    created_at       timestamptz not null default now(),
    primary key (announcement_id, member_id)
);

create table announcement_replies (
    id               uuid primary key default gen_random_uuid(),
    club_id          uuid not null references clubs (id),
    announcement_id  uuid not null references announcements (id),
    member_id        uuid not null references members (id),
    body             text not null check (length(btrim(body)) > 0),
    created_at       timestamptz not null default now(),
    -- Hidden by its writer or an admin; the text stays for the record but isn't shown.
    hidden_at        timestamptz,
    hidden_by        uuid references members (id),
    hidden_reason    text
);
create index announcement_replies_idx on announcement_replies (announcement_id, created_at);

-- Who has opened a notice. Admins see only the count.
create table announcement_reads (
    announcement_id  uuid not null references announcements (id),
    member_id        uuid not null references members (id),
    read_at          timestamptz not null default now(),
    primary key (announcement_id, member_id)
);
