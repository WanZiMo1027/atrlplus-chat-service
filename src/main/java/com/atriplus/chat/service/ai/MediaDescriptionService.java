package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.ChatImageItem;
import com.atriplus.chat.dto.MediaOutcome;
import com.atriplus.chat.mapper.MediaDescriptionMapper;
import com.atriplus.chat.service.media.MediaUrl;
import com.atriplus.chat.service.media.MediaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/** 图片和动画表情的描述缓存与识别流程。 */
@Service
@Profile("snowluma")
@Slf4j
public class MediaDescriptionService {

    private final MediaDescriptionMapper mediaDescriptionMapper;
    private final VisionDescriptionClient visionClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor mediaDescriptionExecutor;
    private final MediaRepository mediaRepository;

    public MediaDescriptionService(
            MediaDescriptionMapper mediaDescriptionMapper,
            VisionDescriptionClient visionClient,
            ObjectMapper objectMapper,
            @Qualifier("mediaDescriptionExecutor") TaskExecutor mediaDescriptionExecutor,
            MediaRepository mediaRepository) {
        this.mediaDescriptionMapper = mediaDescriptionMapper;
        this.visionClient = visionClient;
        this.objectMapper = objectMapper;
        this.mediaDescriptionExecutor = mediaDescriptionExecutor;
        this.mediaRepository = mediaRepository;
    }

    public MediaOutcome resolve(String file, String summary, String url, boolean collectImages) {
        boolean meme = "[动画表情]".equals(summary);
        String kind = meme ? "meme" : "image";
        if (file == null || file.isBlank()) {
            return new MediaOutcome(meme ? "动画表情（缺少 file）" : "图片（缺少 file）", null);
        }

        try {
            // 删除标记先于缓存和原图发送检查；引用仍然只读取描述，不更新地址或发起识别。
            if (mediaRepository.isDeleted(file)) return ignored(meme);
        } catch (Exception exception) {
            log.warn("媒体删除标记读取失败，file={}，reason={}", file, exception.getClass().getSimpleName());
            return new MediaOutcome(meme ? "动画表情（暂不可用）" : "图片（暂不可用）", null);
        }
        if (collectImages) {
            try {
                Long id = mediaRepository.observe(file, kind, validUrl(url) ? url : null);
                if (id == null) return ignored(meme);
            } catch (Exception exception) {
                log.warn("媒体地址保存失败，file={}，reason={}", file, exception.getClass().getSimpleName());
            }
        }

        try {
            // 主要流程：命中 PostgreSQL 时只传描述，重复表情不再占用多模态上下文。
            var cached = mediaDescriptionMapper.findReady(file);
            if (cached.isPresent() && kind.equals(cached.get().kind())) {
                JsonNode description = objectMapper.readTree(cached.get().json());
                String text = meme ? "动画表情含义：" + description
                        : "图片内容：" + description.path("content").asText();
                return new MediaOutcome(text, null);
            }
        } catch (Exception exception) {
            log.warn("媒体描述缓存读取失败，file={}，reason={}", file,
                    exception.getClass().getSimpleName());
        }

        // 引用消息只查已有描述，不触发识别，也不把引用图片作为新图片上传。
        if (!collectImages) {
            return new MediaOutcome(meme ? "被引用动画表情（暂无描述）" : "被引用图片（暂无描述）", null);
        }
        if (!validUrl(url)) {
            return new MediaOutcome(meme ? "动画表情（图片地址不可用）" : "图片（图片地址不可用）", null);
        }

        if (meme) {
            // 首次表情先把原图送入当前 Agent 请求；描述任务在后台写库。
            ChatImageItem image = new ChatImageItem(file, summary, url);
            Long revision = null;
            try {
                revision = mediaDescriptionMapper.claim(file, kind);
                if (revision != null) {
                    long taskRevision = revision;
                    mediaDescriptionExecutor.execute(() -> recognizeAndStore(file, kind, url, taskRevision));
                }
            } catch (Exception exception) {
                log.warn("动画表情描述任务提交失败，file={}，reason={}", file,
                        exception.getClass().getSimpleName());
                try {
                    if (revision != null) mediaDescriptionMapper.markFailed(file, revision);
                } catch (Exception storeException) {
                    log.warn("动画表情描述失败状态保存失败，file={}，reason={}", file,
                            storeException.getClass().getSimpleName());
                }
            }
            return new MediaOutcome("动画表情（首次出现，请结合附带原图理解情绪与含义）", image);
        }

        // 普通图片在本轮同步识别；缓存写入失败时仍使用本次成功生成的描述。
        Long revision;
        boolean storeAvailable = true;
        try {
            revision = mediaDescriptionMapper.claim(file, kind);
        } catch (Exception exception) {
            log.warn("图片描述缓存抢占失败，file={}，reason={}", file,
                    exception.getClass().getSimpleName());
            revision = 0L;
            storeAvailable = false;
        }
        if (revision == null) {
            return new MediaOutcome("图片内容暂时无法识别", null);
        }
        try {
            JsonNode description = visionClient.describe(kind, url);
            if (storeAvailable) {
                try {
                    int saved = mediaDescriptionMapper.saveReady(file, revision, description.toString());
                    if (saved == 0) {
                        if (mediaRepository.isDeleted(file)) return ignored(false);
                        var current = mediaDescriptionMapper.findReady(file);
                        if (current.isPresent()) description = objectMapper.readTree(current.get().json());
                    }
                } catch (Exception storeException) {
                    log.warn("图片描述缓存写入失败，file={}，reason={}", file,
                            storeException.getClass().getSimpleName());
                }
            }
            return new MediaOutcome("图片内容：" + description.path("content").asText(), null);
        } catch (Exception exception) {
            log.warn("图片描述失败，file={}，reason={}", file,
                    visionFailureReason(exception));
            try {
                if (storeAvailable) mediaDescriptionMapper.markFailed(file, revision);
            } catch (Exception storeException) {
                log.warn("图片描述失败状态保存失败，file={}，reason={}", file,
                        storeException.getClass().getSimpleName());
            }
        }
        return new MediaOutcome("图片内容暂时无法识别", null);
    }

