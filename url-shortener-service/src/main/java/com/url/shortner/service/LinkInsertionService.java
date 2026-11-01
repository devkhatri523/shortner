package com.url.shortner.service;

import com.url.shortner.dto.Link;
import com.url.shortner.repo.LinkRepository;
import com.url.shortner.exception.ShortCodeCollisionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Each retry gets a fresh transaction; a uniqueness failure rolls back this attempt. */
@Service
public class LinkInsertionService {
    private final LinkRepository repository;
    private final MappingCryptoService crypto;

    public LinkInsertionService(LinkRepository repository, MappingCryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Link insert(String code, String originalUrl) {
        try {
            return repository.saveAndFlush(new Link(code, crypto.encrypt(code, originalUrl)));
        } catch (DataIntegrityViolationException e) {
            throw new ShortCodeCollisionException("Generated short code already exists", e);
        }
    }
}
