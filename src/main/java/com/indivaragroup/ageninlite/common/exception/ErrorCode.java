package com.indivaragroup.ageninlite.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String name();
    String getMessage();
    HttpStatus getHttpStatus();
}
