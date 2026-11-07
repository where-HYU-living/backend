create table transfers (
    id               bigserial primary key,
    author_id        bigint           not null references users (id) on delete cascade,
    title            varchar(50)      not null,
    address          varchar(300)     not null,
    dong             varchar(50)      not null,
    lat              double precision not null,
    lng              double precision not null,
    deposit          bigint           not null,
    rent             bigint           not null,
    mgmt_fee         bigint,
    area_m2          double precision not null,
    floor            int,
    room_type        varchar(20)      not null check (room_type in ('OPEN', 'SEPARATED', 'ONE_HALF', 'TWO_ROOM', 'DUPLEX')),
    remaining_months int              not null check (remaining_months > 0),
    available_from   date,
    description      varchar(2000),
    status           varchar(10)      not null check (status in ('OPEN', 'DONE')),
    converted_rent   bigint,
    grade            varchar(20) check (grade in ('FAIR', 'SLIGHTLY_HIGH', 'HIGH', 'CULPRIT', 'PENDING')),
    created_at       timestamptz      not null,
    updated_at       timestamptz      not null
);

create table transfer_photos (
    transfer_id bigint       not null references transfers (id) on delete cascade,
    position    int          not null,
    url         varchar(500) not null,
    primary key (transfer_id, position)
);

create index idx_transfers_status_created on transfers (status, created_at desc);
