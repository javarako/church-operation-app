package com.church.operation.rest;

import com.church.operation.dto.ForgotPasswordRequest;
import com.church.operation.dto.ResetPasswordRequest;
import com.church.operation.service.AuthService;
import com.church.operation.service.PasswordResetService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTest {
    private final AuthService authService = mock(AuthService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final AuthController controller = new AuthController(authService, passwordResetService);

    @Test
    void forgotPasswordAlwaysReturnsGenericAcceptedResponse() {
        var response = controller.forgotPassword(new ForgotPasswordRequest("member@example.com"));

        verify(passwordResetService).requestReset("member@example.com");
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry(
            "message",
            "If an account matches that email, a password reset link has been sent."
        );
    }

    @Test
    void resetPasswordReturnsNoContent() {
        var response = controller.resetPassword(new ResetPasswordRequest("reset-token", "new-password"));

        verify(passwordResetService).resetPassword("reset-token", "new-password");
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void logoutRevokesBearerTokenAndReturnsNoContent() {
        var response = controller.logout("Bearer issued-token");

        verify(authService).logout("issued-token");
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
