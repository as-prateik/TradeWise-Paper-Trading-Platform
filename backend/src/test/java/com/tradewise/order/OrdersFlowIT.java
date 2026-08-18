package com.tradewise.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradewise.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The core buy/sell loop over HTTP against real PostgreSQL: wallet debits/credits,
 * rejections as first-class outcomes, idempotent replays, and history records.
 */
class OrdersFlowIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders authenticate() {
        Map<String, String> register = Map.of(
                "email", "trader-" + System.nanoTime() + "@example.com",
                "password", "passw0rd123",
                "fullName", "Trader");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/register", register, Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) response.getBody().get("accessToken"));
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private ResponseEntity<Map> placeOrder(HttpHeaders headers, String key, String symbol,
                                           String side, long quantity) {
        HttpHeaders orderHeaders = new HttpHeaders();
        orderHeaders.putAll(headers);
        orderHeaders.set("Idempotency-Key", key);
        Map<String, Object> body = Map.of("symbol", symbol, "side", side, "type", "MARKET",
                "quantity", quantity);
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(body, orderHeaders), Map.class);
    }

    @Test
    @DisplayName("buy debits the wallet, sell credits it, and every event is recorded")
    void buySellLoop() {
        HttpHeaders headers = authenticate();

        ResponseEntity<Map> buy = placeOrder(headers, "it-buy-1", "TCS", "BUY", 10);
        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(buy.getBody()).containsEntry("status", "EXECUTED");
        BigDecimal executedPrice = new BigDecimal(buy.getBody().get("executedPrice").toString());
        BigDecimal cost = executedPrice.multiply(BigDecimal.TEN);

        // wallet debited by exactly quantity * executed price
        ResponseEntity<Map> wallet = restTemplate.exchange("/api/v1/wallets/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        BigDecimal balance = new BigDecimal(wallet.getBody().get("balance").toString());
        assertThat(balance).isEqualByComparingTo(new BigDecimal("1000000").subtract(cost));

        // idempotent replay: same key -> 200 and the same order id, no double execution
        ResponseEntity<Map> replay = placeOrder(headers, "it-buy-1", "TCS", "BUY", 10);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().get("id")).isEqualTo(buy.getBody().get("id"));

        ResponseEntity<Map> walletAfterReplay = restTemplate.exchange("/api/v1/wallets/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(new BigDecimal(walletAfterReplay.getBody().get("balance").toString()))
                .isEqualByComparingTo(balance);

        // overselling is a recorded rejection, not an error
        ResponseEntity<Map> oversell = placeOrder(headers, "it-sell-1", "TCS", "SELL", 100);
        assertThat(oversell.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(oversell.getBody()).containsEntry("status", "REJECTED");
        assertThat(oversell.getBody()).containsEntry("rejectionReason", "INSUFFICIENT_SHARES");

        // a real sell credits proceeds
        ResponseEntity<Map> sell = placeOrder(headers, "it-sell-2", "TCS", "SELL", 4);
        assertThat(sell.getBody()).containsEntry("status", "EXECUTED");

        // history: 3 orders (2 executed, 1 rejected), 2 trades, 3 transactions (seed+debit+credit)
        ResponseEntity<Map> orders = restTemplate.exchange("/api/v1/orders?size=10", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat((List<?>) orders.getBody().get("content")).hasSize(3);

        ResponseEntity<Map> trades = restTemplate.exchange("/api/v1/trades?size=10", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat((List<?>) trades.getBody().get("content")).hasSize(2);

        ResponseEntity<Map> transactions = restTemplate.exchange("/api/v1/transactions?size=10",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> transactionRows = (List<Map<String, Object>>) transactions.getBody().get("content");
        assertThat(transactionRows).hasSize(3);
        assertThat(transactionRows).extracting(row -> row.get("type"))
                .containsExactlyInAnyOrder("SEED", "TRADE_DEBIT", "TRADE_CREDIT");
    }

    @Test
    @DisplayName("a buy the wallet cannot cover is recorded as REJECTED INSUFFICIENT_FUNDS")
    void insufficientFundsIsRecorded() {
        HttpHeaders headers = authenticate();

        ResponseEntity<Map> tooBig = placeOrder(headers, "it-big-1", "MARUTI", "BUY", 1000);
        assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tooBig.getBody()).containsEntry("status", "REJECTED");
        assertThat(tooBig.getBody()).containsEntry("rejectionReason", "INSUFFICIENT_FUNDS");

        // and the rejected order shows up in history
        ResponseEntity<Map> orders = restTemplate.exchange("/api/v1/orders?size=10", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) orders.getBody().get("content");
        assertThat(content).anySatisfy(row -> assertThat(row).containsEntry("rejectionReason", "INSUFFICIENT_FUNDS"));
    }
}
