package com.chatatda.api.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    /** 내 정보를 두 창에서 동시에 저장하면 자주 가는 건물 표가 겹쳐 500 이 났다 → 한 번에 하나씩 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);
}
