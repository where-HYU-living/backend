create table buildings (
    id   bigserial primary key,
    name varchar(100) not null,
    type varchar(10)  not null check (type in ('CAMPUS', 'GATE', 'DORM')),
    lat  double precision not null,
    lng  double precision not null
);

create table departments (
    id      bigserial primary key,
    name    varchar(100) not null,
    college varchar(100) not null
);

create table department_buildings (
    department_id bigint not null references departments (id) on delete cascade,
    position      int    not null,
    building_id   bigint not null references buildings (id),
    primary key (department_id, position)
);

alter table users
    add column department_id bigint references departments (id),
    add column rent_cap      bigint,
    add column deposit_cap   bigint;

create table user_buildings (
    user_id     bigint not null references users (id) on delete cascade,
    position    int    not null,
    building_id bigint not null references buildings (id),
    primary key (user_id, position)
);
