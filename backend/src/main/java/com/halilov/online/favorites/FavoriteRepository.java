package com.halilov.online.favorites;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
