package com.tradewise.portfolio;

import com.tradewise.portfolio.dto.PortfolioResponse;
import com.tradewise.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioResponse portfolio(@AuthenticationPrincipal AuthenticatedUser principal) {
        return portfolioService.getPortfolio(principal.userId());
    }
}
