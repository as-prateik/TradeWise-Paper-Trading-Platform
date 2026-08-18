package com.tradewise.portfolio;

import static com.tradewise.common.MoneyConstants.MONEY_SCALE;
import static com.tradewise.common.MoneyConstants.ROUNDING;

import com.tradewise.marketdata.MarketDataService;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.portfolio.dto.PortfolioResponse;
import com.tradewise.portfolio.dto.PortfolioResponse.HoldingView;
import com.tradewise.portfolio.dto.PortfolioResponse.PortfolioTotals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HoldingRepository holdingRepository;
    private final MarketDataService marketDataService;

    @Override
    @Transactional
    public void applyBuy(UUID userId, String symbol, long quantity, BigDecimal price) {
        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseGet(() -> Holding.empty(userId, symbol));
        holding.applyBuy(quantity, price);
        holdingRepository.save(holding);
    }

    @Override
    @Transactional
    public Optional<BigDecimal> attemptSell(UUID userId, String symbol, long quantity, BigDecimal price) {
        Optional<Holding> lookup = holdingRepository.findByUserIdAndSymbol(userId, symbol);
        if (lookup.isEmpty() || lookup.get().getQuantity() < quantity) {
            return Optional.empty();
        }
        Holding holding = lookup.get();
        BigDecimal realizedDelta = holding.applySell(quantity, price);
        holdingRepository.save(holding);
        return Optional.of(realizedDelta);
    }

    @Override
    public List<Holding> getHoldings(UUID userId) {
        return holdingRepository.findByUserIdOrderBySymbolAsc(userId);
    }

    @Override
    public PortfolioResponse getPortfolio(UUID userId) {
        List<Holding> allHoldings = holdingRepository.findByUserIdOrderBySymbolAsc(userId);

        BigDecimal totalInvested = zero();
        BigDecimal totalMarket = zero();
        BigDecimal totalUnrealized = zero();
        BigDecimal totalRealized = zero();

        record ValuedHolding(Holding holding, Quote quote, BigDecimal invested, BigDecimal market,
                             BigDecimal unrealized) {
        }
        List<ValuedHolding> openPositions = new ArrayList<>();

        for (Holding holding : allHoldings) {
            totalRealized = totalRealized.add(holding.getRealizedPnl());
            if (holding.getQuantity() == 0) {
                continue;
            }
            Quote quote = marketDataService.getQuote(holding.getSymbol());
            BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
            BigDecimal invested = holding.getAveragePrice().multiply(quantity).setScale(MONEY_SCALE, ROUNDING);
            BigDecimal market = quote.price().multiply(quantity).setScale(MONEY_SCALE, ROUNDING);
            BigDecimal unrealized = market.subtract(invested);

            totalInvested = totalInvested.add(invested);
            totalMarket = totalMarket.add(market);
            totalUnrealized = totalUnrealized.add(unrealized);
            openPositions.add(new ValuedHolding(holding, quote, invested, market, unrealized));
        }

        BigDecimal finalTotalMarket = totalMarket;
        List<HoldingView> views = openPositions.stream()
                .map(position -> new HoldingView(
                        position.holding().getSymbol(),
                        position.quote().companyName(),
                        position.holding().getQuantity(),
                        position.holding().getAveragePrice(),
                        position.invested(),
                        position.quote().price(),
                        position.market(),
                        position.unrealized(),
                        percentOf(position.unrealized(), position.invested()),
                        position.holding().getRealizedPnl(),
                        percentOf(position.market(), finalTotalMarket)))
                .toList();

        return new PortfolioResponse(views,
                new PortfolioTotals(totalInvested, totalMarket, totalUnrealized, totalRealized));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
    }

    /** part / whole * 100 at scale 2; zero when the denominator is zero. */
    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, ROUNDING);
        }
        return part.multiply(HUNDRED).divide(whole, 2, ROUNDING);
    }
}
