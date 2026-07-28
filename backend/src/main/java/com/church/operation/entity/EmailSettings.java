package com.church.operation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("email_settings")
public class EmailSettings {
    public static final String SINGLETON_ID = "runtime-email";

    @Id
    private String id = SINGLETON_ID;
    private String host;
    private int port;
    private String username;
    private String fromAddress;
    private String passwordCiphertext;
    private String passwordNonce;
    private int cipherVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByMemberId;
    private String updatedByMemberId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getPasswordCiphertext() { return passwordCiphertext; }
    public void setPasswordCiphertext(String passwordCiphertext) { this.passwordCiphertext = passwordCiphertext; }
    public String getPasswordNonce() { return passwordNonce; }
    public void setPasswordNonce(String passwordNonce) { this.passwordNonce = passwordNonce; }
    public int getCipherVersion() { return cipherVersion; }
    public void setCipherVersion(int cipherVersion) { this.cipherVersion = cipherVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedByMemberId() { return createdByMemberId; }
    public void setCreatedByMemberId(String createdByMemberId) { this.createdByMemberId = createdByMemberId; }
    public String getUpdatedByMemberId() { return updatedByMemberId; }
    public void setUpdatedByMemberId(String updatedByMemberId) { this.updatedByMemberId = updatedByMemberId; }

    public boolean hasEncryptedPassword() {
        return passwordCiphertext != null && !passwordCiphertext.isBlank()
            && passwordNonce != null && !passwordNonce.isBlank();
    }
}
