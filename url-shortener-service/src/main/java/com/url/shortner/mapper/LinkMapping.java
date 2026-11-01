package com.url.shortner.mapper;

/** Only encrypted mapping values are stored in Postgres and Redis. */
public record LinkMapping(String encryptedUrl, String checksum) {}
