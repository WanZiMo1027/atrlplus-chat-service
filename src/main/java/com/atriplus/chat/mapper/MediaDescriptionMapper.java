package com.atriplus.chat.mapper;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/** 用 MyBatis 访问 PostgreSQL 媒体描述表，以 OneBot image.file 为唯一键。 */
@Mapper
public interface MediaDescriptionMapper {

    @Select("""
            select media_kind, description_json::text as description_json
            from media_description
            where file_id = #{file} and status = 'ready'
            """)
    @ConstructorArgs({
            @Arg(column = "media_kind", javaType = String.class),
            @Arg(column = "description_json", javaType = String.class)
    })
    CachedMedia selectReady(@Param("file") String file);

    default Optional<CachedMedia> findReady(String file) {
        return Optional.ofNullable(selectReady(file));
    }

    @Insert("""
            insert into media_description (file_id, media_kind, status, updated_at)
            values (#{file}, #{kind}, 'processing', current_timestamp)
            on conflict (file_id) do update
            set media_kind = excluded.media_kind,
                status = 'processing',
                description_json = null,
                updated_at = current_timestamp
            where media_description.status = 'failed'
               or (media_description.status = 'processing'
                   and media_description.updated_at < current_timestamp - interval '10 minutes')
            """)
    int claimRows(@Param("file") String file, @Param("kind") String kind);

    default boolean claim(String file, String kind) {
        // 多实例通过唯一键抢占任务；超时的 processing 记录允许下一条消息重新识别。
        return claimRows(file, kind) > 0;
    }

    @Update("""
            update media_description
            set status = 'ready', description_json = #{json}::jsonb, updated_at = current_timestamp
            where file_id = #{file} and status = 'processing'
            """)
    void saveReady(@Param("file") String file, @Param("json") String json);

    @Update("""
            update media_description
            set status = 'failed', updated_at = current_timestamp
            where file_id = #{file} and status = 'processing'
            """)
    void markFailed(@Param("file") String file);

    record CachedMedia(String kind, String json) {
    }
}
