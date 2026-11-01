package com.universalplatform.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AssignmentApiExceptionHandlerTest {
    @Test
    void tenantScopedAssignmentMissIsNotFoundWithoutRevealingWhetherAnotherTenantOwnsIt() {
        var response = new AssignmentApiExceptionHandler().notFound(new AssignmentNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "ASSIGNMENT_NOT_FOUND")
                .containsEntry("message", "Assignment not found");
    }
}
