package com.example.sales.common.error;

import com.example.sales.common.logging.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;

/// Turns every exception into an RFC 9457 `application/problem+json` response that
/// carries the request's `correlationId`. Validation failures add an `errors` list.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SalesException.class)
    ResponseEntity<Object> handleSales(SalesException ex) {
        var status = switch (ex) {
            case NotFoundException _ -> HttpStatus.NOT_FOUND;
            case ConflictException _ -> HttpStatus.CONFLICT;
            case ValidationException _ -> HttpStatus.BAD_REQUEST;
        };
        log.debug("Business exception mapped to {}: {}", status.value(), ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        if (ex instanceof ValidationException validation) {
            problem.setProperty("errors", validation.errors());
        }
        return respond(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        var sqlState = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
        // 23505 unique violation, 23503 foreign key violation; check/not-null/domain violations are bad input
        var problem = switch (sqlState) {
            case "23505", "23503" -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "The request conflicts with existing data");
            case null, default -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "The request contains a value that violates a data constraint");
        };
        log.debug("Data integrity violation sqlState={} mapped to {}: {}",
                sqlState, problem.getStatus(), ex.getMostSpecificCause().getMessage());
        return respond(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new Violation(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        log.debug("Request body validation failed: {}", errors);
        var problem = ex.getBody();
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Violation(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        log.debug("Request parameter validation failed: {}", errors);
        var problem = ex.getBody();
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /// Final hook for all responses built by [ResponseEntityExceptionHandler].
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty("correlationId", RequestContext.currentCorrelationId());
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private static ResponseEntity<Object> respond(ProblemDetail problem) {
        problem.setProperty("correlationId", RequestContext.currentCorrelationId());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
