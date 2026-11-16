-- Finance ledger (docs/05). Append-only: charges, payments, allocations and adjustments are
-- never updated or deleted; a correction is a new row with a reason. A member's balance is
-- always calculated from these rows, never stored. Amounts are whole cents in SGD.

create sequence charge_reference_seq;
create sequence payment_reference_seq;

-- One "raise charges" action (for one member, several, or everyone). request_key makes a
-- repeated submission (double tap, retry after a timeout) return the first result.
create table charge_batches (
    id            uuid primary key default gen_random_uuid(),
    club_id       uuid not null references clubs (id),
    description   text not null,
    amount_cents  bigint not null check (amount_cents > 0),
    due_date      date,
    request_key   text not null,
    created_by    uuid not null references members (id),
    created_at    timestamptz not null default now(),
    unique (club_id, request_key)
);

create table charges (
    id            uuid primary key default gen_random_uuid(),
    club_id       uuid not null references clubs (id),
    member_id     uuid not null references members (id),
    batch_id      uuid not null references charge_batches (id),
    reference     text not null,
    description   text not null,
    amount_cents  bigint not null check (amount_cents > 0),
    currency      char(3) not null default 'SGD',
    due_date      date,
    created_by    uuid not null references members (id),
    created_at    timestamptz not null default now(),
    unique (club_id, reference),
    unique (batch_id, member_id)
);
create index charges_member_idx on charges (club_id, member_id);

-- Money received. The authoritative receipt, whatever the method.
create table payments (
    id            uuid primary key default gen_random_uuid(),
    club_id       uuid not null references clubs (id),
    member_id     uuid not null references members (id),
    reference     text not null,
    amount_cents  bigint not null check (amount_cents > 0),
    currency      char(3) not null default 'SGD',
    method        text not null check (method in ('paynow', 'bank', 'cash', 'other')),
    received_on   date not null,
    external_ref  text,            -- e.g. the bank's transaction reference
    note          text,
    request_key   text not null,
    recorded_by   uuid not null references members (id),
    created_at    timestamptz not null default now(),
    unique (club_id, reference),
    unique (club_id, request_key)
);
create index payments_member_idx on payments (club_id, member_id);

-- Corrections and concessions. Each has a reason and the admin who approved it.
--   void      a charge raised by mistake: cancels the whole charge
--   waiver    part or all of a charge is forgiven
--   credit    the club gives the member credit (no money received); used like a payment
--   refund    money from a payment is returned to the member
--   reversal  a payment recorded by mistake: cancels the whole payment
create table adjustments (
    id            uuid primary key default gen_random_uuid(),
    club_id       uuid not null references clubs (id),
    member_id     uuid not null references members (id),
    kind          text not null check (kind in ('void', 'waiver', 'credit', 'refund', 'reversal')),
    amount_cents  bigint not null check (amount_cents > 0),
    charge_id     uuid references charges (id),
    payment_id    uuid references payments (id),
    reason        text not null check (length(btrim(reason)) > 0),
    request_key   text not null,
    approved_by   uuid not null references members (id),
    created_at    timestamptz not null default now(),
    unique (club_id, request_key),
    check ((kind in ('void', 'waiver') and charge_id is not null and payment_id is null)
        or (kind in ('refund', 'reversal') and payment_id is not null and charge_id is null)
        or (kind = 'credit' and charge_id is null and payment_id is null))
);
create index adjustments_member_idx on adjustments (club_id, member_id);
create unique index adjustments_one_void_uq on adjustments (charge_id) where kind = 'void';
create unique index adjustments_one_reversal_uq on adjustments (payment_id) where kind = 'reversal';

-- Which money paid which charge. The money is a payment or a credit adjustment. Because rows
-- can't be changed, taking an allocation back (when a charge is voided or a payment reversed)
-- is a second row that "releases" the first.
create table payment_allocations (
    id                    uuid primary key default gen_random_uuid(),
    club_id               uuid not null references clubs (id),
    member_id             uuid not null references members (id),
    payment_id            uuid references payments (id),
    credit_adjustment_id  uuid references adjustments (id),
    charge_id             uuid not null references charges (id),
    amount_cents          bigint not null check (amount_cents > 0),
    releases              uuid unique references payment_allocations (id),
    created_by            uuid not null references members (id),
    created_at            timestamptz not null default now(),
    check ((payment_id is null) <> (credit_adjustment_id is null))
);
create index payment_allocations_charge_idx on payment_allocations (charge_id);
create index payment_allocations_member_idx on payment_allocations (club_id, member_id);

create function reject_ledger_change() returns trigger language plpgsql as $$
begin
    raise exception '% is append-only: add a correcting row instead of changing or deleting one', tg_table_name;
end $$;

create trigger charge_batches_append_only before update or delete on charge_batches
    for each row execute function reject_ledger_change();
create trigger charges_append_only before update or delete on charges
    for each row execute function reject_ledger_change();
create trigger payments_append_only before update or delete on payments
    for each row execute function reject_ledger_change();
create trigger adjustments_append_only before update or delete on adjustments
    for each row execute function reject_ledger_change();
create trigger payment_allocations_append_only before update or delete on payment_allocations
    for each row execute function reject_ledger_change();
create trigger audit_log_append_only before update or delete on audit_log
    for each row execute function reject_ledger_change();
