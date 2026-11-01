package com.url.shortner.service;

import com.url.shortner.mapper.LinkMapping;
import com.url.shortner.exception.MappingIntegrityException;
import com.url.shortner.dto.SecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** AES-256-GCM protects confidentiality; HMAC-SHA256 is a separate mapping checksum. */
@Service
public class MappingCryptoService {
    private static final int NONCE_SIZE = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hmacKey;

    public MappingCryptoService(SecurityProperties properties) {
        byte[] enc = decodeKey(properties.encryptionKey(), "APP_ENCRYPTION_KEY");
        byte[] mac = decodeKey(properties.hmacKey(), "APP_HMAC_KEY");
        this.encryptionKey = new SecretKeySpec(enc, "AES");
        this.hmacKey = new SecretKeySpec(mac, "HmacSHA256");
    }

    private static byte[] decodeKey(String base64, String name) {
        try {
            byte[] key = Base64.getDecoder().decode(base64);
            if (key.length != 32) throw new IllegalArgumentException(name + " must decode to 32 bytes");
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be a Base64-encoded 32-byte secret", e);
        }
    }

    public LinkMapping encrypt(String code, String url) {
        try {
            byte[] nonce = new byte[NONCE_SIZE];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(code.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array();
            String value = Base64.getEncoder().encodeToString(encrypted);
            return new LinkMapping(value, checksum(code, value));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt URL mapping", e);
        }
    }

    public String decrypt(String code, LinkMapping mapping) {
        if (mapping == null || mapping.encryptedUrl() == null || mapping.checksum() == null) {
            throw new MappingIntegrityException("Missing mapping data");
        }
        byte[] expected = checksum(code, mapping.encryptedUrl()).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = mapping.checksum().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new MappingIntegrityException("HMAC checksum mismatch");
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(mapping.encryptedUrl());
            if (encrypted.length < NONCE_SIZE + 16) {
                throw new MappingIntegrityException("Invalid ciphertext length");
            }
            byte[] nonce = new byte[NONCE_SIZE];
            byte[] ciphertext = new byte[encrypted.length - NONCE_SIZE];
            System.arraycopy(encrypted, 0, nonce, 0, NONCE_SIZE);
            System.arraycopy(encrypted, NONCE_SIZE, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(code.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new MappingIntegrityException("Cannot authenticate/decrypt URL mapping", e);
        }
    }

    private String checksum(String code, String encryptedUrl) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            mac.update(code.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(encryptedUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot calculate mapping HMAC", e);
        }
    }
}
