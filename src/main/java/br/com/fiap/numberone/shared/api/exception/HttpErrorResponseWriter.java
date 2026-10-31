package br.com.fiap.numberone.shared.api.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class HttpErrorResponseWriter {
    // Only fixed messages are emitted. Never echo headers, payloads or exception messages.
    public void write(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        String title = status == 400 ? "Invalid correlation header" : "Access denied";
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + "}");
    }
}
