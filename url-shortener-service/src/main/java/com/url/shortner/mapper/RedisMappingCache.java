package com.url.shortner.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortner.dto.ShortenerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class RedisMappingCache {
    private static final Logger log = LoggerFactory.getLogger(RedisMappingCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ShortenerProperties properties;

    public RedisMappingCache(StringRedisTemplate redis, ObjectMapper mapper,
                             ShortenerProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    private String key(String code) { return "short-url:" + code; }

    public Optional<LinkMapping> get(String code) {
        String value;
        try {
            value = redis.opsForValue().get(key(code));
        } catch (DataAccessException e) {
            log.warn("Redis read unavailable; falling back to Postgres");
            return Optional.empty();
        }
        if (value == null) return Optional.empty();
        try {
            LinkMapping mapping = mapper.readValue(value, LinkMapping.class);
            if (mapping == null) {
                evict(code);
                return Optional.empty();
            }
            return Optional.of(mapping);
        } catch (JsonProcessingException e) {
            log.warn("Malformed cached mapping for code {}; evicting", code);
            evict(code);
            return Optional.empty();
        }
    }

    public void put(String code, LinkMapping mapping) {
        try {
            redis.opsForValue().set(key(code), mapper.writeValueAsString(mapping), properties.cache().ttl());
        } catch (DataAccessException e) {
            log.warn("Redis write unavailable; serving from Postgres");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize encrypted mapping", e);
        }
    }

    public void evict(String code) {
        try { redis.delete(key(code)); }
        catch (DataAccessException e) { log.warn("Redis eviction unavailable"); }
    }
}
