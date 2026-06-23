// service/AesEncryptionService.java
package com.miniESB.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Slf4j
@Service
public class AesEncryptionService {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    @Value("${aes.key}")
    private String aesKey;

    // IV fixe de 16 bytes dérivé de la clé — simple et suffisant pour ce cas
    private byte[] getIv() {
        byte[] key = aesKey.getBytes();
        byte[] iv = new byte[16];
        System.arraycopy(key, 0, iv, 0, Math.min(key.length, 16));
        return iv;
    }

    public String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    aesKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec,
                    new IvParameterSpec(getIv()));
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    aesKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                    new IvParameterSpec(getIv()));
            byte[] decoded  = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}