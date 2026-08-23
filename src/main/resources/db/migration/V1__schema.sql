create table sources (
    code text primary key,
    name text not null
);

create table api_clients (
    id          uuid primary key,
    name        text not null,
    token_hash  text not null unique,
    source_code text not null references sources (code),
    revoked_at  timestamptz
);

create table inbox_messages (
    id              uuid primary key,
    source_code     text        not null references sources (code),
    external_id     text        not null,
    aggregate_type  text        not null,
    aggregate_id    text        not null,
    payload         jsonb       not null,
    received_at     timestamptz not null default now(),
    status          text        not null default 'pending',
    attempts        int         not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error      text,
    processed_at    timestamptz,
    constraint uq_inbox_messages_external unique (source_code, external_id),
    constraint chk_inbox_messages_status check (status in ('pending', 'processing', 'done', 'dead'))
);

create table dead_letters (
    id               uuid primary key,
    inbox_message_id uuid        not null unique references inbox_messages (id),
    reason_code      text        not null,
    detail           text,
    attempts         int         not null,
    parked_at        timestamptz not null default now(),
    retried_at       timestamptz
);

create table orders_projection (
    order_id      text primary key,
    status        text        not null,
    placed_at     timestamptz,
    paid_at       timestamptz,
    cancelled_at  timestamptz,
    refunded_at   timestamptz,
    last_event_at timestamptz not null,
    updated_at    timestamptz not null default now()
);

create table order_event_log (
    id          uuid primary key,
    order_id    text        not null,
    event_id    text        not null unique,
    event_type  text        not null,
    occurred_at timestamptz not null,
    applied_at  timestamptz not null default now(),
    effect      text        not null
);

create table products (
    sku  text primary key,
    name text not null
);

create table vending_batches (
    id                uuid primary key,
    device_id         text        not null,
    batch_external_id text        not null,
    line_count        int         not null,
    received_at       timestamptz not null default now(),
    constraint uq_vending_batches_external unique (device_id, batch_external_id)
);

create table vending_sales (
    id               uuid primary key,
    device_id        text        not null,
    seq              bigint      not null,
    sale_external_id text        not null,
    sku              text        not null,
    qty              int         not null,
    amount_minor     bigint      not null,
    currency         char(3)     not null,
    sold_at          timestamptz not null,
    received_at      timestamptz not null default now(),
    constraint uq_vending_sales_seq unique (device_id, seq),
    constraint uq_vending_sales_external unique (device_id, sale_external_id)
);
