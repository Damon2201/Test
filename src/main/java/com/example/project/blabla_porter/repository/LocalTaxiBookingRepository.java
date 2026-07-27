package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.LocalTaxiBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalTaxiBookingRepository extends JpaRepository<LocalTaxiBooking, Long> {
    List<LocalTaxiBooking> findByRiderId(Long riderId);
    List<LocalTaxiBooking> findByCaptainId(Long captainId);
    java.util.Optional<LocalTaxiBooking> findByTripId(Long tripId);
    java.util.Optional<LocalTaxiBooking> findByRazorpayOrderId(String razorpayOrderId);
}
