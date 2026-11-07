create table real_deals (
    id            bigserial primary key,
    lawd_cd       varchar(5)       not null,
    building_type varchar(10)      not null check (building_type in ('OFFICETEL', 'ROWHOUSE', 'DETACHED')),
    dong          varchar(50)      not null,
    area_m2       double precision not null,
    deposit       bigint           not null,
    rent          bigint           not null,
    contract_ym   int              not null,
    floor         int,
    built_year    int,
    contract_type varchar(10),
    ingested_at   timestamptz      not null
);

create index idx_real_deals_sample on real_deals (dong, contract_ym, area_m2);
create index idx_real_deals_batch on real_deals (lawd_cd, building_type, contract_ym);
