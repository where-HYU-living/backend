create table housing (
    id                     bigserial primary key,
    soco_home_code         varchar(20)      unique,
    name                   varchar(200)     not null,
    address                varchar(300)     not null,
    dong                   varchar(50),
    lat                    double precision not null,
    lng                    double precision not null,
    contact                varchar(100),
    homepage_url           varchar(500),
    floor_layout_image_url varchar(500),
    updated_at             timestamptz      not null
);

create table housing_unit_types (
    id                bigserial primary key,
    housing_id        bigint       not null references housing (id) on delete cascade,
    name              varchar(100) not null,
    room_kind         varchar(30),
    exclusive_area_m2 double precision,
    deposit           bigint       not null,
    rent              bigint       not null,
    mgmt_fee          bigint,
    floorplan_id      bigint,
    unique (housing_id, name)
);

create table announcements (
    id            bigserial primary key,
    soco_board_id bigint       unique,
    housing_id    bigint       not null references housing (id) on delete cascade,
    title         varchar(300) not null,
    additional    boolean      not null,
    posted_on     date         not null
);

create table announcement_events (
    id              bigserial primary key,
    announcement_id bigint      not null references announcements (id) on delete cascade,
    type            varchar(20) not null check (type in ('NOTICE', 'APPLY_START', 'APPLY_END', 'DOCS_DUE', 'RESULT', 'CONTRACT', 'MOVE_IN')),
    event_date      date        not null,
    unique (announcement_id, type)
);

create index idx_announcement_events_date on announcement_events (event_date);

create table housing_subscriptions (
    user_id    bigint      not null references users (id) on delete cascade,
    housing_id bigint      not null references housing (id) on delete cascade,
    created_at timestamptz not null,
    primary key (user_id, housing_id)
);

create table housing_floorplan_images (
    id             bigserial primary key,
    housing_id     bigint       not null references housing (id) on delete cascade,
    position       int          not null,
    url            varchar(500) not null unique,
    sha256         varchar(64),
    unit_type_name varchar(100)
);

create table dorms (
    id   bigint primary key,
    name varchar(100)     not null,
    lat  double precision not null,
    lng  double precision not null
);

create table dorm_room_types (
    id              bigint primary key,
    dorm_id         bigint       not null references dorms (id) on delete cascade,
    name            varchar(100) not null,
    capacity        int          not null,
    semester_fee    bigint       not null,
    semester_months int          not null check (semester_months > 0),
    meal_included   boolean      not null,
    floorplan_id    bigint
);
