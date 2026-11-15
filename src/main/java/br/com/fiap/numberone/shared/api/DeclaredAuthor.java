package br.com.fiap.numberone.shared.api;

import java.util.UUID;

/** Declared authorship only; the caller's identity is not authenticated. */
public final class DeclaredAuthor {
    private DeclaredAuthor() { }

    public static UUID parse(String subject) {
        if (subject == null || subject.length() != 36) {
            throw new IllegalArgumentException("INVALID_AUTHOR");
        }
        UUID author = UUID.fromString(subject);
        if (!author.toString().equalsIgnoreCase(subject)) {
            throw new IllegalArgumentException("INVALID_AUTHOR");
        }
        return author;
    }
}
