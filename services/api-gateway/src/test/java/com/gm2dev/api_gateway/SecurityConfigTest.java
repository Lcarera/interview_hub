package com.gm2dev.api_gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
    }

    @Test
    void authEndpointShouldBePublic() {
        webTestClient.get()
            .uri("/auth/google")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void actuatorHealthShouldBePublic() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void actuatorInfoShouldRequireAuth() {
        webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() {
        webTestClient.get()
            .uri("/interviews")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpointWithValidTokenShouldNotReturn401() {
        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt())
            .get()
            .uri("/interviews")
            .exchange()
            .expectStatus().isNotFound(); // 404 = passed security, no route in test context
    }
}
