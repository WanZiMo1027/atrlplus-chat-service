package com.atriplus.chat.client.dto;

public record PublishPromptRequest(String content, String expectedMemoryPromptId, int expectedVersion) {
}
