package com.example.sales.common.concurrency;

import org.slf4j.MDC;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.ThreadFactory;

/// Structured-concurrency helpers.
///
/// Subtasks run on virtual threads. They inherit the caller's scoped values
/// (`RequestContext`) automatically; this class also copies the logging MDC, so
/// subtask log lines keep the correlation id.
///
/// When a subtask fails, the scope interrupts the others. Interrupting a JDBC call
/// closes its connection, so subtasks should report expected outcomes (such as "not
/// found") as values and fail only on real errors.
public final class Structured {

    private Structured() {
    }

    /// A scope that waits for all subtasks and cancels the rest when one fails.
    public static <T> StructuredTaskScope<T, Void> open() {
        return StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow(),
                config -> config.withThreadFactory(virtualThreadsWithMdc()));
    }

    /// Joins `scope`. A subtask's unchecked exception is rethrown as is, so a
    /// `NotFoundException` from a subtask still becomes a 404.
    public static void join(StructuredTaskScope<?, ?> scope) {
        try {
            scope.join();
        } catch (StructuredTaskScope.FailedException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for subtasks", e);
        }
    }

    private static ThreadFactory virtualThreadsWithMdc() {
        var mdc = MDC.getCopyOfContextMap();
        return task -> Thread.ofVirtual().unstarted(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        });
    }
}
