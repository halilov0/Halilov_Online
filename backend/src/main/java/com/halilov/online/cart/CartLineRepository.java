package com.halilov.online.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartLineRepository extends JpaRepository<CartLine, Long> {
    List<CartLine> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM CartLine c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
