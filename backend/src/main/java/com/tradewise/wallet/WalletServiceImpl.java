package com.tradewise.wallet;

import static com.tradewise.common.MoneyConstants.MONEY_SCALE;
import static com.tradewise.common.MoneyConstants.ROUNDING;

import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.wallet.Transaction.TransactionType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletProperties walletProperties;

    @Override
    public Wallet openWallet(UUID userId) {
        BigDecimal seed = walletProperties.seedBalance().setScale(MONEY_SCALE, ROUNDING);
        Wallet wallet = walletRepository.save(Wallet.builder()
                .userId(userId)
                .balance(seed)
                .build());
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .walletId(wallet.getId())
                .transactionType(TransactionType.SEED)
                .amount(seed)
                .balanceAfter(seed)
                .description("Initial virtual balance")
                .build());
        return wallet;
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return walletRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public Optional<BigDecimal> attemptDebitForTrade(UUID userId, BigDecimal amount, UUID orderId,
                                                     String description) {
        Wallet wallet = requiredWallet(userId);
        BigDecimal debit = amount.setScale(MONEY_SCALE, ROUNDING);
        if (wallet.getBalance().compareTo(debit) < 0) {
            return Optional.empty();
        }
        wallet.debit(debit);
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .walletId(wallet.getId())
                .transactionType(TransactionType.TRADE_DEBIT)
                .amount(debit)
                .balanceAfter(wallet.getBalance())
                .referenceOrderId(orderId)
                .description(description)
                .build());
        return Optional.of(wallet.getBalance());
    }

    @Override
    @Transactional
    public BigDecimal creditForTrade(UUID userId, BigDecimal amount, UUID orderId, String description) {
        Wallet wallet = requiredWallet(userId);
        BigDecimal credit = amount.setScale(MONEY_SCALE, ROUNDING);
        wallet.credit(credit);
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .walletId(wallet.getId())
                .transactionType(TransactionType.TRADE_CREDIT)
                .amount(credit)
                .balanceAfter(wallet.getBalance())
                .referenceOrderId(orderId)
                .description(description)
                .build());
        return wallet.getBalance();
    }

    @Override
    public Page<Transaction> getTransactions(UUID userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    private Wallet requiredWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
    }
}
