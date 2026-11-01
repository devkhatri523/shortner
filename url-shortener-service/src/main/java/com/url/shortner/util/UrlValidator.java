package com.url.shortner.util;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;

@Component
public class UrlValidator {
    public void validate(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank() || originalUrl.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required and must be <= 2048 characters");
        }
        try {
            URI uri = URI.create(originalUrl);
            if (uri.getHost() == null || uri.getRawUserInfo() != null ||
                    !("http".equalsIgnoreCase(uri.getScheme()) ||
                      "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only HTTP(S) URLs with a valid host are supported");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL", e);
        }
    }
}
