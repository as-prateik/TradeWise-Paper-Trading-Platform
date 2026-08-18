package com.tradewise.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradewise.AbstractIntegrationTest;
import com.tradewise.user.User;
import com.tradewise.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Genuinely triggers an OptimisticLockingFailureException on the wallet — not just the
 * annotation sitting there. Two transactions read the same wallet version, both write;
 * the second commit must fail and roll back.
 */
class WalletOptimisticLockingIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("two overlapping debits on one wallet: exactly one commits, the loser gets an optimistic-lock failure")
    void overlappingDebitsConflict() throws InterruptedException {
        User user = userService.createUser("lock-" + System.nanoTime() + "@example.com",
                "$2a$12$notarealhashnotarealhashnotarealhash", "Lock Tester");
        UUID userId = user.getId();
        walletService.openWallet(userId);

        CountDownLatch bothLoaded = new CountDownLatch(2);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Runnable debitTask = () -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
                    wallet.debit(new BigDecimal("100.0000"));
                    bothLoaded.countDown();
                    try {
                        // hold the transaction open until BOTH have read the same version
                        if (!bothLoaded.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Latch timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    walletRepository.saveAndFlush(wallet);
                });
            } catch (Throwable throwable) {
                failures.add(throwable);
            }
        };

        executor.submit(debitTask);
        executor.submit(debitTask);
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // exactly one loser, and it lost to the optimistic lock
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(OptimisticLockingFailureException.class);

        // the winner's debit is applied exactly once
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("999900.0000"));
    }
}
