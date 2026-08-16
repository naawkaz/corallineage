package com.corallineage.backend.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("unit-test-secret-key-at-least-32-characters-long", 86400000L);

    @Test
    void generateToken_thenExtractUsername_roundTrips() {
        String token = jwtService.generateToken("researcher_01");

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("researcher_01");
    }

    @Test
    void extractUsername_garbageToken_returnsNull() {
        assertThat(jwtService.extractUsername("not-a-real-token")).isNull();
    }

    @Test
    void extractUsername_tokenSignedWithDifferentSecret_returnsNull() {
        JwtService other = new JwtService("a-completely-different-secret-key-of-length-32", 86400000L);
        String token = other.generateToken("researcher_01");

        assertThat(jwtService.extractUsername(token)).isNull();
    }

    @Test
    void extractUsername_expiredToken_returnsNull() throws InterruptedException {
        JwtService shortLived = new JwtService("unit-test-secret-key-at-least-32-characters-long", 1L);
        String token = shortLived.generateToken("researcher_01");
        Thread.sleep(20);

        assertThat(shortLived.extractUsername(token)).isNull();
    }
}
