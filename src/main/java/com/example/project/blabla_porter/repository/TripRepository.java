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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Trip t where t.id = :id")
    java.util.Optional<Trip> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @jakarta.transaction.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Trip t SET t.travelMode = 'DRIVING' WHERE t.travelMode IS NULL")
    void updateNullTravelModesToDriving();
}
