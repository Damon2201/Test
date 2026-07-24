package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {
    List<RideRequest> findByRiderId(Long riderId);
    List<RideRequest> findByTripId(Long tripId);
    List<RideRequest> findByStatus(RideRequest.RideStatus status);
}