    private void recognizeAndStore(String file, String kind, String url, long revision) {
        try {
            JsonNode description = visionClient.describe(kind, url);
            mediaDescriptionMapper.saveReady(file, revision, description.toString());
        } catch (Exception exception) {
            log.warn("动画表情描述失败，file={}，reason={}", file,
                    visionFailureReason(exception));
            try {
                mediaDescriptionMapper.markFailed(file, revision);
            } catch (Exception storeException) {
                log.warn("动画表情描述失败状态保存失败，file={}，reason={}", file,
                        storeException.getClass().getSimpleName());
            }
        }
    }

    private boolean validUrl(String url) {
        try { MediaUrl.validateUrl(url); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    private MediaOutcome ignored(boolean meme) {
        return new MediaOutcome(meme ? "动画表情（已忽略）" : "图片（已忽略）", null);
    }

    private String visionFailureReason(Exception exception) {
        // 仅输出固定文案、HTTP 状态和异常类型，避免记录响应体或带签名的媒体 URL。
        if (exception instanceof IllegalStateException) {
            String message = exception.getMessage();
            if ("未配置媒体视觉模型 API Key".equals(message)
                    || "视觉模型未返回有效 content".equals(message)
                    || "视觉模型未返回 JSON 对象".equals(message)) {
                return message;
            }
        }
        if (exception instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        StringBuilder reason = new StringBuilder(exception.getClass().getSimpleName());
        Throwable cause = exception.getCause();
        for (int depth = 0; cause != null && depth < 4; depth++) {
            reason.append(" -> ").append(cause.getClass().getSimpleName());
            cause = cause.getCause();
        }
        return reason.toString();
    }
}
