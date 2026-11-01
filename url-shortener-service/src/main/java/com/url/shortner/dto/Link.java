package com.url.shortner.dto;

import com.url.shortner.mapper.LinkMapping;
import jakarta.persistence.*;

@Entity
@Table(name = "short_links")
public class Link {
    @Id
    @Column(length = 32, nullable = false)
    private String code;

    @Column(nullable = false, length = 4096)
    private String encryptedUrl;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false)
    private long clicks;

    protected Link() {}

    public Link(String code, LinkMapping mapping) {
        this.code = code;
        this.encryptedUrl = mapping.encryptedUrl();
        this.checksum = mapping.checksum();
        this.clicks = 0;
    }

    public String getCode() { return code; }
    public long getClicks() { return clicks; }
    public LinkMapping getMapping() { return new LinkMapping(encryptedUrl, checksum); }
}
