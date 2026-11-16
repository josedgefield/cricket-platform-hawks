-- Members and sign-in (docs/03). Invite-only: there is no self sign-up.
-- Roles: player < admin < superuser (support). "Deleting" a member deactivates them, so
-- finance and stats history keeps pointing at a real row.
create table members (
    id                uuid primary key default gen_random_uuid(),
    club_id           uuid not null references clubs (id),
    email             text not null check (email = lower(email)),
    display_name      text not null,
    phone             text,
    role              text not null check (role in ('player', 'admin', 'superuser')),
    status            text not null check (status in ('invited', 'active', 'deactivated')),
    password_hash     text,                         -- null until the invite is accepted
    player_id         uuid references players (id), -- their row in the cricket stats, when linked
    invited_by        uuid references members (id),
    invite_sent_at    timestamptz,
    last_email_error  text,                         -- why the last invite email failed, if it did
    last_sign_in_at   timestamptz,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    check (status <> 'active' or password_hash is not null)
);
create unique index members_club_email_uq on members (club_id, email);
create index members_club_status_idx on members (club_id, status);

-- Single-use links sent by email. Only a SHA-256 of the token is stored.
create table member_tokens (
    id          uuid primary key default gen_random_uuid(),
    member_id   uuid not null references members (id),
    purpose     text not null check (purpose in ('invite', 'password_reset')),
    token_hash  text not null unique,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_by  uuid references members (id),
    created_at  timestamptz not null default now()
);
create index member_tokens_member_idx on member_tokens (member_id, purpose);

-- Signed-in devices. The bearer token is random; only its SHA-256 is stored, so a database
-- leak doesn't hand out sessions. Revoking a row signs that device out immediately.
create table sessions (
    id            uuid primary key default gen_random_uuid(),
    member_id     uuid not null references members (id),
    token_hash    text not null unique,
    user_agent    text,
    created_at    timestamptz not null default now(),
    last_used_at  timestamptz not null default now(),
    expires_at    timestamptz not null,
    revoked_at    timestamptz
);
create index sessions_member_idx on sessions (member_id) where revoked_at is null;

-- Who changed what. Append-only; finance will write here too.
create table audit_log (
    id               bigint generated always as identity primary key,
    club_id          uuid not null references clubs (id),
    actor_member_id  uuid references members (id),   -- null for the system (bootstrap, seed)
    action           text not null,
    target_type      text,
    target_id        uuid,
    details          jsonb not null default '{}'::jsonb,
    at               timestamptz not null default now()
);
create index audit_log_club_at_idx on audit_log (club_id, at desc);
