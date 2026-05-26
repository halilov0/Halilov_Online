package com.halilov.online.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlertState, String> {
}
