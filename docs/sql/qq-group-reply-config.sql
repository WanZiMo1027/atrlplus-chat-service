create table if not exists qq_group_reply_config (
    id bigserial primary key,
    group_id bigint not null unique,
    group_name varchar(255) not null,
    enabled boolean not null default true,
    receive_enabled boolean not null default true
);

-- 旧群的回复设置保持原值；接收消息统一默认开启，用于积累 Agent 聊天记录。
alter table qq_group_reply_config
    add column if not exists receive_enabled boolean not null default true;
