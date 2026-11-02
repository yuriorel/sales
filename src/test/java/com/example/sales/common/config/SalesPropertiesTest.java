package com.example.sales.common.config;

import com.example.sales.messaging.domain.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class SalesPropertiesTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "sales.persistence.provider=postgres",
                    "sales.messaging.provider=kafka",
                    "sales.messaging.topic=sales_online");

    @EnableConfigurationProperties(SalesProperties.class)
    static class Config {
    }

    @Test
    void bindsKnownProvidersIgnoringCase() {
        runner.run(context -> assertThat(context.getBean(SalesProperties.class).messaging().provider())
                .isEqualTo(SalesProperties.MessagingProvider.KAFKA));
    }

    @Test
    void unknownProviderFailsStartupNamingTheProperty() {
        runner.withPropertyValues("sales.messaging.provider=kinesis").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Failed to bind properties under 'sales.messaging.provider'")
                    .rootCause().hasMessageContaining("kinesis");
        });
    }

    /// Without the early check, the first bean that needs the missing adapter would fail first.
    @Test
    void unknownProviderIsReportedBeforeMissingAdapterBeans() {
        runner.withUserConfiguration(ProviderCheckConfig.class, NeedsPublisher.class)
                .withPropertyValues("sales.messaging.provider=kinesis")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("Failed to bind properties under 'sales.messaging.provider'")
                        .hasStackTraceContaining("kinesis"));
    }

    record NeedsPublisher(EventPublisher publisher) {
    }

    @Test
    void missingTopicFailsStartup() {
        runner.withPropertyValues("sales.messaging.topic=").run(context -> assertThat(context).hasFailed());
    }
}
