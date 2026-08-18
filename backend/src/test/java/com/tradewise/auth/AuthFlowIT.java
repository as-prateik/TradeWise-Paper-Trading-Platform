package com.tradewise.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradewise.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * Full register → login → protected-read flow against real PostgreSQL.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("register seeds a wallet, login issues a token, protected endpoints honor it")
    void fullAuthFlow() {
        Map<String, String> registerBody = Map.of(
                "email", "Flow@Example.com",
                "password", "passw0rd123",
                "fullName", "Flow Tester");

        ResponseEntity<Map> registered =
                restTemplate.postForEntity("/api/v1/auth/register", registerBody, Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).containsKeys("accessToken", "user");

        // duplicate registration → 409 with the stable error code
        ResponseEntity<Map> duplicate =
                restTemplate.postForEntity("/api/v1/auth/register", registerBody, Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("errorCode", "EMAIL_ALREADY_REGISTERED");

        // login with normalized-case email
        ResponseEntity<Map> loggedIn = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "flow@example.com", "password", "passw0rd123"), Map.class);
        assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) loggedIn.getBody().get("accessToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> authorized = new HttpEntity<>(headers);

        ResponseEntity<Map> me = restTemplate.exchange(
                "/api/v1/users/me", HttpMethod.GET, authorized, Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("email", "flow@example.com");

        ResponseEntity<Map> wallet = restTemplate.exchange(
                "/api/v1/wallets/me", HttpMethod.GET, authorized, Map.class);
        assertThat(wallet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(wallet.getBody().get("balance").toString()))
                .isEqualByComparingTo(new BigDecimal("1000000"));

        // without a token, protected endpoints return the standard 401 envelope
        ResponseEntity<Map> anonymous = restTemplate.getForEntity("/api/v1/users/me", Map.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).containsEntry("errorCode", "UNAUTHORIZED");
    }
}
