-- 후기 (G1·G4), 내 집 기록 (H1~H6), 알림함 (Q1) — 계약 5-8 ~ 5-10

create table reviews (
    id             bigserial primary key,
    user_id        bigint        not null references users (id) on delete cascade,
    target_type    varchar(20)   not null check (target_type in ('LISTING', 'HOUSING', 'DORM')),
    target_id      bigint        not null,
    rating         int           not null check (rating between 1 and 5),
    noise          int           not null check (noise between 1 and 5),
    light          int           not null check (light between 1 and 5),
    bugs           int           not null check (bugs between 1 and 5),
    landlord       int           not null check (landlord between 1 and 5),
    deposit_return int           not null check (deposit_return between 1 and 5),
    text           varchar(500)  not null,
    pin_x          double precision,
    pin_y          double precision,
    pin_z          double precision,
    created_at     timestamptz   not null
);
create index idx_reviews_target on reviews (target_type, target_id, created_at desc);

create table my_homes (
    user_id        bigint primary key references users (id) on delete cascade,
    address        varchar(300)     not null,
    dong           varchar(50)      not null,
    lat            double precision not null,
    lng            double precision not null,
    deposit        bigint           not null,
    rent           bigint           not null,
    mgmt_fee       bigint,
    area_m2        double precision not null,
    contract_start date             not null,
    contract_end   date             not null,
    updated_at     timestamptz      not null
);

create table home_payments (
    user_id bigint  not null references users (id) on delete cascade,
    month   char(7) not null,
    amount  bigint  not null,
    paid_at date    not null,
    primary key (user_id, month)
);

create table home_utilities (
    user_id     bigint  not null references users (id) on delete cascade,
    month       char(7) not null,
    electricity bigint,
    gas         bigint,
    water       bigint,
    internet    bigint,
    primary key (user_id, month)
);

create table home_alerts (
    id          bigserial primary key,
    user_id     bigint  not null references users (id) on delete cascade,
    days_before int     not null check (days_before between 1 and 365),
    enabled     boolean not null
);

create table notifications (
    id          bigserial primary key,
    user_id     bigint       not null references users (id) on delete cascade,
    type        varchar(30)  not null check (type in ('HOUSING_DDAY', 'CONTRACT_EXPIRY')),
    title       varchar(200) not null,
    body        varchar(500) not null,
    target_type varchar(20),
    target_id   bigint,
    dedupe_key  varchar(200) not null,
    created_at  timestamptz  not null,
    read        boolean      not null default false,
    unique (user_id, dedupe_key)
);
create index idx_notifications_user on notifications (user_id, created_at desc);
