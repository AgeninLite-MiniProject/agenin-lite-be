package com.indivaragroup.ageninlite.controller.transaction;

import com.indivaragroup.ageninlite.common.dto.ApiResponse;
import com.indivaragroup.ageninlite.dto.transaction.CompleteTransactionResponse;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionRequest;
import com.indivaragroup.ageninlite.dto.transaction.CreateTransactionResponse;
import com.indivaragroup.ageninlite.service.transaction.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateTransactionResponse>> create(
            @AuthenticationPrincipal String sellerId,
            @Valid @RequestBody CreateTransactionRequest request){
        UUID sellerUuid = UUID.fromString(sellerId);

        CreateTransactionResponse response = transactionService.create(sellerUuid, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Transaction created", response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<CompleteTransactionResponse>> complete(
            @AuthenticationPrincipal String requesterId,
            @PathVariable UUID id) {

        UUID requesterUuid = UUID.fromString(requesterId);
        CompleteTransactionResponse response = transactionService.complete(requesterUuid, id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Transaction completed", response));
    }
}
