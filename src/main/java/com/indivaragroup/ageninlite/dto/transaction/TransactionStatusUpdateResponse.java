package com.indivaragroup.ageninlite.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionStatusUpdateResponse {
    private UUID trxId;
    private String trxStatus;
    private String message;
}
