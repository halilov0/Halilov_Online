package com.halilov.online.auth.totp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {

    List<TotpRecoveryCode> findByUserIdAndUsedAtIsNullOrderById(Long userId);

    @Modifying
    @Query("DELETE FROM TotpRecoveryCode r WHERE r.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
