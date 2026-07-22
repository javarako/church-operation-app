package com.church.operation.service;

import java.util.Arrays;

public record EmailSettingsDraft(
    String host,
    int port,
    String username,
    char[] password,
    String fromAddress
) {
    public EmailSettingsDraft {
        password = password == null ? new char[0] : Arrays.copyOf(password, password.length);
    }

    @Override
    public char[] password() {
        return Arrays.copyOf(password, password.length);
    }
}
