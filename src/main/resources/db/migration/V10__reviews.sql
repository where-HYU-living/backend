create table reviews (
    id             bigserial primary key,
    author_id      bigint      not null references users (id) on delete cascade,
    target_type    varchar(20) not null check (target_type in ('LISTING', 'HOUSING', 'DORM')),
    target_id      bigint      not null,
    rating         smallint    not null check (rating between 1 and 5),
    noise          smallint    not null check (noise between 1 and 5),
    light          smallint    not null check (light between 1 and 5),
    bugs           smallint    not null check (bugs between 1 and 5),
    landlord       smallint    not null check (landlord between 1 and 5),
    deposit_return smallint    not null check (deposit_return between 1 and 5),
    text           varchar(500),
    pin_x          double precision,
    pin_y          double precision,
    pin_z          double precision,
    floorplan_id   bigint,
    created_at     timestamptz not null,
    check ((pin_x is null and pin_y is null and pin_z is null) or (pin_x is not null and pin_y is not null and pin_z is not null))
);

create index idx_reviews_target on reviews (target_type, target_id, created_at desc);
