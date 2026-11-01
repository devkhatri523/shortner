package com.url.shortner.util;

import com.url.shortner.dto.ShortenerProperties;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {
    private static final char[] ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final ShortenerProperties properties;

    public ShortCodeGenerator(ShortenerProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(properties.code().length());
        for (int i = 0; i < properties.code().length(); i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
