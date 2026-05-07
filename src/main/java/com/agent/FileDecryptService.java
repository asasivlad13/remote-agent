package com.agent;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

public class FileDecryptService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_SIZE_BITS = 128;

    public void decrypt(InputStream encryptedInputStream,
                        OutputStream decryptedOutputStream,
                        String base64Key,
                        String base64Iv) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        byte[] ivBytes = Base64.getDecoder().decode(base64Iv);

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE_BITS, ivBytes);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        try (CipherInputStream cipherInputStream = new CipherInputStream(encryptedInputStream, cipher)) {
            cipherInputStream.transferTo(decryptedOutputStream);
        }
    }
}