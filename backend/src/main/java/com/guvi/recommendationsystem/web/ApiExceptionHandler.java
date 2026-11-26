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
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingRoute(org.springframework.web.servlet.resource.NoResourceFoundException exception,HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND,"Not found","Endpoint or resource was not found.",request);
    }
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler({
            InvalidUserInputException.class,
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
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
            com.guvi.recommendationsystem.exception.UpdateConflictException.class,
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
        logger.error("Unhandled request failure requestId={} method={} type={}",
                request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE),
                request.getMethod(), exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed.", request);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    public ResponseEntity<ApiError> handleDatabaseUnavailable(Exception exception,HttpServletRequest request){
        logger.warn("Database unavailable requestId={} type={}",request.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE),exception.getClass().getName());
        return error(HttpStatus.SERVICE_UNAVAILABLE,"Service unavailable","The service is temporarily unavailable. Please try again shortly.",request);
    }

    @ExceptionHandler({org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleUnsupported(Exception exception,HttpServletRequest request) {
        return exception instanceof org.springframework.web.HttpRequestMethodNotSupportedException
                ? error(HttpStatus.METHOD_NOT_ALLOWED,"Method not allowed","This endpoint does not support this method.",request)
                : error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Unsupported media type","Supply the supported JSON content type.",request);
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
                || exception instanceof org.springframework.web.bind.ServletRequestBindingException
                || exception instanceof MethodArgumentTypeMismatchException) {
            return "Request body or parameter is missing, malformed, or has an invalid value.";
        }
        return exception.getMessage();
    }
}
