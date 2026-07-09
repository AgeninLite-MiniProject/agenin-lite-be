package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRD_0001("Missing or invalid fields", HttpStatus.BAD_REQUEST),
    PRD_0002("Product not found", HttpStatus.NOT_FOUND),
    PRD_9999("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;
}
