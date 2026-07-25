package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.repository.LocationPingRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service that purges stale GPS location pings to ensure
 * we don't indefinitely store people's movement history.
 *
 * Rules:
 * 1. All pings older than 48 hours are deleted regardless of trip status.
 * 2. Pings for trips that are COMPLETED or CANCELLED (and ended 2+ hours ago)
 *    are also deleted immediately.
 */
@Service
@EnableScheduling
public class LocationDataCleanupService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocationDataCleanupService.class);

    @Autowired
    private LocationPingRepository locationPingRepository;

    @Autowired
    private TripRepository tripRepository;

    /**
     * Runs every hour (3,600,000 ms). Purges old location pings.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void purgeStaleLocationData() {
        LocalDateTime now = LocalDateTime.now();

        // Rule 1: Delete all pings older than 48 hours
        LocalDateTime ageCutoff = now.minusHours(48);
        long staleCount = locationPingRepository.countByTimestampBefore(ageCutoff);
        if (staleCount > 0) {
            locationPingRepository.deleteByTimestampBefore(ageCutoff);
            log.info("Location cleanup: purged {} location pings older than 48 hours", staleCount);
        }

        // Rule 2: Delete pings for completed/cancelled trips that ended 2+ hours ago
        List<Trip> finishedTrips = tripRepository.findAll().stream()
                .filter(t -> t.getStatus() == Trip.TripStatus.COMPLETED || t.getStatus() == Trip.TripStatus.CANCELLED)
                .filter(t -> {
                    // Use createdAt as a proxy for trip end time if no explicit field exists
                    LocalDateTime tripTime = t.getCreatedAt() != null ? t.getCreatedAt() : now.minusDays(1);
                    return tripTime.isBefore(now.minusHours(2));
                })
                .toList();

        int tripPurgeCount = 0;
        for (Trip trip : finishedTrips) {
            try {
                locationPingRepository.deleteByTripId(trip.getId());
                tripPurgeCount++;
            } catch (Exception e) {
                log.warn("Failed to purge pings for trip {}: {}", trip.getId(), e.getMessage());
            }
        }

        if (tripPurgeCount > 0) {
            log.info("Location cleanup: purged pings for {} completed/cancelled trips (ended 2+ hours ago)", tripPurgeCount);
        }
    }
}
