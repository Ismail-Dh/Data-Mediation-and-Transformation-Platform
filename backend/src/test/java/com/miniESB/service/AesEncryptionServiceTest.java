package com.miniESB.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AesEncryptionService — unit tests")
class AesEncryptionServiceTest {

    private AesEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new AesEncryptionService();
        // aesKey is normally injected via @Value; AES-128 requires a 16-byte key.
        ReflectionTestUtils.setField(service, "aesKey", "0123456789abcdef");
    }

    @Test
    @DisplayName("encrypting then decrypting returns the original plain text")
    void encryptDecryptRoundTrip() {
        String plain = "sensitive-data-123";

        String encrypted = service.encrypt(plain);
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("encrypted value differs from the plain text and is Base64-encoded")
    void encryptedValueIsBase64AndDiffers() {
        String plain = "hello world";
        String encrypted = service.encrypt(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThatCode(() -> java.util.Base64.getDecoder().decode(encrypted)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("encrypting the same plain text twice yields the same cipher text (fixed IV)")
    void deterministicEncryption() {
        String plain = "repeatable";
        assertThat(service.encrypt(plain)).isEqualTo(service.encrypt(plain));
    }

    @Test
    @DisplayName("decrypting an invalid Base64 string throws a RuntimeException")
    void decryptInvalidInputThrows() {
        assertThatThrownBy(() -> service.decrypt("not-valid-base64!!"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    @DisplayName("supports empty string round-trip")
    void emptyStringRoundTrip() {
        String encrypted = service.encrypt("");
        assertThat(service.decrypt(encrypted)).isEqualTo("");
    }
}
