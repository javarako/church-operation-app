package com.church.operation.dto;

import com.church.operation.util.Role;

import java.util.Set;

public record LoginResponse(
    String primaryEmail,
    String displayName,
    Set<Role> roles,
    boolean mustChangePassword,
    String token
) {
}
