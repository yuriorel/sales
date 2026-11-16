package com.example.sales.ai.api;

import com.example.sales.ai.domain.Answer;
import com.example.sales.ai.domain.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Product questions answered from the user guides; signed-in users only.
@RestController
@RequestMapping("/api/v1/ai")
class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AssistantService assistant;

    AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    /// @param productId limits the answer to one product's guide; optional
    record AskRequest(@NotBlank @Size(max = 1000) String question, @Positive Long productId) {
    }

    @PostMapping("/ask")
    Answer ask(Authentication authentication, @Valid @RequestBody AskRequest request) {
        log.debug("POST /ai/ask as {}: {}", authentication.getName(), request);
        return assistant.ask(request.question(), request.productId());
    }
}
