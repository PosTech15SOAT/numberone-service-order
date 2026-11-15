package br.com.fiap.numberone.shared.infrastructure.logging;

import br.com.fiap.numberone.shared.application.gateways.LoggerGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SafeLoggerGateway implements LoggerGateway {
    private static final Logger LOG = LoggerFactory.getLogger(SafeLoggerGateway.class);
    public void info(String message, Object... args) { LOG.info(message, args); }
    public void infoWithContext(String message, Object... values) { LOG.info(message); }
    public void warn(String message, Object... args) { LOG.warn(message); }
    public void error(String message, Object... args) { LOG.error(message); }
    public void error(String message, Throwable error, Object... args) {
        LOG.error("Operation failed: {}", error.getClass().getSimpleName());
    }
}
