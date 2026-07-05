package com.aiinvestor.gateway.modules.identity.security;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.service.UserService;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerTokenAuthenticationFilterTest {

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private UserService userService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private BearerTokenAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        UserContext.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBindAuthenticationAndClearContextAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDO user = new UserDO();
        user.setId(1L);
        user.setRole("admin");
        user.setStatus("1");

        when(authTokenService.resolveUserId("token-123")).thenReturn(1L);
        when(userService.getById(1L)).thenReturn(user);
        doAnswer(invocation -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication);
            assertSame(user, authentication.getPrincipal());
            assertEquals("token-123", authentication.getCredentials());
            assertSame(user, UserContext.get());
            assertEquals("token-123", UserContext.getToken());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(UserContext.get());
        assertNull(UserContext.getToken());
        verify(authTokenService, never()).revokeToken(anyString());
    }

    @Test
    void shouldRevokeTokenWhenUserIsInactive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDO user = new UserDO();
        user.setId(2L);
        user.setRole("user");
        user.setStatus("0");

        when(authTokenService.resolveUserId("token-456")).thenReturn(2L);
        when(userService.getById(2L)).thenReturn(user);
        doAnswer(invocation -> {
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(UserContext.get());
            assertNull(UserContext.getToken());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        verify(authTokenService).revokeToken("token-456");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(UserContext.get());
        assertNull(UserContext.getToken());
    }
}
