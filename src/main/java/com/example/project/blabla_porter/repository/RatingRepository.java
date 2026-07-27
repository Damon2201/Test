package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByRateeUserId(Long rateeUserId);
    List<Rating> findByRaterUserId(Long raterUserId);
    java.util.Optional<Rating> findByRaterUserIdAndParcelRequestId(Long raterUserId, Long parcelRequestId);
    java.util.Optional<Rating> findByRaterUserIdAndRideRequestId(Long raterUserId, Long rideRequestId);
}
