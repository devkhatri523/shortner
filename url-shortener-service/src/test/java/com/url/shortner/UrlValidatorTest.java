package com.url.shortner;

import com.url.shortner.util.UrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {
    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttps() {
        assertDoesNotThrow(() -> validator.validate("https://example.com/path?x=1"));
    }

    @Test
    void rejectsUnsafeSchemesAndCredentials() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("javascript:alert('hello')"));
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("https://alice:secret@example.com/"));
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("not a url"));
    }
}
