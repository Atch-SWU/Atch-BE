
package com.swu.atch.user.repository;

import com.swu.atch.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 한 번에 최대 100명씩 순회하며 실패한 사용자 때문에 뒤 사용자가 굶지 않게 한다.
    @Query(value = """
            select u.id from users u left join user_daily_settlement_states d on d.user_id=u.id
            where u.id > :afterId and (
              d.next_settlement_date < :today or (d.user_id is null and exists(select 1 from todos t where t.user_id=u.id)))
            order by u.id limit 100
            """, nativeQuery = true)
    java.util.List<Long> findDailyMaintenanceCandidates(@Param("afterId") Long afterId, @Param("today") java.time.LocalDate today);

    // 계정 변경·탈퇴·로그인 발급을 같은 사용자 행에서 직렬화
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
