package com.haulwise.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Paths that are hard to reach through the API: the database backstop and the catch-all.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dataIntegrityViolationBecomes409Conflict() {
        ResponseEntity<Object> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("unique index violated"));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
        assertThat(problem.getDetail()).doesNotContain("unique index");
    }

    @Test
    void unexpectedExceptionBecomes500WithGenericMessage() {
        ResponseEntity<Object> response = handler.handleUnexpected(new IllegalStateException("secret details"));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).isEqualTo("Something went wrong on the server.");
    }

    @Test
    void serviceValidationErrorsAreSortedByField() {
        RequestValidationException ex = new RequestValidationException(List.of(
                new FieldViolation("originId", "warehouse 9 does not exist"),
                new FieldViolation("destinationId", "must differ from originId")));

        ProblemDetail problem = (ProblemDetail) handler.handleValidation(ex).getBody();

        assertThat(problem.getDetail()).isEqualTo("Request has 2 invalid fields.");
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(problem.getProperties().get("errors")).asInstanceOf(list(FieldViolation.class))
                .extracting(FieldViolation::field)
                .containsExactly("destinationId", "originId");
    }
}
