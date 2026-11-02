package com.example.sales.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// Application settings under `sales.*`.
///
/// The providers are enums, so an unknown value (for example `dynamodb` before that
/// adapter exists) stops startup with a binding error that lists the valid values.
/// Adapters are switched on with `@ConditionalOnProperty` on the same properties.
@ConfigurationProperties("sales")
@Validated
public record SalesProperties(@Valid @NotNull Persistence persistence, @Valid @NotNull Messaging messaging) {

    public enum PersistenceProvider { POSTGRES }

    public enum MessagingProvider { KAFKA }

    public record Persistence(@NotNull PersistenceProvider provider) {
    }

    /// @param topic the topic (Kafka) or stream that carries [com.example.sales.messaging.domain.SalesEvent]s
    public record Messaging(@NotNull MessagingProvider provider, @NotBlank String topic) {
    }
}
