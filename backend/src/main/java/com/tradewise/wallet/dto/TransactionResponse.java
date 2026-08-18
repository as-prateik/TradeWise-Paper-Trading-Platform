package com.tradewise.wallet.dto;

import com.tradewise.wallet.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID referenceOrderId,
        String description,
        Instant createdAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getTransactionType().name(),
                transaction.getAmount(), transaction.getBalanceAfter(),
                transaction.getReferenceOrderId(), transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
