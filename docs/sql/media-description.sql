-- image.file 是媒体描述的唯一 ID；首次表情识别期间保留 processing 状态。
create table if not exists media_description (
    file_id text primary key,
    media_kind text not null check (media_kind in ('image', 'meme')),
    status text not null check (status in ('processing', 'ready', 'failed')),
    description_json jsonb,
    updated_at timestamptz not null default current_timestamp,
    constraint media_description_ready_has_content
        check (status <> 'ready' or description_json is not null)
);

-- 同一脚本兼容新库和已有媒体描述表，重复执行不会覆盖已有描述。
alter table media_description add column if not exists id bigserial;
create unique index if not exists media_description_id_idx on media_description (id);
alter table media_description add column if not exists source_url text;
alter table media_description add column if not exists created_at timestamptz;
update media_description set created_at = updated_at where created_at is null;
alter table media_description alter column created_at set default current_timestamp;
alter table media_description alter column created_at set not null;
alter table media_description add column if not exists deleted_at timestamptz;
alter table media_description add column if not exists revision bigint not null default 0;
alter table media_description add column if not exists recognition_claimed_at timestamptz;
update media_description set recognition_claimed_at = updated_at
    where status = 'processing' and recognition_claimed_at is null;
create index if not exists media_description_list_idx
    on media_description (media_kind, updated_at desc, id desc) where deleted_at is null;
-- 已部署的历史下载字段和副本数据保留，当前服务不再读写或创建下载任务。
