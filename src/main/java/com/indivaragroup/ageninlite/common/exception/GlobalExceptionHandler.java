package com.indivaragroup.ageninlite.common.exception;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    // handle error validasi DTO
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ApiResponse<Map<String, String>> response = new ApiResponse<>(false, "Validation Error", errors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // handle custom exception
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        ApiResponse<Void> response = new ApiResponse<>(false, ex.getMessage(), null);

        return new ResponseEntity<>(response,HttpStatus.BAD_REQUEST);
    }

    // handle authorization error (403 Forbidden)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ApiResponse<Void> response = new ApiResponse<>(false, "AUTH_0020: Unauthorized — user is not an Admin", null);
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // handle app exception
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        String formattedMessage = ex.getErrorCode().name() + ": " + ex.getMessage();
        ApiResponse<Void> response = new ApiResponse<>(false, formattedMessage, null);
        return new ResponseEntity<>(response, ex.getErrorCode().getHttpStatus());
    }
}
