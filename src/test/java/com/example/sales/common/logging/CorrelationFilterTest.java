package com.example.sales.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationFilterTest {

    final CorrelationFilter filter = new CorrelationFilter();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final AtomicReference<RequestContext> boundContext = new AtomicReference<>();
    final AtomicReference<String> mdcValue = new AtomicReference<>();

    @Test
    void reusesIncomingCorrelationId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(CorrelationFilter.HEADER, "abc-123");

        run(request);

        assertThat(response.getHeader(CorrelationFilter.HEADER)).isEqualTo("abc-123");
        assertThat(boundContext.get()).isEqualTo(new RequestContext("abc-123", null));
        assertThat(mdcValue.get()).isEqualTo("abc-123");
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        run(new MockHttpServletRequest("GET", "/api/v1/products"));

        var generated = response.getHeader(CorrelationFilter.HEADER);
        assertThat(generated).matches("[0-9a-f-]{36}");
        assertThat(boundContext.get().correlationId()).isEqualTo(generated);
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(CorrelationFilter.HEADER, "bad\nforged log line");

        run(request);

        assertThat(response.getHeader(CorrelationFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsContextAfterRequest() throws Exception {
        run(new MockHttpServletRequest("GET", "/api/v1/products"));

        assertThat(MDC.get(CorrelationFilter.MDC_KEY)).isNull();
        assertThat(RequestContext.current()).isEmpty();
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, (_, _) -> {
            boundContext.set(RequestContext.CURRENT.get());
            mdcValue.set(MDC.get(CorrelationFilter.MDC_KEY));
        });
    }
}
