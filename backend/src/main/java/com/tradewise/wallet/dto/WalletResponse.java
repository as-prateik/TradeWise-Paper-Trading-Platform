package com.tradewise.wallet.dto;

import com.tradewise.wallet.Wallet;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(BigDecimal balance, Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getBalance(), wallet.getUpdatedAt());
    }
}
