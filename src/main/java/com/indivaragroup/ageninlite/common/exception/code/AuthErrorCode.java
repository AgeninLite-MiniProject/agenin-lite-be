package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // --- Register Errors ---
    AUTH_0001("Phone number is already registered", HttpStatus.CONFLICT), // 409
    AUTH_0002("Password must be at least 8-15 characters", HttpStatus.BAD_REQUEST), // 400
    AUTH_0003("Missing mandatory property", HttpStatus.BAD_REQUEST), // 400
    AUTH_0004("Email is already registered", HttpStatus.CONFLICT), // 409
    AUTH_0005("Invalid phone number format", HttpStatus.BAD_REQUEST), // 400
    AUTH_0006("Referral code not found", HttpStatus.NOT_FOUND), // 404
    AUTH_0007("Referral owner has reached max 10 downliners", HttpStatus.BAD_REQUEST), // 400
    AUTH_0008("Referral code belongs to an account that no longer exists", HttpStatus.BAD_REQUEST), // 400

    // --- Login Errors ---
    AUTH_0010("Invalid phone number and/or password", HttpStatus.UNAUTHORIZED), // 401
    AUTH_0011("Account has been deleted", HttpStatus.FORBIDDEN), // 403

    // --- Authorization & Token Errors ---
    AUTH_0020("Unauthorized - invalid or expired token", HttpStatus.UNAUTHORIZED), // 401
    AUTH_0030("Refresh token is invalid, expired, or revoked", HttpStatus.UNAUTHORIZED), // 401
    AUTH_0040("Unauthorized - Token has been blacklisted", HttpStatus.UNAUTHORIZED), // 401

    // --- General Auth Error ---
    AUTH_9999("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR); // 500

    private final String message;
    private final HttpStatus httpStatus;
}
