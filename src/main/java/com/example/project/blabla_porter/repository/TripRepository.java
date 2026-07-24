package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByTravelerId(Long travelerId);
    List<Trip> findBySourceContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
            String source, String destination, Trip.TripStatus status);
    List<Trip> findByStatus(Trip.TripStatus status);
}
