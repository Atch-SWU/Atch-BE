package com.swu.atch.reward.admin;

import com.swu.atch.auth.service.LocalAuthPersistenceService;
import com.swu.atch.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {
    private final LocalAuthPersistenceService accounts;
    private final AdminProperties properties;

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!properties.enabled() || !(authentication.getCredentials() instanceof String password)) {
            throw invalid();
        }
        try {
            var account = accounts.authenticate(authentication.getName().strip().toLowerCase(Locale.ROOT), password);
            if (!properties.allows(account.userId())) throw invalid();
            var principal = new AdminPrincipal(account.userId(), account.credentialStamp());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_ITEM_ADMIN")));
        } catch (BusinessException ex) {
            throw invalid();
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("운영진 로그인 정보를 확인해주세요.");
    }
}
