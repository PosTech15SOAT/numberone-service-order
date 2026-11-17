package br.com.fiap.numberone.shared.infrastructure.correlation;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalCorrelationMdcTest {
    @AfterEach
    void clearContext() { MDC.clear(); }

    @Test
    void preservesExistingContextWhenRequestFails() {
        String correlation = UUID.randomUUID().toString();
        MDC.put("correlation_id", "previous");
        MDC.put("dd.trace_id", "trace");
        MDC.put("dd.span_id", "span");
        var request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader("X-Correlation-Id", correlation);
        var response = new MockHttpServletResponse();
        var filter = new InternalCorrelationIdFilter(new ObjectMapper());
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("correlation_id")).isEqualTo(correlation);
            assertThat(MDC.get("correlation.id")).isNull();
            throw new ServletException("failure");
        })).isInstanceOf(ServletException.class);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(correlation);
        assertThat(MDC.get("correlation_id")).isEqualTo("previous");
        assertThat(MDC.get("dd.trace_id")).isEqualTo("trace");
        assertThat(MDC.get("dd.span_id")).isEqualTo("span");
    }
}
