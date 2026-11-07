create table walk_cache (
    lat4        int         not null,
    lng4        int         not null,
    building_id bigint      not null,
    minutes     int         not null,
    distance_m  int         not null,
    fetched_at  timestamptz not null,
    primary key (lat4, lng4, building_id)
);
