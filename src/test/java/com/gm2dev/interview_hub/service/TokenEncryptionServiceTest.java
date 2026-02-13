package com.gm2dev.interview_hub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private TokenEncryptionService tokenEncryptionService;

    @BeforeEach
    void setUp() {
        tokenEncryptionService = new TokenEncryptionService("test-encryption-key-32chars-long!");
    }

    @Test
    void encrypt_andDecrypt_roundTrip() {
        String plainText = "ya29.a0AfH6SMA_some_google_access_token";

        String encrypted = tokenEncryptionService.encrypt(plainText);
        String decrypted = tokenEncryptionService.decrypt(encrypted);

        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    void encrypt_returnsNull_forNullInput() {
        assertNull(tokenEncryptionService.encrypt(null));
    }

    @Test
    void decrypt_returnsNull_forNullInput() {
        assertNull(tokenEncryptionService.decrypt(null));
    }

    @Test
    void encrypt_producesDifferentCiphertext_forDifferentPlaintext() {
        String encrypted1 = tokenEncryptionService.encrypt("token-one");
        String encrypted2 = tokenEncryptionService.encrypt("token-two");

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void encrypt_isConsistentlyDecryptable_acrossMultipleCalls() {
        String plainText = "refresh-token-value";

        String encrypted = tokenEncryptionService.encrypt(plainText);
        assertEquals(plainText, tokenEncryptionService.decrypt(encrypted));
        assertEquals(plainText, tokenEncryptionService.decrypt(encrypted));
    }
}
