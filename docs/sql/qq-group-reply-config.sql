create table if not exists qq_group_reply_config (
    id bigserial primary key,
    group_id bigint not null unique,
    group_name varchar(255) not null,
    enabled boolean not null default true
);
