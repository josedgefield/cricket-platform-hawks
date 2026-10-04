-- Tenant root. One club is deployed today; every tenant-owned table carries club_id.
create table clubs (
    id          uuid primary key,
    slug        text not null unique,
    name        text not null,
    settings    jsonb not null default '{}'::jsonb,
    created_at  timestamptz not null default now()
);

insert into clubs (id, slug, name)
values ('6f1c2a0e-6b1d-4c39-9d3e-2a4b7f0c1a01', 'hawks-cc', 'Hawks Cricket Club');
