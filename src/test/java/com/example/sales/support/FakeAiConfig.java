package com.example.sales.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/// Replaces the OpenAI models in the `test` profile (`spring.ai.model.*: none`), so
/// integration tests need no API key and make no paid calls. Picked up by component
/// scanning; `OpenAiIT` adds the `openai` profile to use the real models instead.
@Configuration
@Profile("test & !openai")
public class FakeAiConfig {

    /// Echoes the user message, so tests can check what context the model was given.
    public static final String ANSWER_PREFIX = "FAKE ANSWER for: ";

    @Bean
    EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    ChatModel fakeChatModel() {
        return prompt -> new ChatResponse(List.of(new Generation(
                new AssistantMessage(ANSWER_PREFIX + prompt.getUserMessage().getText()))));
    }
}
