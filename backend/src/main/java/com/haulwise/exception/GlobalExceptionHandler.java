package com.haulwise.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into one RFC 9457 problem shape with a {@code code} property and, for validation
 * failures, an {@code errors[]} array (D-12). Framework errors (405, 415, unknown path, ...) go through
 * {@link #handleExceptionInternal} and get a {@code code} too.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final int MAX_ECHOED_VALUE_LENGTH = 50;

    private static final String LOCAL_DATE_TIME_EXPECTATION =
            "must be a local date-time without offset, e.g. 2026-11-13T12:00:00";

    private static final Comparator<FieldViolation> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message);

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<Object> handleValidation(RequestValidationException ex) {
        return validationProblem(ex.getViolations());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        return validationProblem(ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ErrorCode.NOT_FOUND.name());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ErrorCode.CONFLICT.name());
    }

    /**
     * Backstop for database constraints the services did not catch first (e.g. concurrent inserts).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change conflicts with existing data. Reload and try again.", ErrorCode.CONFLICT.name());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Something went wrong on the server.", ErrorCode.INTERNAL_ERROR.name());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Stream<FieldViolation> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation);
        Stream<FieldViolation> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldViolation(e.getObjectName(), e.getDefaultMessage()));
        return validationProblem(Stream.concat(fieldErrors, globalErrors).toList());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", unreadableBodyDetail(ex),
                ErrorCode.MALFORMED_REQUEST.name());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : ex.getPropertyName();
        String detail = isEnum(ex.getRequiredType())
                ? "Parameter '" + name + "' must be one of " + allowedValues(ex.getRequiredType()) + "."
                : "Parameter '" + name + "' has an invalid value: '" + abbreviate(ex.getValue()) + "'.";
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", detail, ErrorCode.MALFORMED_REQUEST.name());
    }

    /** Adds the {@code code} property to problems built by Spring for framework exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail detail
                && (detail.getProperties() == null || !detail.getProperties().containsKey("code"))) {
            detail.setProperty("code", codeFor(statusCode));
        }
        return response;
    }

    private static String codeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.MALFORMED_REQUEST.name();
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND.name();
        }
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR.name();
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static String unreadableBodyDetail(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                return "Field '" + field + "' " + expectation(mismatch.getTargetType()) + ".";
            }
        }
        return "Request body is missing or is not valid JSON.";
    }

    /** What a body field of the given type must look like; enums list their (case-sensitive) values. */
    private static String expectation(Class<?> targetType) {
        if (isEnum(targetType)) {
            return "must be one of " + allowedValues(targetType);
        }
        if (LocalDateTime.class.equals(targetType)) {
            return LOCAL_DATE_TIME_EXPECTATION;
        }
        return "has an invalid value or type";
    }

    private static boolean isEnum(Class<?> type) {
        return type != null && type.isEnum();
    }

    private static String allowedValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .collect(Collectors.joining(", "));
    }

    private static String abbreviate(Object value) {
        String text = String.valueOf(value);
        return text.length() <= MAX_ECHOED_VALUE_LENGTH
                ? text : text.substring(0, MAX_ECHOED_VALUE_LENGTH) + "...";
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private static String lastNode(String propertyPath) {
        return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
    }

    private static ResponseEntity<Object> validationProblem(List<FieldViolation> violations) {
        List<FieldViolation> sorted = violations.stream().sorted(BY_FIELD_THEN_MESSAGE).toList();
        String detail = sorted.size() == 1
                ? "Request has 1 invalid field."
                : "Request has " + sorted.size() + " invalid fields.";
        ProblemDetail problem = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", detail,
                ErrorCode.VALIDATION_FAILED.name());
        problem.setProperty("errors", sorted);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String title, String detail, String code) {
        return ResponseEntity.status(status).body(problemDetail(status, title, detail, code));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
