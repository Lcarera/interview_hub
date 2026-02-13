package com.gm2dev.interview_hub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
public class TokenEncryptionService {

    private final TextEncryptor encryptor;

    public TokenEncryptionService(@Value("${app.token-encryption-key}") String encryptionKey) {
        // Derive a hex-encoded salt from the key for deterministic encryption across restarts
        String salt = String.format("%016x", encryptionKey.hashCode());
        this.encryptor = Encryptors.text(encryptionKey, salt);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        return encryptor.decrypt(cipherText);
    }
}
