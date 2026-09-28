package com.atriplus.chat.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 管理面板启用时建立自己的表，不触碰现有聊天数据表。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class AdminSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                create table if not exists admin_l1_prompt_state (
                  id smallint primary key check (id = 1),
                  draft_content text not null,
                  draft_revision bigint not null,
                  base_prompt_id text not null,
                  base_version integer not null,
                  updated_at timestamptz not null default now()
                )
                """);
        jdbc.execute("""
                create table if not exists admin_l1_prompt_history (
                  id bigserial primary key,
                  content text not null,
                  remote_prompt_id text not null,
                  remote_version integer not null,
                  status text not null,
                  requested_at timestamptz not null default now(),
                  published_at timestamptz
                )
                """);
        jdbc.execute("""
                create table if not exists admin_l1_audit (
                  id bigserial primary key,
                  action text not null,
                  target_id text not null,
                  before_json jsonb,
                  after_json jsonb,
                  status text not null,
                  created_at timestamptz not null default now(),
                  completed_at timestamptz
                )
                """);
    }
}
