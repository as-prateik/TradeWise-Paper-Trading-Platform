package com.tradewise.wallet;

import com.tradewise.common.PageResponse;
import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.security.AuthenticatedUser;
import com.tradewise.wallet.dto.TransactionResponse;
import com.tradewise.wallet.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletService walletService;

    @GetMapping("/wallets/me")
    public WalletResponse currentWallet(@AuthenticationPrincipal AuthenticatedUser principal) {
        return walletService.findByUserId(principal.userId())
                .map(WalletResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageResponse.from(walletService.getTransactions(principal.userId(),
                PageRequest.of(Math.max(0, page), clampedSize)), TransactionResponse::from);
    }
}
