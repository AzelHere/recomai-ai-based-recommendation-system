package com.guvi.recommendationsystem.web;

import com.guvi.recommendationsystem.exception.CategoryInUseException;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.FeedbackNotFoundException;
import com.guvi.recommendationsystem.exception.InterestNotFoundException;
import com.guvi.recommendationsystem.exception.DuplicateUserException;
import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.exception.RecommendationNotFoundException;
import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler({
            InvalidUserInputException.class,
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", messageForBadRequest(exception), request);
    }

    @ExceptionHandler(UnauthorizedRequestException.class)
    public ResponseEntity<ApiError> handleUnauthorized(
            UnauthorizedRequestException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenRequestException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenRequestException exception,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage(), request);
    }

    @ExceptionHandler({
            UserNotFoundException.class,
            CategoryNotFoundException.class,
            InterestNotFoundException.class,
            FeedbackNotFoundException.class,
            ItemNotFoundException.class,
            RecommendationNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Not found", exception.getMessage(), request);
    }

    @ExceptionHandler({
            DuplicateUserException.class,
            DuplicateCatalogEntryException.class,
            CategoryInUseException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException exception, HttpServletRequest request) {
        String message = exception instanceof DataIntegrityViolationException
                ? "The request conflicts with existing data."
                : exception.getMessage();
        return error(HttpStatus.CONFLICT, "Conflict", message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled request failure requestId={} method={} path={}",
                request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE),
                request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed.", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request) {
        ApiError body = new ApiError(
                status.value(),
                error,
                message,
                request.getRequestURI(),
                Instant.now());
        return ResponseEntity.status(status).body(body);
    }

    private String messageForBadRequest(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException) {
            return "Request body or parameter is missing, malformed, or has an invalid value.";
        }
        return exception.getMessage();
    }
}
