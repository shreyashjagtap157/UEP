package com.universalplatform.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GradebookApiExceptionHandlerTest {
    @Test
    void guardianAccessDenialIsReturnedAsForbiddenWithoutInternalDetails() {
        var response = new GradebookApiExceptionHandler().forbidden(new SecurityException("guardian relationship is not verified"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .containsEntry("code", "GRADEBOOK_ACCESS_DENIED")
                .containsEntry("message", "Gradebook access is not permitted");
    }
}
