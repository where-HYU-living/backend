-- 실내 3D 평면도. 팀이 만든 FloorPlan JSON 을 그대로 저장한다 (스키마가 자주 늘어나서 컬럼으로 쪼개지 않는다).
-- 원본은 resources/fixtures/floorplans/*.json 이고, 서버가 뜰 때마다 id 기준으로 덮어쓴다.
-- floorplan_id 를 가진 listings / housing_unit_types / dorm_room_types 는 FK 없이 이 id 만 가리킨다.
create table floorplans (
    id         bigint primary key,
    name       varchar(100)     not null,
    area_m2    double precision not null,
    data       jsonb            not null,
    updated_at timestamptz      not null
);
