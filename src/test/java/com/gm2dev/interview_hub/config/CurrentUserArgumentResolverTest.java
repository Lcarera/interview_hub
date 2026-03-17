package com.gm2dev.interview_hub.config;

import com.gm2dev.interview_hub.dto.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameter_returnsTrueForCurrentUser() throws NoSuchMethodException {
        MethodParameter param = new MethodParameter(
                TestController.class.getMethod("testMethod", CurrentUser.class), 0);
        assertTrue(resolver.supportsParameter(param));
    }

    @Test
    void supportsParameter_returnsFalseForOtherTypes() throws NoSuchMethodException {
        MethodParameter param = new MethodParameter(
                TestController.class.getMethod("otherMethod", String.class), 0);
        assertFalse(resolver.supportsParameter(param));
    }

    @Test
    void resolveArgument_withValidJwt_returnsCurrentUser() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        CurrentUser result = (CurrentUser) resolver.resolveArgument(
                mock(MethodParameter.class), null, mock(NativeWebRequest.class), null);

        assertNotNull(result);
        assertEquals(userId, result.id());
    }

    @Test
    void resolveArgument_withMalformedSubject_throwsIllegalArgumentException() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThrows(IllegalArgumentException.class, () ->
                resolver.resolveArgument(mock(MethodParameter.class), null, mock(NativeWebRequest.class), null));
    }

    @Test
    void resolveArgument_withNoAuthentication_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                resolver.resolveArgument(mock(MethodParameter.class), null, mock(NativeWebRequest.class), null));
    }

    static class TestController {
        public void testMethod(CurrentUser user) {}
        public void otherMethod(String s) {}
    }
}
