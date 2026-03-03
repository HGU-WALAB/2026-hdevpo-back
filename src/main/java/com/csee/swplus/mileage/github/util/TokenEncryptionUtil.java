package com.csee.swplus.mileage.github.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts sensitive tokens (e.g. GitHub OAuth) at rest.
 * Uses AES-256-GCM. Key from env (GITHUB_TOKEN_ENCRYPTION_KEY, 32+ chars recommended).
 */
@Slf4j
public final class TokenEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String DELIM = ":";

    private TokenEncryptionUtil() {}

    /**
     * Encrypts plaintext. Returns null if key is missing or encryption fails.
     * Output format: base64(iv) + ":" + base64(ciphertext)
     */
    public static String encrypt(String plaintext, String keyBase) {
        if (plaintext == null || plaintext.isEmpty() || keyBase == null || keyBase.isEmpty()) {
            return null;
        }
        try {
            SecretKey key = deriveKey(keyBase);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivB64 = Base64.getEncoder().encodeToString(iv);
            String ctB64 = Base64.getEncoder().encodeToString(ciphertext);
            return ivB64 + DELIM + ctB64;
        } catch (Exception e) {
            log.warn("Token encryption failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts ciphertext from encrypt(). Returns null if decryption fails.
     */
    public static String decrypt(String encrypted, String keyBase) {
        if (encrypted == null || encrypted.isEmpty() || keyBase == null || keyBase.isEmpty()) {
            return null;
        }
        int idx = encrypted.indexOf(DELIM);
        if (idx <= 0) {
            return null;
        }
        try {
            byte[] iv = Base64.getDecoder().decode(encrypted.substring(0, idx));
            byte[] ct = Base64.getDecoder().decode(encrypted.substring(idx + 1));

            SecretKey key = deriveKey(keyBase);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Token decryption failed: {}", e.getMessage());
            return null;
        }
    }

    private static SecretKey deriveKey(String keyBase) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(keyBase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }
}
