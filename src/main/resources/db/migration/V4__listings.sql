create table listings (
    id             bigserial primary key,
    title          varchar(200)     not null,
    address        varchar(300)     not null,
    dong           varchar(50)      not null,
    lat            double precision not null,
    lng            double precision not null,
    deposit        bigint           not null,
    rent           bigint           not null,
    mgmt_fee       bigint,
    area_m2        double precision not null,
    floor          int,
    room_type      varchar(20)      not null check (room_type in ('OPEN', 'SEPARATED', 'ONE_HALF', 'TWO_ROOM', 'DUPLEX')),
    thumbnail_url  varchar(500),
    floorplan_id   bigint,
    built_year     int,
    description    varchar(2000),
    converted_rent bigint,
    grade          varchar(20) check (grade in ('FAIR', 'SLIGHTLY_HIGH', 'HIGH', 'CULPRIT', 'PENDING')),
    graded_at      timestamptz
);

create table listing_options (
    listing_id  bigint       not null references listings (id) on delete cascade,
    position    int          not null,
    option_name varchar(100) not null,
    primary key (listing_id, position)
);

create table listing_photos (
    listing_id bigint       not null references listings (id) on delete cascade,
    position   int          not null,
    url        varchar(500) not null,
    primary key (listing_id, position)
);

create index idx_listings_location on listings (lat, lng);
