package com.tradewise.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The wallet module's contract. Other modules call this interface, never {@link WalletRepository}.
 *
 * <p>Debits deliberately return a result instead of throwing: an insufficient balance is a
 * business outcome the order engine records as a REJECTED order, not an exception that
 * would mark the surrounding transaction rollback-only.
 */
public interface WalletService {

    Wallet openWallet(UUID userId);

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Atomically debits the wallet if the balance allows it.
     *
     * @return the balance after the debit, or empty when funds are insufficient
     */
    Optional<BigDecimal> attemptDebitForTrade(UUID userId, BigDecimal amount, UUID orderId, String description);

    /** Credits sale proceeds. @return the balance after the credit */
    BigDecimal creditForTrade(UUID userId, BigDecimal amount, UUID orderId, String description);

    Page<Transaction> getTransactions(UUID userId, Pageable pageable);
}
