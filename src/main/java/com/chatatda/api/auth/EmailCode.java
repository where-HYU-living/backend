package com.chatatda.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_codes")
public class EmailCode {

    @Id
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected EmailCode() {
    }

    public EmailCode(String email, String codeHash, Instant sentAt, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
    }

    public void renew(String codeHash, Instant sentAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public void recordFailure() {
        attempts++;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
