package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USR_0001("User not found", HttpStatus.NOT_FOUND),
    USR_0002("Cannot delete your own Admin account", HttpStatus.BAD_REQUEST),
    USR_9999("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;
}
