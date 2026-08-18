package com.tradewise.wallet;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradewise.wallet")
public record WalletProperties(BigDecimal seedBalance) {
}
