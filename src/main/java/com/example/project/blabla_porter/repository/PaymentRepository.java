package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByParcelRequestId(Long parcelRequestId);
    Optional<Payment> findByRideRequestId(Long rideRequestId);
}
