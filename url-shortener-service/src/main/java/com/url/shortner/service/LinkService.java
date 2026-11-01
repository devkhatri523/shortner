package com.url.shortner.service;

import com.url.shortner.dto.Link;
import com.url.shortner.dto.ShortenerProperties;
import com.url.shortner.exception.MappingIntegrityException;
import com.url.shortner.exception.ShortCodeCollisionException;
import com.url.shortner.mapper.RedisMappingCache;
import com.url.shortner.repo.LinkRepository;
import com.url.shortner.util.ShortCodeGenerator;
import com.url.shortner.util.UrlValidator;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LinkService {
    private final LinkRepository repository;
    private final ShortCodeGenerator generator;
    private final LinkInsertionService insertion;
    private final MappingCryptoService crypto;
    private final RedisMappingCache cache;
    private final UrlValidator validator;
    private final RetryTemplate retry;
    private final ShortenerProperties properties;

    public LinkService(LinkRepository repository, ShortCodeGenerator generator,
                       LinkInsertionService insertion, MappingCryptoService crypto,
                       RedisMappingCache cache, UrlValidator validator,
                       RetryTemplate shortCodeRetryTemplate, ShortenerProperties properties) {
        this.repository = repository;
        this.generator = generator;
        this.insertion = insertion;
        this.crypto = crypto;
        this.cache = cache;
        this.validator = validator;
        this.retry = shortCodeRetryTemplate;
        this.properties = properties;
    }

    public Link create(String originalUrl) {
        validator.validate(originalUrl);
        try {
            Link saved = retry.execute(context -> insertion.insert(generator.generate(), originalUrl));
            cache.put(saved.getCode(), saved.getMapping());
            return saved;
        } catch (ShortCodeCollisionException exhausted) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to allocate a unique short code; try again later", exhausted);
        }
    }

    public String shortUrl(String code) {
        return UriComponentsBuilder.fromUri(properties.baseUrl())
                .pathSegment(code).build().toUriString();
    }

    public String resolve(String code) {
        String url = null;
        var cached = cache.get(code);
        if (cached.isPresent()) {
            try {
                url = crypto.decrypt(code, cached.get());
            } catch (MappingIntegrityException e) {
                // Corrupt cached data: never redirect using it; reload trusted database data.
                cache.evict(code);
            }
        }
        if (url == null) {
            Link link = find(code);
            url = crypto.decrypt(code, link.getMapping());
            cache.put(code, link.getMapping());
        }
        // A DB write occurs for EVERY redirect, including cache hits.
        // If this fails, do not issue a redirect that would silently lose the click.
        if (repository.incrementClicks(code) != 1) {
            cache.evict(code);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown short code");
        }
        return url;
    }

    public Link find(String code) {
        return repository.findById(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown short code"));
    }

    public String originalUrl(Link link) {
        return crypto.decrypt(link.getCode(), link.getMapping());
    }
}
