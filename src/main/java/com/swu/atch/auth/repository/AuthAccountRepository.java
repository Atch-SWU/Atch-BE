
package com.swu.atch.auth.repository;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    // 본인 LOCAL 계정 조회. 변경 시에는 먼저 User 행 잠금 획득
    Optional<AuthAccount> findByUser_IdAndProvider(Long userId, AuthProvider provider);

    @Query("select a.user.id from AuthAccount a where a.provider = :provider and lower(a.email) = lower(:email)")
    Optional<Long> findUserIdByProviderAndEmail(@Param("provider") AuthProvider provider,
                                             @Param("email") String email);

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
