package br.com.fiap.numberone.shared.infrastructure.correlation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdResolverTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "id\nspoof", "id\u007f"})
    void rejectsMissingAndControlCharacters(String value) {
        assertThat(CorrelationIdResolver.isValid(value)).isFalse();
    }

    @Test
    void enforcesHeaderLengthBoundary() {
        assertThat(CorrelationIdResolver.isValid("a".repeat(128))).isTrue();
        assertThat(CorrelationIdResolver.isValid("a".repeat(129))).isFalse();
    }
}
