package com.tpi.toxicity.api;

import com.tpi.toxicity.dto.ErrorResponse;
import com.tpi.toxicity.model.InferenceException;
import com.tpi.toxicity.model.InvalidTextException;
import com.tpi.toxicity.model.ServiceSaturatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Respuestas de error controladas: sin stack traces, rutas locales ni texto del usuario. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        return build(HttpStatus.BAD_REQUEST, "invalid_request", "text is required and must not be blank");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, InvalidTextException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RequestBodyTooLargeException) {
                return build(HttpStatus.PAYLOAD_TOO_LARGE, "text_too_long", "request body too large");
            }
        }
        return build(HttpStatus.BAD_REQUEST, "invalid_request", "request body is missing or malformed");
    }

    @ExceptionHandler(TextTooLongException.class)
    ResponseEntity<ErrorResponse> tooLong(TextTooLongException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "text_too_long", e.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType(Exception e) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Content-Type must be application/json");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> method(Exception e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "method not allowed");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> notFound(Exception e) {
        return build(HttpStatus.NOT_FOUND, "not_found", "resource not found");
    }

    @ExceptionHandler(ServiceSaturatedException.class)
    ResponseEntity<ErrorResponse> saturated(ServiceSaturatedException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "service_busy", "service is busy, retry later");
    }

    @ExceptionHandler(InferenceException.class)
    ResponseEntity<ErrorResponse> inference(InferenceException e) {
        log.error("Inference failed: {} (cause: {})", e.getMessage(), e.getCause());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "inference_failed", "inference failed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error: {}", e.getClass().getSimpleName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "internal error");
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(error, message));
    }
}
