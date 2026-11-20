package com.example.crm.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApiException(ApiException ex) {
        return build(HttpStatusCode.valueOf(ex.getStatus()), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldIssue(toSnakeCase(error.getField()), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldIssue::field))
                .toList();
        return validationError(details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return validationError(List.of(new FieldIssue(jsonPath(unknown), "unknown field")));
        }
        if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return validationError(List.of(new FieldIssue(jsonPath(mapping), "has invalid type or format")));
        }
        return validationError(List.of(new FieldIssue("body", "malformed JSON or missing request body")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorBody> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationError(List.of(new FieldIssue(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationError(List.of(new FieldIssue(ex.getName(), "has invalid value '%s'".formatted(ex.getValue()))));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatusCode.valueOf(409), "conflict", "Request conflicts with existing data", List.of());
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class
    })
    public ResponseEntity<ErrorBody> handleFrameworkError(Exception ex) {
        ErrorResponse response = (ErrorResponse) ex;
        return build(response.getStatusCode(), "http_error", response.getBody().getDetail(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatusCode.valueOf(500), "internal_error", "Unexpected server error", List.of());
    }

    private ResponseEntity<ErrorBody> validationError(List<FieldIssue> details) {
        return build(HttpStatusCode.valueOf(422), "validation_error", ValidationException.MESSAGE, details);
    }

    private ResponseEntity<ErrorBody> build(HttpStatusCode status, String code, String message, List<FieldIssue> details) {
        return ResponseEntity.status(status).body(ErrorBody.of(code, message, details));
    }

    private static String jsonPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : String.valueOf(ref.getIndex()))
                .collect(Collectors.joining("."));
    }

    private static String toSnakeCase(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
