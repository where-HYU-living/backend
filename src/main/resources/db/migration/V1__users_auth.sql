create table users (
    id         bigserial primary key,
    email      varchar(255) not null unique,
    nickname   varchar(30)  not null unique,
    created_at timestamptz  not null
);

create table email_codes (
    email      varchar(255) primary key,
    code_hash  varchar(64)  not null,
    expires_at timestamptz  not null,
    attempts   int          not null default 0,
    sent_at    timestamptz  not null
);
