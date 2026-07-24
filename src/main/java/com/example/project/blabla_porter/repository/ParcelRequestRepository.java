package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.ParcelRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelRequestRepository extends JpaRepository<ParcelRequest, Long> {
    List<ParcelRequest> findBySenderId(Long senderId);
    List<ParcelRequest> findByTripId(Long tripId);
    List<ParcelRequest> findByStatus(ParcelRequest.ParcelStatus status);
}
