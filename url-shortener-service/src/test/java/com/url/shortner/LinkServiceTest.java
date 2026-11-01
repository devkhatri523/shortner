package com.url.shortner;

import com.url.shortner.dto.Link;
import com.url.shortner.dto.SecurityProperties;
import com.url.shortner.dto.ShortenerProperties;
import com.url.shortner.exception.ShortCodeCollisionException;
import com.url.shortner.mapper.LinkMapping;
import com.url.shortner.mapper.RedisMappingCache;
import com.url.shortner.repo.LinkRepository;
import com.url.shortner.service.LinkInsertionService;
import com.url.shortner.service.LinkService;
import com.url.shortner.service.MappingCryptoService;
import com.url.shortner.util.ShortCodeGenerator;
import com.url.shortner.util.UrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {
    @Mock
    LinkRepository repository;
    @Mock
    ShortCodeGenerator generator;
    @Mock
    LinkInsertionService insertion;
    @Mock
    RedisMappingCache cache;
    private LinkService service;
    private MappingCryptoService crypto;

    @BeforeEach
    void setup() {
        var properties = new ShortenerProperties(
            URI.create("https://short.example"),
            new ShortenerProperties.Code(8, 3, Duration.ofMillis(1)),
            new ShortenerProperties.Cache(Duration.ofHours(1)));
        var enc = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        byte[] macKey = new byte[32]; java.util.Arrays.fill(macKey, (byte) 7);
        var mac = java.util.Base64.getEncoder().encodeToString(macKey);
        crypto = new MappingCryptoService(new SecurityProperties(enc, mac));
        RetryTemplate retry = RetryTemplate.builder()
                .maxAttempts(properties.code().maxAttempts())
                .fixedBackoff(properties.code().retryBackoff().toMillis())
                .retryOn(ShortCodeCollisionException.class).build();
        service = new LinkService(repository, generator, insertion, crypto, cache,
                new UrlValidator(), retry, properties);
    }

    @Test
    void retriesOnlyCollisionsThenCreatesUrl() {
        String url = "https://example.com/path";
        when(generator.generate()).thenReturn("duplicate", "unique12");
        when(insertion.insert("duplicate", url)).thenThrow(
                new ShortCodeCollisionException("collision", new RuntimeException()));
        Link saved = new Link("unique12", crypto.encrypt("unique12", url));
        when(insertion.insert("unique12", url)).thenReturn(saved);
        assertEquals("https://short.example/unique12", service.shortUrl(service.create(url).getCode()));
        verify(insertion, times(2)).insert(anyString(), eq(url));
        verify(cache).put(eq("unique12"), any(LinkMapping.class));
    }

    @Test
    void stopsAfterConfiguredAttemptLimit() {
        String url = "https://example.com";
        when(generator.generate()).thenReturn("duplicate");
        when(insertion.insert(anyString(), eq(url))).thenThrow(
                new ShortCodeCollisionException("collision", new RuntimeException()));
        assertThrows(ResponseStatusException.class, () -> service.create(url));
        verify(insertion, times(3)).insert(anyString(), eq(url));
    }

    @Test
    void cacheHitStillIncrementsClickCount() {
        String code = "abcdefgh";
        String url = "https://example.com";
        when(cache.get(code)).thenReturn(Optional.of(crypto.encrypt(code, url)));
        when(repository.incrementClicks(code)).thenReturn(1);
        assertEquals(url, service.resolve(code));
        verify(repository, never()).findById(anyString());
        verify(repository).incrementClicks(code);
    }

    @Test
    void corruptCachedDataFallsBackToDatabase() {
        String code = "abcdefgh";
        String url = "https://example.com";
        when(cache.get(code)).thenReturn(Optional.of(new LinkMapping("bad", "0".repeat(64))));
        Link link = new Link(code, crypto.encrypt(code, url));
        when(repository.findById(code)).thenReturn(Optional.of(link));
        when(repository.incrementClicks(code)).thenReturn(1);
        assertEquals(url, service.resolve(code));
        verify(cache).evict(code);
        verify(cache).put(eq(code), any(LinkMapping.class));
    }
}
