package com.aiinvestor.gateway.modules.identity.security;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.service.UserService;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final UserService userService;

    public BearerTokenAuthenticationFilter(AuthTokenService authTokenService, UserService userService) {
        this.authTokenService = authTokenService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        boolean boundUser = false;
        try {
            if (StringUtils.hasText(token)) {
                Long userId = authTokenService.resolveUserId(token);
                if (userId != null) {
                    UserDO user = userService.getById(userId);
                    if (user != null && isUserActive(user.getStatus())) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, token, buildAuthorities(user));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        UserContext.set(user);
                        UserContext.setToken(token);
                        boundUser = true;
                    } else {
                        authTokenService.revokeToken(token);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (boundUser) {
                UserContext.remove();
                SecurityContextHolder.clearContext();
            }
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String legacySatoken = request.getHeader("satoken");
        if (StringUtils.hasText(legacySatoken)) {
            return legacySatoken.trim();
        }
        return null;
    }

    private List<SimpleGrantedAuthority> buildAuthorities(UserDO user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (StringUtils.hasText(user.getRole())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().trim().toUpperCase()));
        }
        return authorities;
    }

    private boolean isUserActive(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim();
        return "1".equals(normalized) || "active".equalsIgnoreCase(normalized);
    }
}
