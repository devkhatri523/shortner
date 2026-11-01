package com.url.shortner;

import com.url.shortner.dto.SecurityProperties;
import com.url.shortner.exception.MappingIntegrityException;
import com.url.shortner.mapper.LinkMapping;
import com.url.shortner.service.MappingCryptoService;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MappingCryptoServiceTest {
    private MappingCryptoService crypto() {
        byte[] enc = new byte[32]; Arrays.fill(enc, (byte) 1);
        byte[] mac = new byte[32]; Arrays.fill(mac, (byte) 2);
        return new MappingCryptoService(new SecurityProperties(
                Base64.getEncoder().encodeToString(enc), Base64.getEncoder().encodeToString(mac)));
    }

    @Test
    void encryptsAndDecryptsWithoutPlaintextInStoredMapping() {
        MappingCryptoService service = crypto();
        LinkMapping mapping = service.encrypt("abcd1234", "https://example.com/private");
        assertFalse(mapping.encryptedUrl().contains("example.com"));
        assertEquals("https://example.com/private", service.decrypt("abcd1234", mapping));
    }

    @Test
    void rejectsTamperedChecksumOrDifferentCode() {
        MappingCryptoService service = crypto();
        LinkMapping mapping = service.encrypt("abcd1234", "https://example.com");
        assertThrows(MappingIntegrityException.class, () ->
                service.decrypt("othercode", mapping));
        assertThrows(MappingIntegrityException.class, () ->
                service.decrypt("abcd1234", new LinkMapping(mapping.encryptedUrl(), "0".repeat(64))));
    }

    @Test
    void encryptingSameUrlTwiceUsesDifferentRandomNonces() {
        MappingCryptoService service = crypto();
        assertNotEquals(service.encrypt("abc12345", "https://example.com").encryptedUrl(),
                service.encrypt("abc12345", "https://example.com").encryptedUrl());
    }
}
