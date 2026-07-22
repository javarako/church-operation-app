package com.church.operation.service;

import com.church.operation.config.RuntimeEmailProperties;
import com.church.operation.exception.EmailConfigurationException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class EmailSettingsCrypto {
    private static final String INVALID_KEY_MESSAGE =
        "CHURCH_SETTINGS_ENCRYPTION_KEY must be a Base64-encoded 256-bit key.";
    private static final String DECRYPTION_MESSAGE =
        "The saved email password cannot be decrypted with this server configuration.";
    private static final byte[] ENCRYPTION_LABEL =
        "email-settings-encryption-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERIFICATION_LABEL =
        "email-settings-verification-v1".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int VERSION = 1;

    private final String encodedMasterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailSettingsCrypto(RuntimeEmailProperties properties) {
        this.encodedMasterKey = properties.encryptionKey();
    }

    public EncryptedSecret encrypt(char[] plaintext) {
        DerivedKeys keys = keys();
        byte[] nonce = new byte[NONCE_LENGTH];
        byte[] clearBytes = null;
        try {
            secureRandom.nextBytes(nonce);
            clearBytes = new String(plaintext).getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(clearBytes);
            return new EncryptedSecret(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(nonce),
                VERSION
            );
        } catch (GeneralSecurityException exception) {
            throw new EmailConfigurationException("The email password could not be encrypted.");
        } finally {
            if (clearBytes != null) {
                Arrays.fill(clearBytes, (byte) 0);
            }
            keys.clear();
        }
    }

    public char[] decrypt(EncryptedSecret secret) {
        DerivedKeys keys = keys();
        byte[] clearBytes = null;
        try {
            if (secret == null || secret.version() != VERSION) {
                throw new GeneralSecurityException("Unsupported encrypted secret.");
            }
            byte[] ciphertext = Base64.getDecoder().decode(secret.ciphertext());
            byte[] nonce = Base64.getDecoder().decode(secret.nonce());
            if (nonce.length != NONCE_LENGTH) {
                throw new GeneralSecurityException("Invalid nonce.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            clearBytes = cipher.doFinal(ciphertext);
            return new String(clearBytes, StandardCharsets.UTF_8).toCharArray();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new EmailConfigurationException(DECRYPTION_MESSAGE);
        } finally {
            if (clearBytes != null) {
                Arrays.fill(clearBytes, (byte) 0);
            }
            keys.clear();
        }
    }

    public byte[] fingerprint(String canonicalSettings) {
        DerivedKeys keys = keys();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keys.verificationKey());
            return mac.doFinal(canonicalSettings.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new EmailConfigurationException("The email settings could not be verified securely.");
        } finally {
            keys.clear();
        }
    }

    private DerivedKeys keys() {
        byte[] master;
        try {
            master = Base64.getDecoder().decode(encodedMasterKey == null ? "" : encodedMasterKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new EmailConfigurationException(INVALID_KEY_MESSAGE);
        }
        if (master.length != 32) {
            Arrays.fill(master, (byte) 0);
            throw new EmailConfigurationException(INVALID_KEY_MESSAGE);
        }
        try {
            byte[] encryption = derive(master, ENCRYPTION_LABEL);
            byte[] verification = derive(master, VERIFICATION_LABEL);
            return new DerivedKeys(
                new SecretKeySpec(encryption, "AES"),
                new SecretKeySpec(verification, "HmacSHA256"),
                encryption,
                verification
            );
        } catch (GeneralSecurityException exception) {
            throw new EmailConfigurationException(INVALID_KEY_MESSAGE);
        } finally {
            Arrays.fill(master, (byte) 0);
        }
    }

    private byte[] derive(byte[] master, byte[] label) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(master, "HmacSHA256"));
        return mac.doFinal(label);
    }

    public record EncryptedSecret(String ciphertext, String nonce, int version) {
    }

    private record DerivedKeys(
        SecretKeySpec encryptionKey,
        SecretKeySpec verificationKey,
        byte[] encryptionBytes,
        byte[] verificationBytes
    ) {
        void clear() {
            Arrays.fill(encryptionBytes, (byte) 0);
            Arrays.fill(verificationBytes, (byte) 0);
        }
    }
}
