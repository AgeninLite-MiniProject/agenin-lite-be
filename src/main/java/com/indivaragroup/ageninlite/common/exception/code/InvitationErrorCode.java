package com.indivaragroup.ageninlite.common.exception.code;

import com.indivaragroup.ageninlite.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InvitationErrorCode implements ErrorCode {

    // --- Send Invitation Errors ---
    INV_0001("Cannot invite yourself", HttpStatus.BAD_REQUEST),                      // 400
    INV_0002("Would create a cycle in the hierarchy", HttpStatus.FORBIDDEN),         // 403
    INV_0003("Duplicate pending invitation already exists", HttpStatus.CONFLICT),    // 409
    INV_0004("Invitee already has an upline", HttpStatus.BAD_REQUEST),               // 400
    INV_0005("Maximum 3 pending invitations reached", HttpStatus.BAD_REQUEST),       // 400
    INV_0006("Invitee not found", HttpStatus.NOT_FOUND),                             // 404
    INV_0007("Invitee account is deleted", HttpStatus.BAD_REQUEST),                 // 400

    // --- Accept Invitation Errors ---
    INV_0010("Inviter has reached maximum 10 downliners", HttpStatus.FORBIDDEN),     // 403
    INV_0011("Invitation is no longer PENDING", HttpStatus.BAD_REQUEST),             // 400
    INV_0012("You already have an upline", HttpStatus.BAD_REQUEST),                  // 400
    INV_0013("Invitation not found", HttpStatus.NOT_FOUND),                          // 404

    // --- Decline Invitation Errors ---
    INV_0014("Invitation not found", HttpStatus.NOT_FOUND),                         // 404

    // --- General Invitation Error ---
    INV_9999("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);            // 500

    private final String message;
    private final HttpStatus httpStatus;
}
