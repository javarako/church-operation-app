package com.church.operation.service;

import com.church.operation.util.EmailSettingsSource;

import java.util.Arrays;

public record EffectiveEmailSettings(
    String host,
    int port,
    String username,
    char[] password,
    boolean auth,
    boolean startTls,
    String fromAddress,
    EmailSettingsSource source
) {
    public EffectiveEmailSettings {
        password = password == null ? new char[0] : Arrays.copyOf(password, password.length);
    }

    @Override
    public char[] password() {
        return Arrays.copyOf(password, password.length);
    }

    public void clearPassword() {
        Arrays.fill(password, '\0');
    }
}
