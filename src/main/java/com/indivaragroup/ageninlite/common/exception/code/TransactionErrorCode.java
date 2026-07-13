package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionErrorCode implements ErrorCode {
    TRX_0001("Product not found", HttpStatus.NOT_FOUND),  // 404
    TRX_0002("Product is no longer active", HttpStatus.BAD_REQUEST), // 400
    TRX_0003("Quantity must be greater than 0", HttpStatus.BAD_REQUEST), // 400
    TRX_0004("Missing or empty required field", HttpStatus.BAD_REQUEST), // 400
    TRX_0005("Duplicate product in request", HttpStatus.BAD_REQUEST), // 400
    TRX_0006("Quantity exceeds maximum allowed", HttpStatus.BAD_REQUEST), // 400

    // === Complete Transaction (TRX_0010 - TRX_0013) ===
    TRX_0010("Transaction not found",                       HttpStatus.NOT_FOUND),  // 404
    TRX_0011("Transaction is not in PENDING status",        HttpStatus.BAD_REQUEST), // 400
    TRX_0012("You can only complete your own transactions", HttpStatus.FORBIDDEN),   // 403
    TRX_0013("Product is no longer active",                 HttpStatus.BAD_REQUEST), // 400

    // === View Transaction (TRX_0014) ===
    TRX_0014("You can only view your own transactions",         HttpStatus.FORBIDDEN),    // 403

    // === List Transaction validation (TRX_0015) ===
    TRX_0015("Invalid view mode, must be SELLER or BENEFICIARY", HttpStatus.BAD_REQUEST), // 400

    // === Catch-all ===
    TRX_9999("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR); // 500

    private final String message;
    private final HttpStatus httpStatus;
}
