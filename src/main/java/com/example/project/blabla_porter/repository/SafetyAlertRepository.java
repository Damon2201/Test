package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.SafetyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SafetyAlertRepository extends JpaRepository<SafetyAlert, Long> {
    List<SafetyAlert> findByRideRequestId(Long rideRequestId);
    List<SafetyAlert> findByRiderId(Long riderId);
    List<SafetyAlert> findByStatus(SafetyAlert.AlertStatus status);
}
