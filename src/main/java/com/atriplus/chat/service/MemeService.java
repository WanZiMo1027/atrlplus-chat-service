package com.atriplus.chat.service;

import com.atriplus.chat.dto.Meme;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

/** 加载并按语义标签选择启用的 QQ 表情。 */
@Service
@Profile("snowluma")
public class MemeService {

    private static final String MEME_LIBRARY_PATH = "memes/custom-faces.json";

    private final List<Meme> memes;
    private final RandomGenerator random;

    @Autowired
    public MemeService(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(MEME_LIBRARY_PATH), new Random());
    }

    MemeService(ObjectMapper objectMapper, Resource resource, RandomGenerator random) {
        this.memes = loadMemes(objectMapper, resource);
        this.random = random;
    }

    /**
     * 按语义标签随机选择一个启用的表情。
     */
    public Optional<Meme> findByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }

        String normalizedTag = tag.trim();
        List<Meme> candidates = memes.stream()
                .filter(Meme::enabled)
                .filter(meme -> meme.tags().stream()
                        .filter(candidateTag -> candidateTag != null)
                        .map(String::trim)
                        .anyMatch(normalizedTag::equals))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    private static List<Meme> loadMemes(ObjectMapper objectMapper, Resource resource) {
        // 主要流程：应用启动时一次性载入表情库，配置损坏时直接阻止服务启动。
        try (InputStream inputStream = resource.getInputStream()) {
            Meme[] loaded = objectMapper.readValue(inputStream, Meme[].class);
            return List.copyOf(Arrays.asList(loaded));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Failed to load meme library: " + resource.getDescription(),
                    exception
            );
        }
    }
}
