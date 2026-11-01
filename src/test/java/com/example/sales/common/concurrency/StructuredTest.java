package com.example.sales.common.concurrency;

import com.example.sales.common.error.NotFoundException;
import com.example.sales.common.logging.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void subtasksSeeScopedValueAndMdcOnVirtualThreads() throws Exception {
        MDC.put("correlationId", "corr-1");
        var context = new RequestContext("corr-1", "alice");

        var seen = ScopedValue.where(RequestContext.CURRENT, context).call(() -> {
            try (var scope = Structured.<String>open()) {
                var subtask = scope.fork(() -> RequestContext.CURRENT.get().user() + "/" + MDC.get("correlationId")
                        + "/" + Thread.currentThread().isVirtual());
                Structured.join(scope);
                return subtask.get();
            }
        });

        assertThat(seen).isEqualTo("alice/corr-1/true");
    }

    @Test
    void failingSubtaskRethrowsItsExceptionAndCancelsTheOthers() {
        var interrupted = new AtomicBoolean();

        assertThatThrownBy(() -> {
            try (var scope = Structured.open()) {
                scope.fork(() -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException _) {
                        interrupted.set(true);
                    }
                    return null;
                });
                scope.fork(() -> {
                    throw new NotFoundException("Product", 1);
                });
                Structured.join(scope);
            }
        }).isInstanceOf(NotFoundException.class);
        assertThat(interrupted).isTrue();
    }
}
