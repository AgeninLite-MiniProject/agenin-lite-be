package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DownlinerErrorCode implements ErrorCode {

    DWN_0001("Downliner not found", HttpStatus.NOT_FOUND),
    DWN_0002("This user is not your direct downliner", HttpStatus.FORBIDDEN),
    DWN_9999("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;

}
