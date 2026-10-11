package com.atriplus.chat.mapper;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
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
            where file_id = #{file} and status = 'ready' and deleted_at is null
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @ConstructorArgs({
            @Arg(column = "media_kind", javaType = String.class),
            @Arg(column = "description_json", javaType = String.class)
    })
    CachedMedia selectReady(@Param("file") String file);

    default Optional<CachedMedia> findReady(String file) {
        return Optional.ofNullable(selectReady(file));
    }

    @Select(value = """
            insert into media_description (file_id, media_kind, status, revision, recognition_claimed_at, updated_at)
            values (#{file}, #{kind}, 'processing', 1, current_timestamp, current_timestamp)
            on conflict (file_id) do update
            set media_kind = excluded.media_kind,
                status = 'processing',
                description_json = null,
                updated_at = current_timestamp,
                recognition_claimed_at = current_timestamp,
                revision = media_description.revision + 1
            where media_description.deleted_at is null and (media_description.status = 'failed'
               or (media_description.status = 'processing'
                   and media_description.recognition_claimed_at < current_timestamp - interval '10 minutes'))
            returning revision
            """, affectData = true)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long claim(@Param("file") String file, @Param("kind") String kind);

    @Update("""
            update media_description
            set status = 'ready', description_json = #{json}::jsonb, updated_at = current_timestamp
            where file_id = #{file} and status = 'processing' and revision = #{revision} and deleted_at is null
            """)
    int saveReady(@Param("file") String file, @Param("revision") long revision, @Param("json") String json);

    @Update("""
            update media_description
            set status = 'failed', updated_at = current_timestamp
            where file_id = #{file} and status = 'processing' and revision = #{revision} and deleted_at is null
            """)
    void markFailed(@Param("file") String file, @Param("revision") long revision);

    record CachedMedia(String kind, String json) {
    }
}
