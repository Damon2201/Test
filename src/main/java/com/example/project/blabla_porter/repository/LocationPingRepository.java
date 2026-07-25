package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {
    List<LocationPing> findByTripIdOrderByTimestampAsc(Long tripId);
    Optional<LocationPing> findTopByTripIdOrderByTimestampDesc(Long tripId);
    List<LocationPing> findByTravelerIdOrderByTimestampDesc(Long travelerId);

    // Data lifecycle: purge old location data
    void deleteByTimestampBefore(LocalDateTime cutoff);
    void deleteByTripId(Long tripId);
    long countByTimestampBefore(LocalDateTime cutoff);
}
