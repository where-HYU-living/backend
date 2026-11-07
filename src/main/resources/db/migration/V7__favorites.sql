create table favorites (
    id         bigserial primary key,
    user_id    bigint      not null references users (id) on delete cascade,
    type       varchar(20) not null check (type in ('LISTING', 'HOUSING', 'DORM', 'TRANSFER')),
    target_id  bigint      not null,
    created_at timestamptz not null,
    unique (user_id, type, target_id)
);
