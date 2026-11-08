package com.chatatda.api.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailCodeRepository extends JpaRepository<EmailCode, String> {

    /** 같은 메일로 동시에 오는 보내기·확인을 한 줄씩 처리한다 (틀린 횟수·재전송 간격이 동시 요청으로 뚫리지 않게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailCode c where c.email = :email")
    Optional<EmailCode> findForUpdate(@Param("email") String email);
}
