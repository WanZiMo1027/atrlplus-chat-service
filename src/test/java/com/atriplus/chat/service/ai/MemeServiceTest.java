package com.atriplus.chat.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemeServiceTest {

    private static final String LIBRARY_JSON = """
            [
              {"id":"a","name":"候选一","tags":[" 开心 "],"url":"https://a","md5":"a","enabled":true},
              {"id":"b","name":"候选二","tags":["开心"],"url":"https://b","md5":"b","enabled":true},
              {"id":"c","name":"禁用候选","tags":["开心"],"url":"https://c","md5":"c","enabled":false},
              {"id":"d","name":"仅名称命中","tags":["其他"],"url":"https://d","md5":"d","enabled":true}
            ]
            """;

    @Test
    void loadsBundledLibrary() {
        MemeService service = new MemeService(new ObjectMapper());

        assertTrue(service.findByTag("开心").isPresent());
    }

    @Test
    void matchesTrimmedExactTagsAndFiltersDisabledItems() {
        MemeService service = service(LIBRARY_JSON);

        Set<String> selectedIds = IntStream.range(0, 30)
                .mapToObj(ignored -> service.findByTag("  开心  ").orElseThrow().id())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("a", "b"), selectedIds);
        assertFalse(service.findByTag("开").isPresent());
        assertFalse(service.findByTag("仅名称命中").isPresent());
        assertFalse(service.findByTag(null).isPresent());
        assertFalse(service.findByTag("  ").isPresent());
    }

    @Test
    void failsFastWhenLibraryIsMissingOrMalformed() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThrows(IllegalStateException.class, () -> new MemeService(
                objectMapper,
                new ClassPathResource("memes/missing.json"),
                new Random(0)
        ));
        assertThrows(IllegalStateException.class, () -> new MemeService(
                objectMapper,
                resource("not-json"),
                new Random(0)
        ));
    }

    private MemeService service(String json) {
        return new MemeService(new ObjectMapper(), resource(json), new Random(0));
    }

    private static ByteArrayResource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
