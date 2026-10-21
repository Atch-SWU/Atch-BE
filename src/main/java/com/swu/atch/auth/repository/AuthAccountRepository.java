
package com.swu.atch.auth.repository;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    // LOCAL 로그인용 이메일 조회
    Optional<AuthAccount> findByProviderAndEmailIgnoreCase(
            AuthProvider provider,
            String email
    );

    // 중복 이메일 가입 여부 확인
    boolean existsByProviderAndEmailIgnoreCase(
            AuthProvider provider,
            String email
    );
}
